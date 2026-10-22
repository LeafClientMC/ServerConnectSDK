package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenGraceTest {

    final List<Rejection> rejected = new ArrayList<Rejection>();
    final List<Click> clicks = new ArrayList<Click>();

    ClientSim sim() {
        ClientSim c = new ClientSim();
        c.connect.onRejected(rejected::add);
        c.connect.onClick(clicks::add);
        return c;
    }

    @Test
    void anOldTokenCannotPressAButtonTheNewVersionDisabledRemovedOrPutOnCooldown() {
        int[] ran = new int[1];
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("a", "A").onClick(x -> ran[0]++), Ui.button("b", "B").onClick(x -> ran[0]++),
                Ui.button("d", "D").onClick(x -> ran[0]++)));
        c.flush();
        c.advance(300);
        c.press("chat", "p", "d");
        assertEquals(1, ran[0]);
        JsonObject oldA = c.clickJson("chat", "p", "a", null, false);
        JsonObject oldB = c.clickJson("chat", "p", "b", null, false);
        JsonObject oldD = c.clickJson("chat", "p", "d", null, false);
        s.send(Panel.create("p", Place.chat()).content(Ui.button("a", "A").enabled(false).onClick(x -> ran[0]++),
                Ui.button("d", "D").cooldown(60000).onClick(x -> ran[0]++)));
        c.advance(100);
        c.receive(oldA);
        c.receive(oldB);
        c.receive(oldD);
        assertEquals(1, ran[0], "an old token ran a handler the new version does not allow");
        assertEquals(Rejection.Reason.DISABLED, rejected.get(0).reason());
        assertEquals(Rejection.Reason.UNKNOWN_BUTTON, rejected.get(1).reason());
        assertEquals(Rejection.Reason.COOLDOWN, rejected.get(2).reason());
    }

    @Test
    void clicksFromTheLastFewVersionsWithinTheGraceAreAccepted() {
        ClientSim c = sim();
        Session s = c.hello();
        Panel p = Panel.create("p", Place.chat()).content(Ui.button("b", "B"));
        s.send(p);
        c.flush();
        c.advance(300);
        JsonObject first = c.clickJson("chat", "p", "b", null, false);
        s.send(p);
        c.advance(200);
        s.send(p);
        c.advance(200);
        c.receive(first);
        assertTrue(rejected.isEmpty(), String.valueOf(rejected));
        assertEquals(1, clicks.size());
    }

    @Test
    void oldVersionsAreForgottenAfterTheGrace() throws ReflectiveOperationException {
        ClientSim c = sim();
        Session s = c.hello();
        Panel p = Panel.create("p", Place.chat()).content(Ui.button("b", "B"));
        s.send(p);
        c.flush();
        s.send(p);
        c.advance(Session.TOKEN_GRACE_MS + 1);
        c.flush();
        c.press("chat", "p", "b");
        PanelRecord record = s.panelRecord("p");
        Field old = PanelRecord.class.getDeclaredField("oldTokens");
        old.setAccessible(true);
        assertTrue(((Collection<?>) old.get(record)).isEmpty());
    }

    @Test
    void aPanelThatExpiresDuringAHelloDoesNotStopOnReady() {
        for (int k = 0; k < 40; k++) {
            long[] clock = {1_000_000L};
            LeafConnect connect = new LeafConnect(ConnectLog.silent(), () -> clock[0]++);
            int[] ready = new int[1];
            connect.onReady(x -> ready[0]++);
            UUID id = UUID.randomUUID();
            Object player = new Object();
            ClientSim.Wire wire = new ClientSim.Wire(Limits.SERVER_MESSAGE_BYTES);
            byte[] hello = "{\"v\":1,\"t\":\"hello\",\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"chat\"]}".getBytes(StandardCharsets.UTF_8);
            connect.receive(id, "Steve", player, hello, wire);
            Session s = connect.session(id);
            s.send(Panel.create("p", Place.chat()).scope(Panel.Scope.CONNECTION).ttl(5000).content(Ui.button("b", "B").cooldown(600000)));
            PanelRecord record = s.panelRecord("p");
            s.setCooldownLeft("p", "b", 600000, 600000, clock[0]);
            clock[0] = record.expiresAt - k;
            connect.receive(id, "Steve", player, hello, wire);
            assertEquals(2, ready[0], "onReady did not run for k=" + k);
        }
    }

    @Test
    void cooldownLeftMustBeANumber() {
        assertThrows(InvalidUiException.class, () -> Patch.of("p").set("b", "cooldown_left", null));
        assertDoesNotThrow(() -> Patch.of("p").set("b", "cooldown_left", new JsonPrimitive(5000)));
    }

    @Test
    void cooldownToleranceIsCappedAtTwoSeconds() {
        ClientSim c = sim();
        Session s = c.hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("b", "B").cooldown(3600000)));
        c.flush();
        c.advance(300);
        c.press("chat", "p", "b");
        c.advance(3600000 - 3000);
        c.press("chat", "p", "b");
        assertEquals(1, clicks.size());
        assertEquals(Rejection.Reason.COOLDOWN, rejected.get(0).reason());
    }
}
