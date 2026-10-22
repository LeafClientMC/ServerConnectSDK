package com.leafclient.connect.sdk.velocity;

import com.leafclient.connect.sdk.ConnectLog;
import com.leafclient.connect.sdk.LeafConnect;
import com.leafclient.connect.sdk.Limits;
import com.leafclient.connect.sdk.Session;
import com.leafclient.connect.sdk.Transport;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Leaf Server Connect for a Velocity proxy. {@link #enable(Object, ProxyServer, Logger)} registers the
 * {@link LeafConnect#CHANNEL} plugin channel and a listener, and flushes every 50 ms. Frames are at most
 * {@link Limits#SERVER_MESSAGE_BYTES}. There is no reach check for block and entity panel clicks on a proxy.
 */
public final class VelocityConnect extends LeafConnect {

    /** The Velocity channel identifier for {@link LeafConnect#CHANNEL}. */
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from(CHANNEL);

    private final Object plugin;
    private final ProxyServer proxy;
    private final Listener listener = new Listener();
    private final ConcurrentHashMap<UUID, PlayerTransport> transports = new ConcurrentHashMap<>();
    private ScheduledTask task;
    private boolean claimed;

    private VelocityConnect(Object plugin, ProxyServer proxy, Logger logger) {
        super(log(logger));
        this.plugin = plugin;
        this.proxy = proxy;
    }

    /**
     * Starts Leaf Server Connect for a plugin. Call it from the proxy initialize event.
     *
     * @param plugin the plugin instance
     * @param proxy the proxy server
     * @param logger the logger the SDK writes to
     * @return the running instance
     */
    public static VelocityConnect enable(Object plugin, ProxyServer proxy, Logger logger) {
        VelocityConnect connect = new VelocityConnect(plugin, proxy, logger);
        claimChannel();
        connect.claimed = true;
        proxy.getChannelRegistrar().register(IDENTIFIER);
        proxy.getEventManager().register(plugin, connect.listener);
        connect.task = proxy.getScheduler().buildTask(plugin, connect::flush).repeat(50, TimeUnit.MILLISECONDS).schedule();
        return connect;
    }

    /**
     * Stops Leaf Server Connect: cancels the flush task, sends what is queued, unregisters the listener and ends every
     * session. Call it on proxy shutdown.
     */
    public void disable() {
        if (task != null) task.cancel();
        task = null;
        flush();
        proxy.getEventManager().unregisterListener(plugin, listener);
        if (claimed) {
            claimed = false;
            if (releaseChannel()) proxy.getChannelRegistrar().unregister(IDENTIFIER);
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

    private static ConnectLog log(Logger logger) {
        return new ConnectLog() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }

            @Override
            public void debug(String message) {
                logger.debug(message);
            }

            @Override
            public boolean debugEnabled() {
                return logger.isDebugEnabled();
            }

            @Override
            public void error(String message, Throwable error) {
                logger.error(message, error);
            }
        };
    }

    private static final class PlayerTransport implements Transport {
        private final Player player;

        PlayerTransport(Player player) {
            this.player = player;
        }

        @Override
        public void send(byte[] data) {
            if (player.isActive()) player.sendPluginMessage(IDENTIFIER, data);
        }

        @Override
        public int maxMessageBytes() {
            return Limits.SERVER_MESSAGE_BYTES;
        }
    }

    /**
     * The plugin message and disconnect listener that {@link #enable(Object, ProxyServer, Logger)} registers. It is
     * public only because Velocity calls it; plugins do not use it.
     */
    public final class Listener {

        private Listener() {}

        /**
         * Passes a message from a player on {@link #IDENTIFIER} to {@link LeafConnect#receive}.
         *
         * @param event the plugin message event
         */
        @Subscribe(async = false)
        public void onMessage(PluginMessageEvent event) {
            if (!IDENTIFIER.equals(event.getIdentifier())) return;
            if (!(event.getSource() instanceof Player player)) return;
            event.setResult(PluginMessageEvent.ForwardResult.handled());
            PlayerTransport t = transports.get(player.getUniqueId());
            if (t == null || t.player != player) {
                t = new PlayerTransport(player);
                transports.put(player.getUniqueId(), t);
            }
            receive(player.getUniqueId(), player.getUsername(), player, event.getData(), t);
            if (!player.isActive()) forget(player);
        }

        private void forget(Player player) {
            disconnect(player.getUniqueId(), player);
            PlayerTransport t = transports.get(player.getUniqueId());
            if (t != null && t.player == player) transports.remove(player.getUniqueId(), t);
        }

        /**
         * Ends the session of a player who disconnected.
         *
         * @param event the disconnect event
         */
        @Subscribe(async = false)
        public void onDisconnect(DisconnectEvent event) {
            forget(event.getPlayer());
        }

        /**
         * Gives a player who joined a backend server a fresh hello allowance, because the client says hello again on
         * every server.
         *
         * @param event the server connected event
         */
        @Subscribe(async = false)
        public void onServerConnected(ServerConnectedEvent event) {
            allowHello(event.getPlayer().getUniqueId());
        }
    }
}
