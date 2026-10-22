package com.leafclient.connect.sdk.velocity;

import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConnectTest {

    private static final byte[] HELLO = ("{\"t\":\"hello\",\"v\":1,\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"toast\"]}")
            .getBytes(StandardCharsets.UTF_8);

    static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> answer) {
        Object proxy = Proxy.newProxyInstance(VelocityConnectTest.class.getClassLoader(), new Class<?>[]{type}, (self, method, a) -> {
            Object[] args = a == null ? new Object[0] : a;
            switch (method.getName()) {
                case "equals":
                    return self == args[0];
                case "hashCode":
                    return System.identityHashCode(self);
                case "toString":
                    return type.getSimpleName();
                default:
                    break;
            }
            Object result = answer.apply(method.getName(), args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == void.class) return null;
            return 0;
        });
        return type.cast(proxy);
    }

    @Test
    void clientMessagesAreHandledOnTheProxyAndTheChannelIsUnregisteredOnDisable() throws ReflectiveOperationException {
        List<Object> unregistered = new ArrayList<>();
        Object[] listener = new Object[1];
        ChannelRegistrar registrar = fake(ChannelRegistrar.class, (m, a) -> {
            if (m.equals("unregister")) unregistered.addAll(List.of((Object[]) a[0]));
            return null;
        });
        EventManager events = fake(EventManager.class, (m, a) -> {
            if (m.equals("register") && a.length == 2) listener[0] = a[1];
            return null;
        });
        ScheduledTask task = fake(ScheduledTask.class, (m, a) -> null);
        Object[] builder = new Object[1];
        builder[0] = fake(Scheduler.TaskBuilder.class, (m, a) -> m.equals("schedule") ? task : builder[0]);
        Scheduler scheduler = fake(Scheduler.class, (m, a) -> builder[0]);
        ProxyServer proxy = fake(ProxyServer.class, (m, a) -> {
            switch (m) {
                case "getChannelRegistrar":
                    return registrar;
                case "getEventManager":
                    return events;
                case "getScheduler":
                    return scheduler;
                default:
                    return null;
            }
        });
        VelocityConnect connect = VelocityConnect.enable(new Object(), proxy, fake(Logger.class, (m, a) -> null));
        UUID id = UUID.randomUUID();
        Player steve = fake(Player.class, (m, a) -> {
            switch (m) {
                case "getUniqueId":
                    return id;
                case "getUsername":
                    return "Steve";
                default:
                    return null;
            }
        });
        ChannelMessageSink backend = fake(ChannelMessageSink.class, (m, a) -> null);
        PluginMessageEvent event = new PluginMessageEvent(steve, backend, VelocityConnect.IDENTIFIER, HELLO);
        ((VelocityConnect.Listener) listener[0]).onMessage(event);
        assertFalse(event.getResult().isAllowed(), "the message must not be forwarded to the backend");
        assertTrue(connect.isLeaf(steve));

        Field transports = VelocityConnect.class.getDeclaredField("transports");
        transports.setAccessible(true);
        Object first = ((Map<?, ?>) transports.get(connect)).get(id);
        ((VelocityConnect.Listener) listener[0]).onMessage(new PluginMessageEvent(steve, backend, VelocityConnect.IDENTIFIER, HELLO));
        assertSame(first, ((Map<?, ?>) transports.get(connect)).get(id));

        connect.disable();
        assertEquals(List.of(VelocityConnect.IDENTIFIER), unregistered);
    }
}
