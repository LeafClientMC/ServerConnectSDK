package com.leafclient.connect.sdk.bungee;

import com.leafclient.connect.sdk.ConnectLog;
import com.leafclient.connect.sdk.LeafConnect;
import com.leafclient.connect.sdk.Limits;
import com.leafclient.connect.sdk.Session;
import com.leafclient.connect.sdk.Transport;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Leaf Server Connect for a BungeeCord or Waterfall proxy. {@link #enable(Plugin)} registers the
 * {@link LeafConnect#CHANNEL} plugin channel and a listener, and flushes every 50 ms. BungeeCord tells the client about
 * proxy channels only on the first join, so the listener answers every channel registration the client sends, which it
 * does after each server switch, with {@link LeafConnect#CHANNEL}. Frames are at most
 * {@link Limits#SERVER_MESSAGE_BYTES}. There is no reach check for block and entity panel clicks on a proxy.
 */
public final class BungeeConnect extends LeafConnect {

    private final Plugin plugin;
    private final Bridge bridge = new Bridge();
    private final ConcurrentHashMap<UUID, PlayerTransport> transports = new ConcurrentHashMap<UUID, PlayerTransport>();
    private ScheduledTask task;
    private boolean claimed;

    private BungeeConnect(Plugin plugin) {
        super(ConnectLog.of(plugin.getLogger()));
        this.plugin = plugin;
    }

    /**
     * Starts Leaf Server Connect for a plugin. Call it from {@code onEnable}. Logs go to the plugin logger.
     *
     * @param plugin the plugin
     * @return the running instance
     */
    public static BungeeConnect enable(Plugin plugin) {
        final BungeeConnect connect = new BungeeConnect(plugin);
        claimChannel();
        connect.claimed = true;
        plugin.getProxy().registerChannel(CHANNEL);
        plugin.getProxy().getPluginManager().registerListener(plugin, connect.bridge);
        connect.task = plugin.getProxy().getScheduler().schedule(plugin, new Runnable() {
            @Override
            public void run() {
                connect.flush();
            }
        }, 50, 50, TimeUnit.MILLISECONDS);
        return connect;
    }

    /**
     * Stops Leaf Server Connect: cancels the flush task, sends what is queued, unregisters the listener and ends every
     * session. Call it from {@code onDisable}.
     */
    public void disable() {
        if (task != null) task.cancel();
        task = null;
        flush();
        plugin.getProxy().getPluginManager().unregisterListener(bridge);
        if (claimed) {
            claimed = false;
            if (releaseChannel()) plugin.getProxy().unregisterChannel(CHANNEL);
        }
        for (Session s : sessions()) disconnect(s.id());
        transports.clear();
    }

    /**
     * Returns the session of a player.
     *
     * @param player the player
     * @return the session, or null if the player has no Leaf Client session
     */
    public Session session(ProxiedPlayer player) {
        return session(player.getUniqueId());
    }

    /**
     * Tells whether a player has a Leaf Client session.
     *
     * @param player the player
     * @return true if the player's client said hello with a supported protocol
     */
    public boolean isLeaf(ProxiedPlayer player) {
        return isLeaf(player.getUniqueId());
    }

    private static final class PlayerTransport implements Transport {
        private final ProxiedPlayer player;

        PlayerTransport(ProxiedPlayer player) {
            this.player = player;
        }

        @Override
        public void send(byte[] data) {
            if (player.isConnected()) player.sendData(CHANNEL, data);
        }

        @Override
        public int maxMessageBytes() {
            return Limits.SERVER_MESSAGE_BYTES;
        }
    }

    /**
     * The plugin message and disconnect listener that {@link #enable(Plugin)} registers. It is public only because
     * BungeeCord calls it; plugins do not use it.
     */
    public final class Bridge implements Listener {

        private Bridge() {}

        /**
         * Passes a message from a player on {@link LeafConnect#CHANNEL} to {@link LeafConnect#receive}.
         *
         * @param event the plugin message event
         */
        @EventHandler
        public void onMessage(PluginMessageEvent event) {
            if (!(event.getSender() instanceof ProxiedPlayer)) return;
            ProxiedPlayer player = (ProxiedPlayer) event.getSender();
            if ("minecraft:register".equals(event.getTag()) || "REGISTER".equals(event.getTag())) {
                player.sendData(event.getTag(), CHANNEL.getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (!CHANNEL.equals(event.getTag())) return;
            event.setCancelled(true);
            PlayerTransport t = transports.get(player.getUniqueId());
            if (t == null || t.player != player) {
                t = new PlayerTransport(player);
                transports.put(player.getUniqueId(), t);
            }
            receive(player.getUniqueId(), player.getName(), player, event.getData(), t);
        }

        /**
         * Ends the session of a player who disconnected.
         *
         * @param event the disconnect event
         */
        @EventHandler
        public void onQuit(PlayerDisconnectEvent event) {
            ProxiedPlayer player = event.getPlayer();
            disconnect(player.getUniqueId());
            PlayerTransport t = transports.get(player.getUniqueId());
            if (t != null && t.player == player) transports.remove(player.getUniqueId(), t);
        }

        /**
         * Gives a player who joined a backend server a fresh hello allowance, because the client says hello again on
         * every server.
         *
         * @param event the server connected event
         */
        @EventHandler
        public void onServerConnected(ServerConnectedEvent event) {
            allowHello(event.getPlayer().getUniqueId());
        }
    }
}
