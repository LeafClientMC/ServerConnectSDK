package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectOutbound;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCooldownsTest {

    final List<Rejection> rejected = new ArrayList<Rejection>();
    final List<Click> clicks = new ArrayList<Click>();

    ClientSim sim() {
        ClientSim c = new ClientSim();
        c.connect.onRejected(rejected::add);
        c.connect.onClick(clicks::add);
        return c;
    }

    boolean clientPress(ClientSim c, String panel, String button) {
        long now = c.clock[0];
        if (c.state.cooldowns().left(panel, button, now) > 0) return false;
        ConnectElement e = c.element(panel, button);
        if (e.cooldownMs > 0) c.state.cooldowns().start(panel, button, e.cooldownMs, now);
        c.press(c.panel(panel).placement, panel, button);
        return true;
    }

    @Test
    void aClickWithTheTokenTheClientHoldsIsAcceptedWhileAReplacementIsOnItsWay() {
        ClientSim c = sim();
        Session s = c.hello();
        Panel p = Panel.create("shop", Place.chat()).content(Ui.text("bal").id("bal"), Ui.button("buy", "Buy"));
        s.send(p);
        c.flush();
        c.advance(1000);
        s.send(p);
        c.press("chat", "shop", "buy");
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(1, clicks.size());
        c.advance(Session.TOKEN_GRACE_MS + 100);
        c.press("chat", "shop", "buy");
        assertEquals(Rejection.Reason.BAD_TOKEN, rejected.get(0).reason());
    }

    @Test
    void sendingThePanelAgainKeepsTheRunningCooldownOnBothSides() {
        ClientSim c = sim();
        Session s = c.hello();
        Panel p = Panel.create("daily", Place.chat()).content(Ui.button("claim", "Claim").cooldown(10000));
        s.send(p);
        c.flush();
        c.advance(300);
        assertTrue(clientPress(c, "daily", "claim"));
        c.advance(2000);
        s.send(p);
        c.flush();
        c.assertClean();
        c.advance(500);
        assertFalse(clientPress(c, "daily", "claim"), "the client must still show the cooldown");
        c.advance(8000);
        assertTrue(clientPress(c, "daily", "claim"));
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(2, clicks.size());
    }

    @Test
    void aServerSwitchKeepsTheCooldownOfAConnectionPanelOnBothSides() {
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("net", Place.chat()).scope(Panel.Scope.CONNECTION).content(Ui.button("claim", "Claim").cooldown(30000)));
        c.flush();
        c.advance(300);
        assertTrue(clientPress(c, "net", "claim"));
        c.advance(3000);
        c.state.clearForJoin();
        c.hello();
        c.flush();
        c.assertClean();
        c.advance(500);
        assertFalse(clientPress(c, "net", "claim"), "the client must get the time left back");
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
    }

    @Test
    void patchingCooldownLeftToZeroClearsTheServerCooldownToo() {
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("daily", Place.chat()).content(Ui.button("claim", "Claim").cooldown(60000)));
        c.flush();
        c.advance(300);
        assertTrue(clientPress(c, "daily", "claim"));
        c.advance(1000);
        s.send(Patch.of("daily").set("claim", "cooldown_left", 0));
        c.flush();
        c.assertClean();
        c.advance(300);
        assertTrue(clientPress(c, "daily", "claim"));
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(2, clicks.size());
    }

    @Test
    void cooldownToleranceGrowsWithTheCooldown() {
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("daily", Place.chat()).content(Ui.button("claim", "Claim").cooldown(10000)));
        c.flush();
        c.advance(300);
        c.press("chat", "daily", "claim");
        c.advance(9100);
        c.press("chat", "daily", "claim");
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(2, clicks.size());
    }

    @Test
    void backendSwitchesGiveOneHelloEachNotAFreshBurst() throws ReflectiveOperationException {
        ClientSim c = sim();
        int[] ready = {0};
        LeafConnect proxy = new LeafConnect(ConnectLog.silent(), () -> c.clock[0]);
        proxy.onReady(x -> ready[0]++);
        JsonObject hello = ConnectOutbound.hello("2.0.0", "1.21.11", "fabric", ClientSim.CAPS, new ArrayList<String>(), ConnectOutbound.defaultPrefs());
        byte[] h = ConnectOutbound.encode(hello);
        Method allow = LeafConnect.class.getDeclaredMethod("allowHello", UUID.class);
        allow.setAccessible(true);
        for (int sw = 0; sw < 10; sw++) {
            allow.invoke(proxy, c.id);
            for (int i = 0; i < 4; i++) proxy.receive(c.id, "Steve", c.player, h, c.wire);
            c.advance(100);
        }
        assertTrue(ready[0] <= 4 + 10, "onReady ran " + ready[0] + " times");
    }
}
