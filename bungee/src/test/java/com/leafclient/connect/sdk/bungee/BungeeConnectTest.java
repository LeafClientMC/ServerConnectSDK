package com.leafclient.connect.sdk.bungee;

import com.leafclient.connect.sdk.LeafConnect;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BungeeConnectTest {

    private static final byte[] HELLO = ("{\"t\":\"hello\",\"v\":1,\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"toast\"]}")
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void clientMessagesAreHandledOnTheProxyAndTheChannelIsUnregisteredOnDisable() throws ReflectiveOperationException {
        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getLogger()).thenReturn(Logger.getLogger("BungeeConnectTest"));
        PluginManager plugins = mock(PluginManager.class);
        when(proxy.getPluginManager()).thenReturn(plugins);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        when(scheduler.schedule(any(Plugin.class), any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(mock(ScheduledTask.class));
        when(proxy.getScheduler()).thenReturn(scheduler);
        PluginDescription description = new PluginDescription();
        description.setName("Test");
        Logger logger = Logger.getLogger("BungeeConnectTest");
        Plugin plugin = new Plugin(proxy, description) {
            @Override
            public ProxyServer getProxy() {
                return proxy;
            }

            @Override
            public Logger getLogger() {
                return logger;
            }
        };

        BungeeConnect connect = BungeeConnect.enable(plugin);
        Field bridgeField = BungeeConnect.class.getDeclaredField("bridge");
        bridgeField.setAccessible(true);
        BungeeConnect.Bridge bridge = (BungeeConnect.Bridge) bridgeField.get(connect);
        verify(plugins).registerListener(plugin, (Listener) bridge);

        UUID id = UUID.randomUUID();
        ProxiedPlayer steve = mock(ProxiedPlayer.class);
        when(steve.getUniqueId()).thenReturn(id);
        when(steve.getName()).thenReturn("Steve");
        Server backend = mock(Server.class);
        when(backend.getInfo()).thenReturn(mock(ServerInfo.class));
        PluginMessageEvent event = new PluginMessageEvent(steve, backend, LeafConnect.CHANNEL, HELLO);
        bridge.onMessage(event);
        assertTrue(event.isCancelled(), "the message must not be forwarded to the backend");
        assertTrue(connect.isLeaf(steve));

        Field transports = BungeeConnect.class.getDeclaredField("transports");
        transports.setAccessible(true);
        Object first = ((Map<?, ?>) transports.get(connect)).get(id);
        bridge.onMessage(new PluginMessageEvent(steve, backend, LeafConnect.CHANNEL, HELLO));
        assertSame(first, ((Map<?, ?>) transports.get(connect)).get(id));

        connect.disable();
        verify(proxy).unregisterChannel(LeafConnect.CHANNEL);
    }
}
