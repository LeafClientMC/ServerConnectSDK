package com.leafclient.connect.sdk;

import com.leafclient.connect.core.ConnectDialogStyle;
import com.leafclient.connect.core.ConnectMenu;
import com.leafclient.connect.core.ConnectState;
import com.leafclient.connect.core.ConnectToast;
import com.leafclient.connect.core.ConnectWaypoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesTest {

    private static List<ConnectState.EffectKind> kinds(ClientSim sim) {
        List<ConnectState.EffectKind> out = new ArrayList<ConnectState.EffectKind>();
        for (ConnectState.Effect e : sim.effects) out.add(e.kind);
        return out;
    }

    @Test
    void menuWithTabsPagesStyleAndBadges() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        s.send(Menu.create("&lLeaf Network").subtitle("Bed Wars season 4").icon(Icon.item("minecraft:totem_of_undying")).accent("#2FA36B")
                .components(Menu.Look.MINECRAFT).font(Menu.Look.LEAF)
                .add(Menu.tab("signup", "Tournament").icon(Icon.item("minecraft:golden_sword")).page("Tournament sign-up", "Saturday 20:00")
                                .pageIcon(Icon.leaf("trophy")).order(2),
                        Menu.tab("shop", "Shop").icon(Icon.leaf("shop")).badge(Badge.of("SALE").bg("#2FA36B").pos(Badge.Pos.BOTTOM)).order(1),
                        Menu.tab("party", "Party").badge("2"))
                .defaultTab("shop"));
        sim.flush();
        sim.assertClean();
        ConnectMenu m = sim.state.menu();
        assertNotNull(m);
        assertEquals("Leaf Network", m.title.plain());
        assertEquals(ConnectMenu.Look.MINECRAFT, m.components);
        assertEquals(ConnectMenu.Look.LEAF, m.font);
        assertEquals("shop", m.tabs.get(0).id);
        assertEquals(ConnectMenu.Tab.class, m.tabs.get(0).getClass());
        assertEquals("BOTTOM", m.tabs.get(0).badge.pos.name());
        assertEquals("Tournament sign-up", m.tab("signup").page.title.plain());
        assertNotNull(m.tab("signup").page.icon);
        assertEquals("2", m.tab("party").badge.text.plain());
        assertEquals("shop", m.firstTab());
    }

    @Test
    void menuFontDefaultsToComponents() {
        ClientSim sim = new ClientSim();
        sim.hello().send(Menu.create("X").components(Menu.Look.MINECRAFT));
        sim.flush();
        sim.assertClean();
        assertEquals(ConnectMenu.Look.MINECRAFT, sim.state.menu().font);
    }

    @Test
    void toastsOfEveryKindWithButtons() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        for (Toast.Kind kind : Toast.Kind.values()) {
            s.send(Toast.create("t." + kind.name().toLowerCase()).title("Title").body("Body").kind(kind));
        }
        s.send(Toast.create("invite").title("Party invite").body("Alex invited you").icon(Icon.head(EveryElementTest.B)).accent("#8BE9A8")
                .duration(12000).button("accept", "Accept", click -> { }).button("deny", "Deny", Action.send("no").confirm("Sure?")));
        sim.flushUntilIdle(s);
        sim.assertClean();
        List<ConnectToast> toasts = new ArrayList<ConnectToast>();
        for (ConnectState.Effect e : sim.effects) if (e.kind == ConnectState.EffectKind.TOAST) toasts.add((ConnectToast) e.payload);
        assertEquals(5, toasts.size());
        assertEquals(ConnectToast.Kind.WARN, toasts.get(2).kind);
        ConnectToast invite = toasts.get(4);
        assertEquals(12000, invite.ms);
        assertEquals(2, invite.buttons.size());
        assertNotNull(invite.token);
        assertNotNull(invite.icon);
        assertEquals("no", invite.buttons.get(1).on.get(0).data);
        assertTrue(sim.types.contains("batch"));
    }

    @Test
    void waypointsAndRemoval() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        s.send(Waypoint.create("arena", "&6Event arena", 120, 70, -40).dim("minecraft:overworld").world("hub").color("#F5A524")
                .icon(Icon.item("minecraft:golden_sword")).beam(false).label(true).distance(false).ttl(60000)
                .onClick(click -> { }));
        s.send(Waypoint.create("shop", "Shop", 1, 2, 3).on(Action.send().confirm("Teleport?")));
        s.send(Waypoint.create("plain", "Plain", 0, 64, 0));
        sim.flush();
        sim.assertClean();
        ConnectWaypoint arena = sim.state.waypoint("arena");
        assertFalse(arena.beam);
        assertFalse(arena.distance);
        assertEquals(60000, arena.ttlMs);
        assertTrue(arena.clickable());
        assertNotNull(arena.token);
        assertEquals("hub", arena.world);
        assertFalse(sim.state.waypoint("plain").clickable());
        s.removeWaypoints("shop");
        sim.flush();
        assertNull(sim.state.waypoint("shop"));
        s.removeAllWaypoints();
        sim.flush();
        sim.assertClean();
        assertEquals(0, sim.state.waypoints(sim.clock[0]).size());
    }

    @Test
    void valuesAndTimers() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        s.send(Values.create().set("coins", 1250).set("name", "Steve").set("vip", true).countdown("bw.next", 45000, "Starting...")
                .stopwatch("round", 0).paused("frozen", 42000));
        s.setValue("q.bedwars", 214);
        sim.flush();
        sim.assertClean();
        assertEquals("1250", sim.state.values().raw("coins", sim.clock[0]));
        assertEquals("true", sim.state.values().raw("vip", sim.clock[0]));
        assertEquals("45", sim.state.values().raw("bw.next", sim.clock[0]));
        assertTrue(sim.state.values().hasTimers());
        s.send(Values.create().delete("coins", "name"));
        sim.flush();
        sim.assertClean();
        assertNull(sim.state.values().raw("coins", sim.clock[0]));
        assertEquals(5, sim.state.values().size());
    }

    @Test
    void patchRemoveOpenCloseKeyWorldResetLocksDialogWelcome() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        s.send(Welcome.create("&aLeaf Testbed").icon(Icon.leaf("leaf")).accent("#8BE9A8"));
        s.send(Menu.create("Menu").add(Menu.tab("main", "Main")));
        s.send(Panel.create("poll", Place.block(0, 64, 12)).group("hub").content(
                Ui.text("Vote").id("q"), Ui.button("a", "A"), Ui.button("b", "B")));
        s.send(Panel.create("vote", Place.front()).content(Ui.button("go", "Go")));
        s.send(Panel.create("side", Place.inventory(Place.Side.LEFT)).group("hub").content(Ui.text("x")));
        s.world("hub").keyOpensPanel("vote");
        s.send(DialogStyle.create().accent("#E5A50A").background(DialogStyle.Background.DIM).titleAlign(DialogStyle.TitleAlign.LEFT)
                .icon(Icon.item("minecraft:emerald")));
        s.lockMods("No minimap in ranked", "minimap", "freelook");
        sim.flush();
        sim.assertClean();
        assertEquals("Leaf Testbed", sim.state.welcome().name.plain());
        assertEquals("hub", sim.state.world());
        assertEquals("panel:vote", sim.state.keyTarget());
        assertEquals(ConnectDialogStyle.Background.DIM, sim.state.dialogStyle().bg);
        assertTrue(kinds(sim).contains(ConnectState.EffectKind.LOCK));

        String oldToken = sim.panel("poll").token;
        s.send(Patch.of("poll").enabled("a", false).label("a", "Voted").text("q", "&aThanks").badge("b", "1").newToken().z(3).bg("#202020"));
        sim.flush();
        sim.assertClean();
        assertFalse(sim.element("poll", "a").enabled);
        assertEquals("Voted", sim.element("poll", "a").label.get(0).text.plain());
        assertEquals(3, sim.panel("poll").z);
        assertFalse(oldToken.equals(sim.panel("poll").token));

        s.openMenu("main");
        sim.flush();
        assertTrue(kinds(sim).contains(ConnectState.EffectKind.OPEN_MENU));
        s.closeMenu();
        sim.flush();
        s.openPanel("vote");
        sim.advance(2500);
        sim.flush();
        assertTrue(kinds(sim).contains(ConnectState.EffectKind.OPEN_FRONT));
        s.closePanel("vote");
        s.removeGroup("hub");
        sim.flush();
        assertNull(sim.state.panel("poll"));
        assertNull(sim.state.panel("side"));
        assertNotNull(sim.state.panel("vote"));
        s.removePanels("vote");
        s.unlockMods("minimap");
        s.unlockAllMods();
        s.keyOpensMenu().keyDoesNothing();
        s.removeMenu();
        sim.advance(1000);
        sim.flush();
        sim.assertClean();
        assertNull(sim.state.menu());
        assertEquals("none", sim.state.keyTarget());
        s.send(Panel.create("again", Place.chat()).content(Ui.button("x", "X")));
        s.reset(false);
        sim.flush();
        sim.assertClean();
        assertTrue(sim.state.isEmpty());
        assertTrue(kinds(sim).contains(ConnectState.EffectKind.RESET));
        assertTrue(kinds(sim).contains(ConnectState.EffectKind.UNLOCK));
        assertEquals(Arrays.asList(), sim.errors);
    }
}
