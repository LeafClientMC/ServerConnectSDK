package com.leafclient.connect.sdk;

import com.google.gson.JsonPrimitive;
import com.leafclient.connect.core.ConnectAction;
import com.leafclient.connect.core.ConnectBadge;
import com.leafclient.connect.core.ConnectChunks;
import com.leafclient.connect.core.ConnectColor;
import com.leafclient.connect.core.ConnectDialogStyle;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectGuard;
import com.leafclient.connect.core.ConnectIcon;
import com.leafclient.connect.core.ConnectLeaderboard;
import com.leafclient.connect.core.ConnectLimits;
import com.leafclient.connect.core.ConnectMenu;
import com.leafclient.connect.core.ConnectPanel;
import com.leafclient.connect.core.ConnectPlace;
import com.leafclient.connect.core.ConnectProtocol;
import com.leafclient.connect.core.ConnectRay;
import com.leafclient.connect.core.ConnectToast;
import com.leafclient.connect.core.ConnectWaypoint;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncTest {

    @Test
    void everyClientLimitWithTheSameNameHasTheSameValue() throws Exception {
        int compared = 0;
        for (Field f : ConnectLimits.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) continue;
            Field ours;
            try {
                ours = Limits.class.getField(f.getName());
            } catch (NoSuchFieldException e) {
                continue;
            }
            assertEquals(f.getInt(null), ours.getInt(null), "Limits." + f.getName());
            compared++;
        }
        assertTrue(compared >= 29, "compared " + compared);
        assertEquals(ConnectLimits.SERVER_RATE_PER_SECOND, Limits.MESSAGES_PER_SECOND);
        assertEquals(ConnectLimits.SERVER_RATE_BURST, Limits.MESSAGES_BURST);
        assertEquals(ConnectLimits.CLIENT_MESSAGE_BYTES, LeafConnect.CLIENT_MESSAGE_BYTES);
        assertEquals(ConnectChunks.MAX_PARTS, Limits.CHUNK_PARTS);
        assertEquals(ConnectGuard.OPEN_INTERVAL_MS, Limits.OPEN_INTERVAL_MS);
        assertEquals(ConnectGuard.OPEN_AFTER_CLOSE_MS, Limits.OPEN_AFTER_CLOSE_MS);
        assertEquals(ConnectLeaderboard.MAX_ROWS, Limits.LEADERBOARD_ROWS);
        assertEquals(ConnectToast.MAX_BUTTONS, Limits.TOAST_BUTTONS);
        assertEquals(com.leafclient.connect.core.ConnectCondition.MAX_DEPTH, Limits.CONDITION_DEPTH);
        assertEquals(com.leafclient.connect.core.ConnectCondition.MAX_LEAVES, Limits.CONDITION_TESTS);
        assertTrue(Outbox.RATE_PER_SECOND < ConnectLimits.SERVER_RATE_PER_SECOND);
        assertTrue(Outbox.RATE_BURST < ConnectLimits.SERVER_RATE_BURST);
    }

    @Test
    void protocolAndDefaultsMatchTheClient() {
        assertEquals(ConnectProtocol.CHANNEL, LeafConnect.CHANNEL);
        assertEquals(ConnectProtocol.VERSION_MAX, LeafConnect.PROTOCOL);
        assertEquals(ConnectPanel.DEFAULT_PAD, Panel.DEFAULT_PAD);
        assertEquals(ConnectPanel.DEFAULT_RADIUS, Panel.DEFAULT_RADIUS);
        assertEquals(ConnectIcon.DEFAULT_SIZE, Icon.DEFAULT_SIZE);
        assertEquals(ConnectIcon.LEAF_DEFAULT_SIZE, Icon.LEAF_DEFAULT_SIZE);
        assertEquals(ConnectBadge.DEFAULT_BG, color(Badge.DEFAULT_BG));
        assertEquals(ConnectBadge.DEFAULT_TEXT, color(Badge.DEFAULT_COLOR));
        assertEquals(ConnectToast.DEFAULT_MS, Toast.DEFAULT_MS);
        assertEquals(ConnectWaypoint.DEFAULT_COLOR, color(Waypoint.DEFAULT_COLOR));
        assertEquals(ConnectPlace.DEFAULT_SCALE, Place.DEFAULT_SCALE);
        ConnectElement e = new ConnectElement();
        assertEquals(e.radius, Button.DEFAULT_RADIUS);
        assertEquals(e.yaw, (float) PlayerModel.DEFAULT_YAW);
        assertEquals(e.barHeight, Bar.DEFAULT_THICKNESS);
        assertEquals(e.headSize, Heads.DEFAULT_SIZE);
        assertEquals(e.maxChars, TextInput.DEFAULT_MAX_LENGTH);
    }

    @Test
    void everyEnumHasTheClientsValues() {
        same(ConnectPanel.Theme.class, Panel.Theme.class);
        same(ConnectPanel.Scope.class, Panel.Scope.class);
        same(ConnectElement.Shape.class, Shape.class);
        same(ConnectElement.Align.class, Align.class);
        same(ConnectElement.Justify.class, Justify.class);
        same(ConnectElement.Side.class, Container.Side.class);
        same(ConnectElement.Fit.class, Image.Fit.class);
        same(ConnectElement.IconPos.class, Button.IconPos.class);
        same(ConnectElement.CooldownShow.class, Button.CooldownShow.class);
        same(ConnectBadge.Pos.class, Badge.Pos.class);
        same(ConnectPlace.Face.class, Place.Face.class);
        same(ConnectPlace.Anchor.class, Place.Anchor.class);
        same(ConnectRay.Face.class, Place.Mount.class);
        same(ConnectToast.Kind.class, Toast.Kind.class);
        same(ConnectMenu.Look.class, Menu.Look.class);
        same(ConnectDialogStyle.Background.class, DialogStyle.Background.class);
        same(ConnectDialogStyle.TitleAlign.class, DialogStyle.TitleAlign.class);
        List<String> pages = new ArrayList<String>();
        for (Action.Page p : Action.Page.values()) pages.add(p.name().toLowerCase(Locale.ROOT));
        assertEquals(ConnectAction.PAGES, pages);
        List<String> places = new ArrayList<String>();
        for (ConnectPlace.In in : ConnectPlace.In.values()) places.add(in.name().toLowerCase(Locale.ROOT));
        assertEquals(ConnectPanel.PLACEMENTS, places);
    }

    @Test
    void theSdkProducesEveryMessageTypeTheClientKnows() {
        ClientSim sim = new ClientSim(Limits.LEGACY_MESSAGE_BYTES);
        Session s = sim.hello();
        s.send(Welcome.create("Server"));
        s.send(Menu.create("M").add(Menu.tab("a", "A")));
        s.send(Panel.create("vote", Place.front()).content(Ui.button("x", "X")));
        s.send(Patch.of("vote").z(1));
        s.send(Values.of("k", 1));
        s.send(Toast.create("t").title("T"));
        s.send(Waypoint.create("w", "W", 0, 64, 0));
        s.send(DialogStyle.create());
        s.send(EveryElementTest.everything());
        s.send(BatchingTest.huge("big", 40, 20, BatchingTest.tricky()));
        s.keyOpensMenu().world("hub").lockMods("r", "minimap").unlockMods("minimap").removeToast("t").removeWaypoints("w");
        s.openMenu();
        sim.flush();
        s.closeMenu();
        s.openPanel("vote");
        sim.advance(2500);
        sim.flush();
        s.closePanel("vote").removePanels("vote");
        s.removeMenu();
        s.reset();
        sim.advance(1000);
        sim.flush();
        sim.assertClean();
        Set<String> produced = new TreeSet<String>(sim.allTypes);
        Set<String> expected = new TreeSet<String>(ConnectProtocol.SERVER_TYPES);
        expected.removeAll(produced);
        assertEquals(Collections.emptySet(), expected, "message types the SDK cannot send");
    }

    @Test
    void theSdkCoversEveryElementKindActionAndPlacement() {
        ClientSim sim = new ClientSim();
        sim.hello().send(EveryElementTest.everything());
        sim.flush();
        sim.assertClean();
        Set<ConnectElement.Kind> kinds = EnumSet.noneOf(ConnectElement.Kind.class);
        Set<ConnectAction.Do> actions = EnumSet.noneOf(ConnectAction.Do.class);
        walk(sim.panel("all.elements").root, kinds, actions);
        assertEquals(EnumSet.allOf(ConnectElement.Kind.class), kinds);
        assertEquals(EnumSet.allOf(ConnectAction.Do.class), actions);
    }

    private static void walk(ConnectElement e, Set<ConnectElement.Kind> kinds, Set<ConnectAction.Do> actions) {
        kinds.add(e.kind);
        if (e.kind == ConnectElement.Kind.BUTTON) for (ConnectAction a : e.on) actions.add(a.kind);
        for (ConnectElement c : e.children) walk(c, kinds, actions);
    }

    private static <A extends Enum<A>, B extends Enum<B>> void same(Class<A> client, Class<B> sdk) {
        List<String> a = new ArrayList<String>();
        for (A v : client.getEnumConstants()) a.add(v.name());
        List<String> b = new ArrayList<String>();
        for (B v : sdk.getEnumConstants()) b.add(v.name());
        assertEquals(a, b, sdk.getName() + " differs from " + client.getName());
    }

    private static int color(String hex) {
        return ConnectColor.parse(new JsonPrimitive(hex), "color");
    }
}
