package com.leafclient.connect.sdk.paper;

import com.leafclient.connect.sdk.ConnectLog;
import com.leafclient.connect.sdk.LeafConnect;
import com.leafclient.connect.sdk.Limits;
import com.leafclient.connect.sdk.Session;
import com.leafclient.connect.sdk.Transport;
import com.leafclient.connect.sdk.WorldAnchor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Leaf Server Connect for Paper, Folia, Spigot, Bukkit and Purpur. {@link #enable(Plugin)} registers the
 * {@link LeafConnect#CHANNEL} plugin channel and a quit listener, and flushes every tick. Frames are at most
 * {@link Limits#SERVER_MESSAGE_BYTES}, or {@link Limits#LEGACY_MESSAGE_BYTES} on servers whose plugin message limit is
 * 32766 bytes or less. Clicks on block and entity panels are checked for reach (see {@link #reachCheck(boolean)}).
 *
 * <p>On Folia there is no main thread, so each Leaf player gets a task on their own entity scheduler instead of one
 * global timer. The task flushes that player's session every tick and sends the frames from the thread that owns the
 * player, in the order they were queued. Frames queued from any other thread go out on the player's next tick.
 * Listeners and click handlers run on the player's region thread.
 */
public final class PaperConnect extends LeafConnect {

    private final Plugin plugin;
    private final int maxMessageBytes;
    private final Bridge bridge = new Bridge();
    private final PlayerScheduler playerScheduler;
    private final ConcurrentHashMap<UUID, PlayerLink> links = new ConcurrentHashMap<UUID, PlayerLink>();
    private final ConcurrentHashMap<UUID, PlayerTransport> transports = new ConcurrentHashMap<UUID, PlayerTransport>();
    private final ConcurrentHashMap<UUID, WeakReference<Entity>> targets = new ConcurrentHashMap<UUID, WeakReference<Entity>>();
    private final Method ownedByCurrentRegion;
    private BukkitTask task;
    private volatile boolean reachCheck = true;

    private PaperConnect(Plugin plugin, PlayerScheduler playerScheduler) {
        super(ConnectLog.of(plugin.getLogger()));
        this.plugin = plugin;
        this.playerScheduler = playerScheduler;
        this.maxMessageBytes = serverMessageLimit();
        this.ownedByCurrentRegion = playerScheduler == null ? null : regionCheck();
    }

    private static Method regionCheck() {
        try {
            return Bukkit.class.getMethod("isOwnedByCurrentRegion", Entity.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Starts Leaf Server Connect for a plugin. Call it from {@code onEnable}. Logs go to the plugin logger. On Folia
     * the plugin also needs {@code folia-supported: true} in its {@code plugin.yml}.
     *
     * @param plugin the plugin
     * @return the running instance
     */
    public static PaperConnect enable(Plugin plugin) {
        return enable(plugin, FoliaScheduler.isFolia(PaperConnect.class.getClassLoader()) ? FoliaScheduler.create() : null);
    }

    /**
     * Starts the SDK with a per player scheduler (Folia), or with one Bukkit timer when it is null.
     */
    static PaperConnect enable(Plugin plugin, PlayerScheduler playerScheduler) {
        final PaperConnect connect = new PaperConnect(plugin, playerScheduler);
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, connect.bridge);
        plugin.getServer().getPluginManager().registerEvents(connect.bridge, plugin);
        if (playerScheduler == null) {
            connect.task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
                @Override
                public void run() {
                    connect.flush();
                }
            }, 1L, 1L);
        }
        return connect;
    }

    /**
     * Stops Leaf Server Connect: cancels the flush tasks, sends what is queued, unregisters the channel and listener,
     * and ends every session. Call it from {@code onDisable}. On Folia, frames still queued are dropped, because only
     * the thread that owns a player may send to them.
     */
    public void disable() {
        if (task != null) task.cancel();
        task = null;
        for (PlayerLink link : links.values()) link.cancel();
        links.clear();
        transports.clear();
        targets.clear();
        flush();
        HandlerList.unregisterAll(bridge);
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.unregisterIncomingPluginChannel(plugin, CHANNEL, bridge);
        messenger.unregisterOutgoingPluginChannel(plugin, CHANNEL);
        for (Session s : sessions()) disconnect(s.id());
    }

    /**
     * Returns the session of a player.
     *
     * @param player the player
     * @return the session, or null if the player has no Leaf Client session
     */
    public Session session(Player player) {
        return session(player.getUniqueId());
    }

    /**
     * Tells whether a player has a Leaf Client session.
     *
     * @param player the player
     * @return true if the player's client said hello with a supported protocol
     */
    public boolean isLeaf(Player player) {
        return isLeaf(player.getUniqueId());
    }

    /**
     * Turns the reach check for block and entity panel clicks on or off. The default is on.
     *
     * @param enabled false to accept clicks from any distance
     * @return this instance
     */
    public PaperConnect reachCheck(boolean enabled) {
        this.reachCheck = enabled;
        return this;
    }

    /**
     * Returns the largest frame this server sends to a player.
     *
     * @return {@link Limits#SERVER_MESSAGE_BYTES}, or {@link Limits#LEGACY_MESSAGE_BYTES} on servers whose plugin
     *         message limit is 32766 bytes or less or cannot be read
     */
    public int maxMessageBytes() {
        return maxMessageBytes;
    }

    /**
     * Checks that the player's eyes are within {@link WorldAnchor#maxClickDistance()} of the panel, using the block
     * position, or the current position of the entity or player plus the offset. The entity or player is looked up only
     * among the entities near the clicking player, which on Folia are in the player's own region. A missing entity, or
     * one that is not near the player, is out of reach.
     *
     * @param session the session of the player who clicked
     * @param anchor where the clicked panel is
     * @return true when the reach check is off or the player is close enough
     */
    @Override
    protected boolean inReach(Session session, WorldAnchor anchor) {
        if (!reachCheck) return true;
        Player player = session.player();
        Location eye = player.getEyeLocation();
        double x;
        double y;
        double z;
        switch (anchor.kind()) {
            case BLOCK:
                x = anchor.x();
                y = anchor.y();
                z = anchor.z();
                break;
            default: {
                Entity target = target(player, anchor);
                if (target == null) return false;
                Location at = target.getLocation();
                x = at.getX() + anchor.offsetX();
                y = at.getY() + anchor.offsetY();
                z = at.getZ() + anchor.offsetZ();
                break;
            }
        }
        double dx = eye.getX() - x;
        double dy = eye.getY() - y;
        double dz = eye.getZ() - z;
        double max = anchor.maxClickDistance();
        return dx * dx + dy * dy + dz * dz <= max * max;
    }

    @Override
    protected Object worldOf(Session session) {
        Player player = session.player();
        return player.getWorld().getUID();
    }

    @Override
    protected void started(Session session, Transport transport) {
        if (!(transport instanceof PlayerLink)) return;
        PlayerLink link = (PlayerLink) transport;
        UUID id = link.player.getUniqueId();
        PlayerLink old = links.put(id, link);
        if (old != null && old != link) old.cancel();
        if (old != link) link.start();
    }

    private Entity target(Player player, WorldAnchor anchor) {
        UUID id = player.getUniqueId();
        WeakReference<Entity> ref = targets.get(id);
        Entity cached = ref == null ? null : ref.get();
        if (cached != null && is(cached, anchor) && owned(cached) && cached.isValid() && cached.getWorld().equals(player.getWorld())) return cached;
        Entity found = nearby(player, anchor);
        if (found != null && found != player) targets.put(id, new WeakReference<Entity>(found));
        return found;
    }

    private boolean owned(Entity entity) {
        if (playerScheduler == null) return true;
        if (ownedByCurrentRegion == null) return false;
        try {
            return Boolean.TRUE.equals(ownedByCurrentRegion.invoke(null, entity));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean is(Entity e, WorldAnchor anchor) {
        return anchor.kind() == WorldAnchor.Kind.PLAYER ? e instanceof Player && e.getUniqueId().equals(anchor.player()) : e.getEntityId() == anchor.entityId();
    }

    static Entity nearby(Player player, WorldAnchor anchor) {
        if (is(player, anchor)) return player;
        double max = anchor.maxClickDistance();
        for (Entity e : player.getNearbyEntities(max + Math.abs(anchor.offsetX()), max + Math.abs(anchor.offsetY()) + 2, max + Math.abs(anchor.offsetZ()))) {
            if (is(e, anchor)) return e;
        }
        return null;
    }

    static int serverMessageLimit() {
        try {
            int max = Messenger.class.getField("MAX_MESSAGE_SIZE").getInt(null);
            return max <= 32766 ? Limits.LEGACY_MESSAGE_BYTES : Limits.SERVER_MESSAGE_BYTES;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Limits.LEGACY_MESSAGE_BYTES;
        }
    }

    private Transport transport(Player player) {
        UUID id = player.getUniqueId();
        if (playerScheduler == null) {
            PlayerTransport t = transports.get(id);
            if (t != null && t.player == player) return t;
            PlayerTransport fresh = new PlayerTransport(player);
            transports.put(id, fresh);
            return fresh;
        }
        PlayerLink link = links.get(id);
        return link != null && link.player == player ? link : new PlayerLink(player);
    }

    private final class PlayerTransport implements Transport {
        private final Player player;

        PlayerTransport(Player player) {
            this.player = player;
        }

        @Override
        public void send(byte[] data) {
            if (player.isOnline()) player.sendPluginMessage(plugin, CHANNEL, data);
        }

        @Override
        public int maxMessageBytes() {
            return maxMessageBytes;
        }
    }

    /**
     * The Folia transport of one player. {@link #send} only queues the frame. The task on the player's entity
     * scheduler flushes the session and sends the queue in order, on the thread that owns the player.
     */
    private final class PlayerLink implements Transport {
        private final Player player;
        private final Queue<byte[]> pending = new ConcurrentLinkedQueue<byte[]>();
        private volatile Runnable cancel;
        private int failures;
        private long lastFailureLog = Long.MIN_VALUE / 2;

        PlayerLink(Player player) {
            this.player = player;
        }

        void start() {
            cancel = playerScheduler.everyTick(plugin, player, new Runnable() {
                @Override
                public void run() {
                    tick();
                }
            }, new Runnable() {
                @Override
                public void run() {
                    links.remove(player.getUniqueId(), PlayerLink.this);
                }
            });
            if (cancel == null) links.remove(player.getUniqueId(), this);
        }

        void cancel() {
            Runnable c = cancel;
            cancel = null;
            if (c != null) c.run();
        }

        void tick() {
            Session s = session(player.getUniqueId());
            if (s != null && s.player() == player) s.flush();
            byte[] frame;
            while ((frame = pending.poll()) != null) {
                try {
                    if (player.isOnline()) player.sendPluginMessage(plugin, CHANNEL, frame);
                } catch (RuntimeException e) {
                    failures++;
                    long now = System.currentTimeMillis();
                    if (now - lastFailureLog >= 5000) {
                        lastFailureLog = now;
                        log().error("could not send " + failures + " Server Connect message" + (failures == 1 ? "" : "s") + " to " + player.getName(), e);
                        failures = 0;
                    }
                    return;
                }
            }
        }

        @Override
        public void send(byte[] data) {
            pending.add(data);
        }

        @Override
        public int maxMessageBytes() {
            return maxMessageBytes;
        }
    }

    /**
     * The plugin message and quit listener that {@link #enable(Plugin)} registers. It is public only because the
     * server calls it; plugins do not use it.
     */
    public final class Bridge implements Listener, PluginMessageListener {

        private Bridge() {}

        /**
         * Passes a message on {@link LeafConnect#CHANNEL} to {@link LeafConnect#receive}.
         *
         * @param channel the channel
         * @param player the sender
         * @param message the message bytes
         */
        @Override
        public void onPluginMessageReceived(String channel, Player player, byte[] message) {
            if (!CHANNEL.equals(channel)) return;
            receive(player.getUniqueId(), player.getName(), player, message, transport(player));
        }

        /**
         * Ends the session of a player who quit.
         *
         * @param event the quit event
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            disconnect(player.getUniqueId());
            targets.remove(player.getUniqueId());
            PlayerTransport t = transports.get(player.getUniqueId());
            if (t != null && t.player == player) transports.remove(player.getUniqueId(), t);
            PlayerLink link = links.get(player.getUniqueId());
            if (link != null && link.player == player && links.remove(player.getUniqueId(), link)) link.cancel();
        }
    }
}
