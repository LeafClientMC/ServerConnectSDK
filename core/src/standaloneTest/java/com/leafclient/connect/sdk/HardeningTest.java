package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HardeningTest {

    static final class Lines implements ConnectLog {
        final List<String> warnings = new ArrayList<String>();
        int debugCalls;

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void debug(String message) {
            debugCalls++;
        }

        @Override
        public boolean debugEnabled() {
            return false;
        }
    }

    static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final String HELLO = "{\"v\":1,\"t\":\"hello\",\"client\":{\"name\":\"Leaf\"},\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"toast\",\"chat\",\"values\",\"block\"]}";

    final long[] clock = {1_000_000L};
    final Lines lines = new Lines();
    final Object player = new Object();
    final TestWire wire = new TestWire(Limits.SERVER_MESSAGE_BYTES);
    final LeafConnect connect = new LeafConnect(lines, () -> clock[0]);

    void receive(String json) {
        connect.receive(ID, "Steve", player, json.getBytes(StandardCharsets.UTF_8), wire);
    }

    @Test
    void helloFloodRunsOnReadyAtMostFourTimesThenOnceEveryTwoSeconds() {
        int[] ready = new int[1];
        connect.onReady(s -> ready[0]++);
        for (int i = 0; i < 50; i++) receive(HELLO);
        assertEquals(4, ready[0]);
        clock[0] += 2000;
        for (int i = 0; i < 50; i++) receive(HELLO);
        assertEquals(5, ready[0]);
    }

    @Test
    void helloAgainDropsWhatWasStillQueuedAndForgetsThosePanels() {
        receive(HELLO);
        Session s = connect.session(ID);
        for (int i = 0; i < 20; i++) s.send(Panel.create("p" + (i % 4), Place.chat()).scope(Panel.Scope.CONNECTION).content(Ui.button("b", "Buy " + i)));
        assertFalse(s.idle());
        receive(HELLO);
        assertTrue(s.idle());
        assertEquals(null, s.panelRecord("p0"));
    }

    @Test
    void queueIsCappedAt512WaitingMessages() {
        receive(HELLO);
        Session s = connect.session(ID);
        int queued = 0;
        for (int i = 0; i < 700; i++) if (s.send(Panel.create("p", Place.chat()).content(Ui.text("v" + i)))) queued++;
        assertEquals(Outbox.MAX_WAITING_MESSAGES, queued);
        assertTrue(lines.warnings.size() >= 1 && lines.warnings.size() <= 5, "throttled warnings: " + lines.warnings.size());
        for (int i = 0; i < 1000 && !s.idle(); i++) {
            clock[0] += 50;
            connect.flush();
        }
        assertTrue(s.send(Panel.create("p", Place.chat()).content(Ui.text("after"))));
    }

    @Test
    void clientErrorsAreCleanedCutAndThrottled() {
        List<ClientError> seen = new ArrayList<ClientError>();
        connect.onError(seen::add);
        receive(HELLO);
        StringBuilder detail = new StringBuilder("line one\\n[Server thread/INFO]: forged line\\u2028");
        for (int i = 0; i < 3000; i++) detail.append('x');
        for (int i = 0; i < 40; i++) receive("{\"v\":1,\"t\":\"error\",\"code\":\"bad\\r\\ncode\",\"detail\":\"" + detail + "\"}");
        assertEquals(40, seen.size());
        String d = seen.get(0).detail();
        assertFalse(d.contains("\n") || d.contains(" "));
        assertTrue(d.length() <= 515);
        assertFalse(seen.get(0).code().contains("\r"));
        assertEquals(5, lines.warnings.size());
        for (String w : lines.warnings) assertFalse(w.contains("\n"));
    }

    @Test
    void unsupportedProtocolWarningsAreThrottled() {
        for (int i = 0; i < 40; i++) {
            connect.receive(UUID.randomUUID(), "P" + i, player, "{\"v\":1,\"t\":\"hello\",\"proto\":{\"min\":7,\"max\":9}}".getBytes(StandardCharsets.UTF_8), wire);
        }
        assertEquals(5, lines.warnings.size());
        assertFalse(connect.isLeaf(ID));
    }

    @Test
    void hugeAndBrokenNumbersNeverThrowOutOfReceive() {
        receive("{\"v\":1,\"t\":\"hello\",\"proto\":{\"min\":1e99999,\"max\":-1e99999},\"caps\":[\"toast\"]}");
        Session s = connect.session(ID);
        assertNotNull(s);
        assertEquals(1, s.client().protocolMin());
        List<Rejection> rejected = new ArrayList<Rejection>();
        connect.onRejected(rejected::add);
        receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"p\",\"button\":\"b\",\"seq\":1e99999}");
        receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"p\",\"button\":\"b\",\"seq\":1.5}");
        receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"p\",\"button\":\"b\",\"seq\":1e9999999999}");
        assertEquals(3, rejected.size());
        for (Rejection r : rejected) assertEquals(Rejection.Reason.MALFORMED, r.reason());
    }

    @Test
    void deeplyNestedMessagesAreIgnoredBeforeParsing() {
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 8000; i++) deep.append('[');
        for (int i = 0; i < 8000; i++) deep.append(']');
        receive(deep.toString());
        assertFalse(Json.strictWithin(deep.toString(), 64));
        assertTrue(Json.strictWithin("{\"a\":\"[[[[[[\",\"b\":[1,{\"c\":2}]}", 3));
        assertTrue(lines.warnings.isEmpty());
    }

    @Test
    void exceptionsWhileHandlingAMessageAreLoggedThrottledNotThrown() {
        LeafConnect broken = new LeafConnect(lines, () -> clock[0]) {
            @Override
            protected boolean inReach(Session session, WorldAnchor anchor) {
                throw new IllegalStateException("boom\nforged");
            }
        };
        broken.receive(ID, "Steve", player, HELLO.getBytes(StandardCharsets.UTF_8), wire);
        broken.session(ID).send(Panel.create("w", Place.block(0, 64, 0)).content(Ui.button("b", "Hi")));
        broken.flush();
        JsonObject batch = TestWire.parse(wire.text(0));
        String token = batch.getAsJsonArray("msgs").get(0).getAsJsonObject().getAsJsonObject("panel").get("token").getAsString();
        for (int i = 1; i <= 20; i++) {
            broken.receive(ID, "Steve", player, ("{\"v\":1,\"t\":\"click\",\"src\":\"block\",\"panel\":\"w\",\"button\":\"b\",\"token\":\""
                    + token + "\",\"seq\":" + i + "}").getBytes(StandardCharsets.UTF_8), wire);
        }
        assertEquals(5, lines.warnings.size());
        assertTrue(lines.warnings.get(0).contains("boom"));
        for (String w : lines.warnings) assertFalse(w.contains("\n"));
    }

    @Test
    void utf8LengthMatchesTheEncoder() {
        String[] samples = {"", "abc", "éè", "€", "🍁 leaf", "lone \ud83c high", "lone \udf41 low", "\ud83c"};
        for (String s : samples) assertEquals(s.getBytes(StandardCharsets.UTF_8).length, Json.utf8Length(s), s);
    }

    @Test
    void manyQueuedValueUpdatesStayOneMessage() {
        receive(HELLO);
        Session s = connect.session(ID);
        for (int i = 0; i < 2000; i++) s.setValue("k" + (i % 200), i);
        s.send(Values.create().delete("k0"));
        connect.flush();
        assertEquals(1, wire.frames.size());
        JsonObject batch = TestWire.parse(wire.text(0));
        JsonObject values = batch.getAsJsonArray("msgs").get(0).getAsJsonObject();
        assertEquals(199, values.getAsJsonObject("set").entrySet().size());
        assertEquals(1999, values.getAsJsonObject("set").get("k199").getAsInt());
        assertEquals("k0", values.getAsJsonArray("del").get(0).getAsString());
    }

    @Test
    void idleSessionsDrainWithoutAllocating() {
        receive(HELLO);
        Session s = connect.session(ID);
        connect.flush();
        assertSame(Collections.emptyList(), s.drain(clock[0]));
        clock[0] += 5000;
        assertSame(Collections.emptyList(), s.drain(clock[0]));
    }

    @Test
    void debugLinesAreNotBuiltWhenDebugIsOff() {
        receive(HELLO);
        receive("{\"v\":1,\"t\":\"nonsense\"}");
        receive("not json");
        assertEquals(0, lines.debugCalls);
    }
}
