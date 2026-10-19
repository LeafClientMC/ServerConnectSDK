package com.leafclient.connect.sdk;

import com.leafclient.connect.core.ConnectBadge;
import com.leafclient.connect.core.ConnectCondition;
import com.leafclient.connect.core.ConnectPanel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReviewFixesTest {

    final List<Click> clicks = new ArrayList<Click>();
    final List<Rejection> rejected = new ArrayList<Rejection>();

    ClientSim sim() {
        ClientSim sim = new ClientSim();
        sim.connect.onRejected(rejected::add);
        return sim;
    }

    @Test
    void patchesKeepATtlPanelAliveLikeTheClient() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("timed", Place.chat()).ttl(2000).content(Ui.text("x").id("t"), Ui.button("go", "Go").onClick(clicks::add)));
        sim.flush();
        for (int i = 0; i < 4; i++) {
            sim.advance(1500);
            s.send(Patch.of("timed").text("t", "tick " + i));
            sim.flush();
        }
        sim.advance(1000);
        assertNotNull(sim.state.panel("timed"));
        sim.press("chat", "timed", "go");
        sim.assertClean();
        assertEquals(1, clicks.size(), String.valueOf(rejected));
    }

    @Test
    void patchedPlaceMovesTheClickSource() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("go", "Go").onClick(clicks::add)));
        s.send(Patch.of("p").place(Place.inventory(Place.Side.LEFT)));
        sim.flush();
        sim.assertClean();
        assertEquals("inventory", sim.panel("p").placement);
        sim.press("inventory", "p", "go");
        assertEquals(1, clicks.size(), String.valueOf(rejected));
    }

    @Test
    void patchesThatWouldDesyncValidationAreRefused() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("form", Place.menu()).content(Ui.choice("mode", "a", "b"), Ui.slider("bet", 0, 10), Ui.button("go", "Go")));
        assertThrows(InvalidUiException.class, () -> s.send(Patch.of("form").set("mode", "options", com.google.gson.JsonNull.INSTANCE)));
        assertThrows(InvalidUiException.class, () -> s.send(Patch.of("form").set("bet", "max", 100)));
        assertThrows(InvalidUiException.class, () -> s.send(Patch.of("form").set("go", "on", com.google.gson.JsonNull.INSTANCE)));
        assertThrows(InvalidUiException.class, () -> s.send(Patch.of("form").place(Place.chat())));
        assertThrows(InvalidUiException.class, () -> s.send(Patch.of("form").enabled("nope", false)));
    }

    @Test
    void everySendOfAMultiSendButtonIsAcceptedDespiteCooldown() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("go", "Go").cooldown(2000)
                .on(Action.send("first"), Action.send("second")).onClick(clicks::add)));
        sim.flush();
        sim.receive(sim.clickJson("chat", "p", "go", "first", false));
        sim.receive(sim.clickJson("chat", "p", "go", "second", false));
        assertEquals(2, clicks.size(), String.valueOf(rejected));
    }

    @Test
    void badgesBuiltFromStyledTextAreReadAsText() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("p", Place.chat()).content(
                Ui.button("a", "A").badge(Badge.of(Text.plain("5").color("red"))),
                Ui.button("b", "B").badge(Badge.of(Text.of("7").bold()))));
        sim.flush();
        sim.assertClean();
        ConnectBadge a = sim.element("p", "a").badge;
        assertEquals("5", a.text.plain());
        assertEquals(ConnectBadge.DEFAULT_BG, a.bg);
        assertEquals("7", sim.element("p", "b").badge.text.plain());
    }

    @Test
    void booleanValuesMatchBooleanConditions() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Values.of("vip", true));
        s.send(Panel.create("p", Place.chat()).when(Condition.eq("vip", true)).content(Ui.text("vip only")));
        sim.flush();
        sim.assertClean();
        ConnectPanel p = sim.panel("p");
        final long now = sim.clock[0];
        boolean shown = p.when.test(new ConnectCondition.Context() {
            @Override
            public String value(String key) {
                return sim.state.values().raw(key, now);
            }

            @Override
            public boolean timerDone(String key) {
                return false;
            }

            @Override
            public String holding() {
                return null;
            }

            @Override
            public boolean sneaking() {
                return false;
            }

            @Override
            public String dim() {
                return null;
            }

            @Override
            public String world() {
                return null;
            }

            @Override
            public double anchorDistance() {
                return Double.NaN;
            }
        });
        assertTrue(shown);
    }

    @Test
    void rawMessagesAreCheckedAndIsolatedInTheirOwnBatch() {
        ClientSim sim = sim();
        Session s = sim.hello();
        assertThrows(InvalidUiException.class, () -> s.sendRaw("{\"t\":\"panel.explode\"}"));
        s.send(Panel.create("before", Place.chat()).content(Ui.text("x")));
        s.sendRaw("{\"t\":\"key.set\",\"opens\":\"somewhere\"}");
        s.send(Panel.create("after", Place.chat()).content(Ui.text("x")));
        sim.flush();
        assertEquals(1, sim.errors.size());
        assertNotNull(sim.state.panel("before"));
        assertNotNull(sim.state.panel("after"));
    }

    @Test
    void aSecondHelloKeepsTheClientsOpenTiming() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Menu.create("M").add(Menu.tab("a", "A")));
        s.openMenu();
        sim.flush();
        sim.advance(500);
        sim.state.clearForJoin();
        sim.hello();
        s.send(Menu.create("M").add(Menu.tab("a", "A")));
        s.openMenu();
        for (int i = 0; i < 60; i++) {
            sim.flush();
            sim.advance(50);
        }
        sim.assertClean();
        assertEquals(0, sim.drops);
    }

    @Test
    void longIconIdsAreRejectedLikeTheClient() {
        StringBuilder id = new StringBuilder("minecraft:");
        while (id.length() < 129) id.append('a');
        assertThrows(InvalidUiException.class, () -> Icon.item(id.toString()));
    }
}
