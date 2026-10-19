package com.leafclient.connect.sdk.bukkit;

import com.leafclient.connect.sdk.ConnectLog;
import com.leafclient.connect.sdk.LeafConnect;
import com.leafclient.connect.sdk.Limits;
import com.leafclient.connect.sdk.Session;
import com.leafclient.connect.sdk.Transport;
import com.leafclient.connect.sdk.WorldAnchor;
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

/**
 * Leaf Server Connect for Bukkit, Spigot, Paper and Purpur. {@link #enable(Plugin)} registers the
 * {@link LeafConnect#CHANNEL} plugin channel and a quit listener, and flushes every tick. Frames are at most
 * {@link Limits#SERVER_MESSAGE_BYTES}, or {@link Limits#LEGACY_MESSAGE_BYTES} on servers whose plugin message limit is
 * 32766 bytes or less. Clicks on block and entity panels are checked for reach (see {@link #reachCheck(boolean)}).
 */
public final class BukkitConnect extends LeafConnect {

    private final Plugin plugin;
    private final int maxMessageBytes;
    private final Bridge bridge = new Bridge();
    private BukkitTask task;
    private volatile boolean reachCheck = true;

    private BukkitConnect(Plugin plugin) {
        super(ConnectLog.of(plugin.getLogger()));
        this.plugin = plugin;
        this.maxMessageBytes = serverMessageLimit();
    }

    /**
     * Starts Leaf Server Connect for a plugin. Call it from {@code onEnable}. Logs go to the plugin logger.
     *
     * @param plugin the plugin
     * @return the running instance
     */
    public static BukkitConnect enable(Plugin plugin) {
        BukkitConnect connect = new BukkitConnect(plugin);
        Messenger messenger = plugin.getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(plugin, CHANNEL);
        messenger.registerIncomingPluginChannel(plugin, CHANNEL, connect.bridge);
        plugin.getServer().getPluginManager().registerEvents(connect.bridge, plugin);
        connect.task = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                connect.flush();
            }
        }, 1L, 1L);
        return connect;
    }

    /**
     * Stops Leaf Server Connect: cancels the flush task, sends what is queued, unregisters the channel and listener,
     * and ends every session. Call it from {@code onDisable}.
     */
    public void disable() {
        if (task != null) task.cancel();
        task = null;
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
    public BukkitConnect reachCheck(boolean enabled) {
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
     * position, or the current position of the entity or player plus the offset. A missing entity, or a player in
     * another world, is out of reach.
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
            case PLAYER: {
                Player target = plugin.getServer().getPlayer(anchor.player());
                if (target == null || !target.getWorld().equals(player.getWorld())) return false;
                Location at = target.getLocation();
                x = at.getX() + anchor.offsetX();
                y = at.getY() + anchor.offsetY();
                z = at.getZ() + anchor.offsetZ();
                break;
            }
            default: {
                Entity target = null;
                for (Entity e : player.getWorld().getEntities()) {
                    if (e.getEntityId() == anchor.entityId()) {
                        target = e;
                        break;
                    }
                }
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

    static int serverMessageLimit() {
        try {
            int max = Messenger.class.getField("MAX_MESSAGE_SIZE").getInt(null);
            return max <= 32766 ? Limits.LEGACY_MESSAGE_BYTES : Limits.SERVER_MESSAGE_BYTES;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Limits.LEGACY_MESSAGE_BYTES;
        }
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
     * The plugin message and quit listener that {@link #enable(Plugin)} registers. It is public only because Bukkit
     * calls it; plugins do not use it.
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
            receive(player.getUniqueId(), player.getName(), player, message, new PlayerTransport(player));
        }

        /**
         * Ends the session of a player who quit.
         *
         * @param event the quit event
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            disconnect(event.getPlayer().getUniqueId());
        }
    }
}
