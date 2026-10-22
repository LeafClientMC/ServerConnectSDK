package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectOutbound;
import com.leafclient.connect.core.ConnectPanel;
import com.leafclient.connect.core.ConnectToast;
import com.leafclient.connect.core.ConnectState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickTest {

    final List<Click> clicks = new ArrayList<Click>();
    final List<Rejection> rejected = new ArrayList<Rejection>();

    ClientSim sim() {
        ClientSim sim = new ClientSim();
        sim.connect.onRejected(rejected::add);
        return sim;
    }

    Rejection.Reason lastReason() {
        return rejected.get(rejected.size() - 1).reason();
    }

    @Test
    void buttonCallbackReceivesAValidatedClick() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("shop", Place.menu("shop")).content(
                Ui.button("buy", "Buy").on(Action.send("boots").confirm("Buy?")).onClick(clicks::add),
                Ui.button("other", "Other")));
        sim.flush();
        sim.assertClean();
        sim.receive(sim.clickJson("menu", "shop", "buy", "boots", true));
        assertEquals(1, clicks.size());
        Click c = clicks.get(0);
        assertEquals("shop", c.panel());
        assertEquals("buy", c.button());
        assertEquals("boots", c.data());
        assertTrue(c.confirmed());
        assertEquals("menu", c.source());
        assertTrue(c.is("shop", "buy"));
        assertTrue(c.inputs().isEmpty());
        assertEquals(sim.player, c.player());
        assertTrue(rejected.isEmpty());
    }

    @Test
    void globalListenerSeesClicksWithoutAHandler() {
        ClientSim sim = sim();
        sim.connect.onClick(clicks::add);
        Session s = sim.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("x", "X")));
        sim.flush();
        sim.press("chat", "p", "x");
        assertEquals(1, clicks.size());
    }

    @Test
    void replayedClickIsRejected() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("x", "X").onClick(clicks::add)));
        sim.flush();
        JsonObject click = sim.clickJson("chat", "p", "x", null, false);
        sim.receive(click);
        sim.receive(click);
        assertEquals(1, clicks.size());
        assertEquals(Rejection.Reason.REPLAY, lastReason());
    }

    @Test
    void forgedAndStaleTokensAreRejected() {
        ClientSim sim = sim();
        Session s = sim.hello();
        Panel panel = Panel.create("p", Place.chat()).content(Ui.button("x", "X").onClick(clicks::add));
        s.send(panel);
        sim.flush();
        JsonObject stale = sim.clickJson("chat", "p", "x", null, false);
        s.send(panel);
        sim.flush();
        sim.advance(Session.TOKEN_GRACE_MS + 1);
        sim.receive(stale);
        assertEquals(Rejection.Reason.BAD_TOKEN, lastReason());
        JsonObject forged = sim.clickJson("chat", "p", "x", null, false);
        forged.addProperty("token", "guessed");
        sim.receive(forged);
        assertEquals(Rejection.Reason.BAD_TOKEN, lastReason());
        sim.press("chat", "p", "x");
        assertEquals(1, clicks.size());
    }

    @Test
    void disabledUnknownWrongSourceAndUnknownPanelAreRejected() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("p", Place.block(0, 64, 0)).content(Ui.button("off", "Off").enabled(false).onClick(clicks::add),
                Ui.button("on", "On").onClick(clicks::add)));
        sim.flush();
        sim.press("block", "p", "off");
        assertEquals(Rejection.Reason.DISABLED, lastReason());
        sim.press("block", "p", "nope");
        assertEquals(Rejection.Reason.UNKNOWN_BUTTON, lastReason());
        sim.press("menu", "p", "on");
        assertEquals(Rejection.Reason.WRONG_SOURCE, lastReason());
        sim.press("block", "ghost", "on");
        assertEquals(Rejection.Reason.UNKNOWN_PANEL, lastReason());
        sim.inReach = false;
        sim.press("block", "p", "on");
        assertEquals(Rejection.Reason.OUT_OF_REACH, lastReason());
        sim.inReach = true;
        sim.press("block", "p", "on");
        assertEquals(1, clicks.size());
        assertNotNull(clicks.get(0).anchor());
        assertEquals(0.5, clicks.get(0).anchor().x());
        assertEquals(65.5, clicks.get(0).anchor().y());
        s.send(Patch.of("p").enabled("off", true));
        sim.flush();
        sim.press("block", "p", "off");
        assertEquals(2, clicks.size());
    }

    @Test
    void cooldownIsEnforcedOnTheServer() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("ready", "Ready").cooldown(2000).onClick(clicks::add)));
        sim.flush();
        sim.press("chat", "p", "ready");
        sim.advance(500);
        sim.press("chat", "p", "ready");
        assertEquals(Rejection.Reason.COOLDOWN, lastReason());
        sim.advance(1800);
        sim.press("chat", "p", "ready");
        assertEquals(2, clicks.size());
    }

    @Test
    void formValuesArriveTyped() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("form", Place.menu("signup")).content(
                Ui.input("name").label("Team name").maxLength(16),
                Ui.toggle("public").value(true),
                Ui.checkbox("rules"),
                Ui.choice("mode", "solo", "duo", "squad"),
                Ui.slider("bet", 0, 100).step(5).value(20),
                Ui.button("submit", "Sign up").onClick(clicks::add)));
        sim.flush();
        sim.assertClean();
        ConnectPanel p = sim.panel("form");
        ConnectElement name = p.find("name");
        sim.state.forms().setText("form", name, "Oak Team");
        sim.state.forms().toggle("form", p.find("rules"));
        sim.state.forms().select("form", p.find("mode"), 2);
        sim.state.forms().setFraction("form", p.find("bet"), 0.62);
        sim.press("menu", "form", "submit");
        assertEquals(1, clicks.size(), String.valueOf(rejected));
        FormValues v = clicks.get(0).inputs();
        assertEquals("Oak Team", v.text("name"));
        assertTrue(v.isOn("public"));
        assertTrue(v.isOn("rules"));
        assertEquals("squad", v.choice("mode"));
        assertEquals(60.0, v.number("bet"));
        assertEquals(60, v.integer("bet"));
        assertEquals(5, v.ids().size());
    }

    @Test
    void tamperedFormValuesAreRejected() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("form", Place.menu()).content(Ui.choice("mode", "solo", "duo"), Ui.slider("bet", 0, 10),
                Ui.input("name").maxLength(4), Ui.button("go", "Go").onClick(clicks::add)));
        sim.flush();
        String[][] bad = {{"mode", "\"admin\""}, {"bet", "1000"}, {"name", "\"toolong\""}, {"name", "true"}, {"extra", "1"}};
        for (String[] b : bad) {
            JsonObject click = sim.clickJson("menu", "form", "go", null, false);
            click.getAsJsonObject("inputs").add(b[0], BatchingTest.parse("{\"x\":" + b[1] + "}").get("x"));
            sim.receive(click);
            assertEquals(Rejection.Reason.BAD_INPUTS, lastReason(), b[0] + "=" + b[1]);
        }
        assertTrue(clicks.isEmpty());
        sim.press("menu", "form", "go");
        assertEquals(1, clicks.size());
        assertEquals("solo", clicks.get(0).inputs().choice("mode"));
    }

    @Test
    void toastAndWaypointClicksCarryTheirOwnTokens() {
        ClientSim sim = sim();
        Session s = sim.hello();
        s.send(Panel.create("invite", Place.chat()).content(Ui.button("x", "X")));
        s.send(Toast.create("invite").title("Party invite").button("accept", "Accept", clicks::add).button("deny", "Deny", clicks::add));
        s.send(Waypoint.create("arena", "Arena", 1, 2, 3).onClick(clicks::add));
        sim.flush();
        sim.assertClean();
        ConnectToast toast = null;
        for (ConnectState.Effect e : sim.effects) if (e.kind == ConnectState.EffectKind.TOAST) toast = (ConnectToast) e.payload;
        assertNotNull(toast);
        sim.receive(sim.clicks.click("toast", "invite", "deny", toast.token, null, false));
        sim.receive(sim.clicks.click("waypoint", "arena", "label", sim.state.waypoint("arena").token, null, true));
        sim.receive(sim.clicks.click("toast", "invite", "accept", "wrong", null, false));
        assertEquals(2, clicks.size());
        assertEquals("deny", clicks.get(0).button());
        assertEquals("toast", clicks.get(0).source());
        assertEquals("waypoint", clicks.get(1).source());
        assertEquals(Rejection.Reason.BAD_TOKEN, lastReason());
    }

    @Test
    void closedPrefsAndErrorsReachListeners() {
        ClientSim sim = sim();
        List<Closed> closed = new ArrayList<Closed>();
        List<ClientError> errors = new ArrayList<ClientError>();
        List<Session> prefs = new ArrayList<Session>();
        sim.connect.onClosed(closed::add).onError(errors::add).onPrefs(prefs::add);
        Session s = sim.hello();
        sim.receive(BatchingTest.closed("panel", "vote", "walk"));
        assertEquals(Closed.What.PANEL, closed.get(0).what());
        assertEquals(Closed.Why.WALK, closed.get(0).why());
        assertEquals("vote", closed.get(0).id());
        JsonObject p = ConnectOutbound.envelope("prefs");
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : ConnectOutbound.defaultPrefs().entrySet()) p.add(e.getKey(), e.getValue());
        p.addProperty("toasts", false);
        sim.receive(p);
        assertEquals(1, prefs.size());
        assertFalse(s.prefs().toasts());
        assertTrue(s.prefs().menu());

        s.send(Panel.create("ok", Place.chat()).content(Ui.button("x", "X")));
        s.sendRaw("{\"t\":\"key.set\",\"opens\":\"somewhere\"}");
        sim.flush();
        assertEquals(1, sim.errors.size());
        sim.replyErrors();
        assertEquals(1, errors.size());
        ClientError err = errors.get(0);
        assertEquals("invalid", err.code());
        assertEquals("b2", err.ref());
        assertEquals("key.set (raw)", err.sent());
        assertTrue(err.detail().contains("opens"), err.detail());
    }

    @Test
    void nonLeafPlayersHaveNoSessionAndGetNothing() {
        ClientSim sim = sim();
        UUID other = UUID.randomUUID();
        assertNull(sim.connect.session(other));
        assertFalse(sim.connect.isLeaf(other));
        sim.receive(sim.clicks.click("menu", "p", "x", "t", null, false));
        sim.connect.flush();
        assertTrue(sim.wire.frames.isEmpty());
        assertNull(sim.connect.session(sim.id));
        Session s = sim.hello();
        assertTrue(sim.connect.isLeaf(sim.id));
        assertTrue(s.has("forms"));
        assertEquals("2.0.0", s.client().version());
        assertEquals("1.21.11", s.client().minecraftVersion());
        assertEquals("fabric", s.client().loader());
        assertEquals(Arrays.asList("freelook", "minimap"), s.client().lockableMods());
        assertTrue(s.prefs().enabled());
    }

    @Test
    void missingCapabilityIsSkippedAndUnsupportedProtocolGetsNoSession() {
        ClientSim sim = sim();
        Session s = sim.hello(Arrays.asList("menu", "batch"));
        assertFalse(s.send(DialogStyle.create().accent("#FFFFFF")));
        assertFalse(s.send(Toast.create("t").title("x")));
        assertTrue(s.send(Panel.create("m", Place.menu()).content(Ui.text("x"))));
        assertFalse(s.send(Panel.create("w", Place.block(0, 0, 0)).content(Ui.text("x"))));
        ClientSim old = sim();
        JsonObject hello = ConnectOutbound.hello("9.0", "1.21", "fabric", ClientSim.CAPS, Arrays.<String>asList(), ConnectOutbound.defaultPrefs());
        hello.getAsJsonObject("proto").addProperty("min", 2);
        hello.getAsJsonObject("proto").addProperty("max", 3);
        old.receive(hello);
        assertNull(old.connect.session(old.id));
    }

    @Test
    void secondHelloKeepsConnectionPanelsAndSequence() {
        ClientSim sim = sim();
        List<Session> ready = new ArrayList<Session>();
        sim.connect.onReady(ready::add);
        Session s = sim.hello();
        s.send(Panel.create("net.lobby", Place.chat()).scope(Panel.Scope.CONNECTION).content(Ui.button("lobby", "Lobby").onClick(clicks::add)));
        s.send(Panel.create("game", Place.chat()).content(Ui.button("x", "X").onClick(clicks::add)));
        sim.flush();
        sim.press("chat", "net.lobby", "lobby");
        sim.state.clearForJoin();
        Session again = sim.hello();
        assertEquals(s, again);
        assertEquals(2, ready.size());
        sim.press("chat", "net.lobby", "lobby");
        assertEquals(2, clicks.size());
        assertNotNull(sim.state.panel("net.lobby"));
        assertNull(sim.state.panel("game"));
    }

    @Test
    void clickHandlerWithoutSendActionFailsAtBuildTime() {
        ClientSim sim = sim();
        Session s = sim.hello();
        InvalidUiException e = org.junit.jupiter.api.Assertions.assertThrows(InvalidUiException.class, () -> s.send(
                Panel.create("p", Place.chat()).content(Ui.button("x", "X").on(Action.command("/spawn")).onClick(clicks::add))));
        assertTrue(e.getMessage().contains("Action.send()"), e.getMessage());
    }
}
