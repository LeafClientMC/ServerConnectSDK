package com.leafclient.connect.sdk.paper;

import com.leafclient.connect.sdk.LeafConnect;
import com.leafclient.connect.sdk.Session;
import com.leafclient.connect.sdk.Toast;
import com.leafclient.connect.sdk.WorldAnchor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperConnectTest {

    private static final byte[] HELLO = ("{\"t\":\"hello\",\"v\":1,\"client\":{\"name\":\"Leaf Client\"},"
            + "\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"toast\"]}").getBytes(StandardCharsets.UTF_8);

    interface Answer {
        Object answer(String method, Object[] args);
    }

    public interface FoliaEntity {
        FoliaEntityScheduler getScheduler();
    }

    public interface FoliaEntityScheduler {
        FoliaTask runAtFixedRate(Plugin plugin, Consumer<FoliaTask> task, Runnable retired, long delay, long period);
    }

    public interface FoliaTask {
        Object cancel();
    }

    static <T> T fake(Class<T> type, Answer answer, Class<?>... more) {
        Class<?>[] types = new Class<?>[more.length + 1];
        types[0] = type;
        System.arraycopy(more, 0, types, 1, more.length);
        Object proxy = Proxy.newProxyInstance(PaperConnectTest.class.getClassLoader(), types, (self, method, a) -> {
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
            Object result = answer.answer(method.getName(), args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == double.class) return 0.0;
            if (method.getReturnType() == void.class) return null;
            return 0;
        });
        return type.cast(proxy);
    }

    static final class Server_ {
        final List<Object[]> timers = new ArrayList<>();
        final List<String> cancelledTimers = new ArrayList<>();
        final List<String> sent = new ArrayList<>();
        final List<Thread> sentOn = new ArrayList<>();
        final List<World> worlds = new ArrayList<>();
        PluginMessageListener listener;
        Listener events;
        Object entityScheduler;
        final Plugin plugin;

        Server_() {
            Messenger messenger = fake(Messenger.class, (m, a) -> {
                if (m.equals("registerIncomingPluginChannel")) listener = (PluginMessageListener) a[2];
                return null;
            });
            PluginManager plugins = fake(PluginManager.class, (m, a) -> {
                if (m.equals("registerEvents")) events = (Listener) a[0];
                return null;
            });
            BukkitScheduler scheduler = fake(BukkitScheduler.class, (m, a) -> {
                if (!m.equals("runTaskTimer")) throw new AssertionError("unexpected scheduler call " + m);
                timers.add(a);
                return fake(BukkitTask.class, (m2, a2) -> {
                    if (m2.equals("cancel")) cancelledTimers.add("timer");
                    return null;
                });
            });
            Server server = fake(Server.class, (m, a) -> {
                switch (m) {
                    case "getMessenger":
                        return messenger;
                    case "getPluginManager":
                        return plugins;
                    case "getScheduler":
                        return scheduler;
                    case "getWorlds":
                        return worlds;
                    default:
                        return null;
                }
            });
            Logger logger = Logger.getLogger("PaperConnectTest");
            plugin = fake(Plugin.class, (m, a) -> {
                switch (m) {
                    case "getServer":
                        return server;
                    case "getLogger":
                        return logger;
                    case "getName":
                        return "Test";
                    case "isEnabled":
                        return true;
                    default:
                        return null;
                }
            });
        }

        Player player(String name, Class<?>... more) {
            UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            return fake(Player.class, (m, a) -> {
                switch (m) {
                    case "getUniqueId":
                        return id;
                    case "getName":
                        return name;
                    case "isOnline":
                        return true;
                    case "getScheduler":
                        return entityScheduler;
                    case "sendPluginMessage":
                        assertSame(plugin, a[0]);
                        assertEquals(LeafConnect.CHANNEL, a[1]);
                        synchronized (sent) {
                            sent.add(name);
                            sentOn.add(Thread.currentThread());
                        }
                        return null;
                    default:
                        return null;
                }
            }, more);
        }

        void hello(Player player) {
            listener.onPluginMessageReceived(LeafConnect.CHANNEL, player, HELLO);
        }
    }

    static final class FakeFolia implements PlayerScheduler {
        final List<Player> players = new ArrayList<>();
        final List<Runnable> tasks = new ArrayList<>();
        final List<Runnable> retired = new ArrayList<>();
        int cancelled;

        @Override
        public Runnable everyTick(Plugin plugin, Player player, Runnable task, Runnable retire) {
            players.add(player);
            tasks.add(task);
            retired.add(retire);
            return () -> cancelled++;
        }
    }

    private static void runOnAnotherThread(Runnable r) throws InterruptedException {
        Thread t = new Thread(r, "not the player's thread");
        t.start();
        t.join();
    }

    @Test
    void foliaIsDetectedByItsRegionizedServerClass() {
        assertTrue(FoliaScheduler.isFolia(PaperConnectTest.class.getClassLoader()));
        assertFalse(FoliaScheduler.isFolia(new URLClassLoader(new URL[0], null)));
    }

    @Test
    void paperSpigotAndBukkitFlushFromOneTimerEveryTick() {
        Server_ server = new Server_();
        PaperConnect connect = PaperConnect.enable(server.plugin, null);
        connect.onReady(s -> s.send(Toast.create("hi").title("Hi")));
        assertEquals(1, server.timers.size());
        Object[] timer = server.timers.get(0);
        assertSame(server.plugin, timer[0]);
        assertEquals(1L, timer[2]);
        assertEquals(1L, timer[3]);

        Player steve = server.player("Steve");
        server.hello(steve);
        assertTrue(connect.isLeaf(steve));
        assertTrue(server.sent.isEmpty());

        ((Runnable) timer[1]).run();
        assertEquals(Collections.singletonList("Steve"), server.sent);

        connect.disable();
        assertEquals(Collections.singletonList("timer"), server.cancelledTimers);
        assertFalse(connect.isLeaf(steve));
    }

    @Test
    void foliaFlushesEachPlayerOnTheirOwnSchedulerAndNeverUsesTheBukkitScheduler() throws InterruptedException {
        Server_ server = new Server_();
        FakeFolia folia = new FakeFolia();
        PaperConnect connect = PaperConnect.enable(server.plugin, folia);
        connect.onReady(s -> s.send(Toast.create("hi").title("Hi")));
        assertTrue(server.timers.isEmpty());

        Player steve = server.player("Steve");
        Player alex = server.player("Alex");
        server.hello(steve);
        server.hello(alex);
        assertEquals(Arrays.asList(steve, alex), folia.players);

        runOnAnotherThread(connect::flush);
        assertTrue(server.sent.isEmpty(), "frames wait for the player's own thread");

        folia.tasks.get(0).run();
        assertEquals(Collections.singletonList("Steve"), server.sent);
        assertSame(Thread.currentThread(), server.sentOn.get(0));

        folia.tasks.get(1).run();
        assertEquals(Arrays.asList("Steve", "Alex"), server.sent);

        server.hello(steve);
        assertEquals(2, folia.players.size(), "one task per player, not per message");
        folia.tasks.get(0).run();
        assertEquals(Arrays.asList("Steve", "Alex", "Steve"), server.sent);

        ((PaperConnect.Bridge) server.events).onQuit(new PlayerQuitEvent(steve, "bye"));
        assertEquals(1, folia.cancelled);
        assertFalse(connect.isLeaf(steve));
        assertTrue(connect.isLeaf(alex));

        connect.disable();
        assertEquals(2, folia.cancelled);
        assertTrue(server.cancelledTimers.isEmpty());
    }

    @Test
    void foliaStartsANewTaskWhenThePlayerRejoins() {
        Server_ server = new Server_();
        FakeFolia folia = new FakeFolia();
        PaperConnect connect = PaperConnect.enable(server.plugin, folia);
        Player first = server.player("Steve");
        server.hello(first);
        folia.retired.get(0).run();

        Player again = server.player("Steve");
        server.hello(again);
        assertEquals(Arrays.asList(first, again), folia.players);
        assertSame(again, connect.session(again).player());
        assertEquals(0, folia.cancelled);
    }

    @Test
    void foliaSchedulerRunsTheTaskEveryTickOnTheEntityScheduler() throws ReflectiveOperationException {
        Server_ server = new Server_();
        List<Object[]> scheduled = new ArrayList<>();
        List<String> cancelled = new ArrayList<>();
        FoliaTask handle = fake(FoliaTask.class, (m, a) -> {
            cancelled.add(m);
            return null;
        });
        server.entityScheduler = fake(FoliaEntityScheduler.class, (m, a) -> {
            scheduled.add(a);
            return handle;
        });
        Player steve = server.player("Steve", FoliaEntity.class);
        int[] runs = new int[1];
        Runnable retired = () -> { };

        Runnable cancel = new FoliaScheduler(FoliaEntity.class).everyTick(server.plugin, steve, () -> runs[0]++, retired);
        assertEquals(1, scheduled.size());
        Object[] args = scheduled.get(0);
        assertSame(server.plugin, args[0]);
        assertSame(retired, args[2]);
        assertEquals(1L, args[3]);
        assertEquals(1L, args[4]);

        @SuppressWarnings("unchecked")
        Consumer<Object> body = (Consumer<Object>) args[1];
        body.accept(handle);
        body.accept(handle);
        assertEquals(2, runs[0]);

        cancel.run();
        assertEquals(Collections.singletonList("cancel"), cancelled);
    }

    private static WorldAnchor anchor(WorldAnchor.Kind kind, int entityId, UUID player) throws ReflectiveOperationException {
        Constructor<WorldAnchor> c = WorldAnchor.class.getDeclaredConstructor(WorldAnchor.Kind.class, double.class, double.class, double.class,
                int.class, UUID.class, double[].class, double.class, double.class, int[].class);
        c.setAccessible(true);
        return c.newInstance(kind, 0.0, 0.0, 0.0, entityId, player, new double[]{1, 2, 0}, 6.0, 0.01, new int[]{100, 50});
    }

    @Test
    void reachCheckLooksOnlyAtEntitiesNearTheClickingPlayer() throws ReflectiveOperationException {
        List<Object[]> lookups = new ArrayList<>();
        UUID alexId = UUID.randomUUID();
        Entity cow = fake(Entity.class, (m, a) -> m.equals("getEntityId") ? 42 : null);
        Player alex = fake(Player.class, (m, a) -> {
            if (m.equals("getUniqueId")) return alexId;
            if (m.equals("getEntityId")) return 7;
            return null;
        });
        Player steve = fake(Player.class, (m, a) -> {
            switch (m) {
                case "getNearbyEntities":
                    lookups.add(a);
                    return Arrays.<Entity>asList(cow, alex);
                case "getEntityId":
                    return 1;
                case "getUniqueId":
                    return UUID.nameUUIDFromBytes(new byte[]{1});
                case "getWorld":
                    throw new AssertionError("the reach check must not scan the world");
                default:
                    return null;
            }
        });
        assertSame(cow, PaperConnect.nearby(steve, anchor(WorldAnchor.Kind.ENTITY, 42, null)));
        assertSame(alex, PaperConnect.nearby(steve, anchor(WorldAnchor.Kind.PLAYER, -1, alexId)));
        assertNull(PaperConnect.nearby(steve, anchor(WorldAnchor.Kind.ENTITY, 99, null)));
        assertSame(steve, PaperConnect.nearby(steve, anchor(WorldAnchor.Kind.ENTITY, 1, null)));
        double max = anchor(WorldAnchor.Kind.ENTITY, 42, null).maxClickDistance();
        assertEquals(3, lookups.size());
        assertEquals(max + 1, (double) lookups.get(0)[0], 1e-9);
        assertEquals(max + 4, (double) lookups.get(0)[1], 1e-9);
        assertEquals(max, (double) lookups.get(0)[2], 1e-9);
    }

    @Test
    void foliaSchedulerReturnsNullWhenThePlayerHasLeft() throws ReflectiveOperationException {
        Server_ server = new Server_();
        server.entityScheduler = fake(FoliaEntityScheduler.class, (m, a) -> null);
        Player gone = server.player("Gone", FoliaEntity.class);
        assertNull(new FoliaScheduler(FoliaEntity.class).everyTick(server.plugin, gone, () -> { }, () -> { }));
    }

    @Test
    void foliaStartsNoTaskBeforeAValidHello() {
        Server_ server = new Server_();
        FakeFolia folia = new FakeFolia();
        PaperConnect.enable(server.plugin, folia);
        Player steve = server.player("Steve");
        server.listener.onPluginMessageReceived(LeafConnect.CHANNEL, steve, "{\"v\":1,\"t\":\"closed\"}".getBytes(StandardCharsets.UTF_8));
        server.listener.onPluginMessageReceived(LeafConnect.CHANNEL, steve,
                "{\"v\":1,\"t\":\"hello\",\"proto\":{\"min\":7,\"max\":9}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(folia.players.isEmpty());
        server.hello(steve);
        assertEquals(1, folia.players.size());
    }

    @Test
    void foliaForgetsALinkWhoseTaskCouldNotStart() throws ReflectiveOperationException {
        Server_ server = new Server_();
        PaperConnect connect = PaperConnect.enable(server.plugin, (plugin, player, task, retired) -> null);
        server.hello(server.player("Steve"));
        Field links = PaperConnect.class.getDeclaredField("links");
        links.setAccessible(true);
        assertTrue(((Map<?, ?>) links.get(connect)).isEmpty());
    }

    @Test
    void paperReusesOneTransportPerPlayer() throws ReflectiveOperationException {
        Server_ server = new Server_();
        PaperConnect connect = PaperConnect.enable(server.plugin, null);
        Player steve = server.player("Steve");
        Method transport = PaperConnect.class.getDeclaredMethod("transport", Player.class);
        transport.setAccessible(true);
        assertSame(transport.invoke(connect, steve), transport.invoke(connect, steve));
    }

    @Test
    void entityReachCheckReusesTheEntityItFound() throws ReflectiveOperationException {
        Server_ server = new Server_();
        PaperConnect connect = PaperConnect.enable(server.plugin, null);
        World world = fake(World.class, (m, a) -> null);
        Entity cow = fake(Entity.class, (m, a) -> {
            switch (m) {
                case "getEntityId":
                    return 42;
                case "isValid":
                    return true;
                case "getWorld":
                    return world;
                case "getLocation":
                    return new Location(world, 2, 64, 0);
                default:
                    return null;
            }
        });
        int[] scans = new int[1];
        UUID id = UUID.nameUUIDFromBytes(new byte[]{2});
        Player steve = fake(Player.class, (m, a) -> {
            switch (m) {
                case "getUniqueId":
                    return id;
                case "getName":
                    return "Steve";
                case "isOnline":
                    return true;
                case "getEntityId":
                    return 1;
                case "getWorld":
                    return world;
                case "getEyeLocation":
                    return new Location(world, 0, 65.6, 0);
                case "getNearbyEntities":
                    scans[0]++;
                    return Collections.singletonList(cow);
                default:
                    return null;
            }
        });
        server.listener.onPluginMessageReceived(LeafConnect.CHANNEL, steve, HELLO);
        Session s = connect.session(steve);
        WorldAnchor anchor = anchor(WorldAnchor.Kind.ENTITY, 42, null);
        for (int i = 0; i < 3; i++) assertTrue(connect.inReach(s, anchor));
        assertEquals(1, scans[0]);
    }

    static World world(String name, World.Environment environment) {
        return fake(World.class, (m, a) -> {
            switch (m) {
                case "getName":
                    return name;
                case "getEnvironment":
                    return environment;
                default:
                    return null;
            }
        });
    }

    @Test
    void dimensionWithoutWorldKeysIsSkippedForExtraWorldsAndUsesTheEnvironmentForLegacyClients() {
        Server_ server = new Server_();
        World main = world("world", World.Environment.NORMAL);
        server.worlds.add(main);
        World[] at = {world("world_nether", World.Environment.NETHER)};
        PaperConnect connect = PaperConnect.enable(server.plugin, null);
        UUID modernId = UUID.randomUUID();
        UUID legacyId = UUID.randomUUID();
        Player modern = fake(Player.class, (m, a) -> m.equals("getUniqueId") ? modernId : m.equals("getWorld") ? at[0] : m.equals("getName") ? "Steve" : null);
        Player legacy = fake(Player.class, (m, a) -> m.equals("getUniqueId") ? legacyId : m.equals("getWorld") ? at[0] : m.equals("getName") ? "Alex" : null);
        server.listener.onPluginMessageReceived(LeafConnect.CHANNEL, modern, HELLO);
        server.listener.onPluginMessageReceived(LeafConnect.CHANNEL, legacy, ("{\"t\":\"hello\",\"v\":1,\"client\":{\"mc\":\"1.8.9\"},"
                + "\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"toast\"]}").getBytes(StandardCharsets.UTF_8));
        assertEquals("minecraft:the_nether", connect.dimensionOf(connect.session(modern)));
        at[0] = world("minigame", World.Environment.NORMAL);
        assertNull(connect.dimensionOf(connect.session(modern)));
        assertEquals("minecraft:overworld", connect.dimensionOf(connect.session(legacy)));
    }
}
