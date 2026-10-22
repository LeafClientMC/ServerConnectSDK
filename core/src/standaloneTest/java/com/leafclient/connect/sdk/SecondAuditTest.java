package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecondAuditTest {

    static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final String HELLO = "{\"v\":1,\"t\":\"hello\",\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"chat\",\"menu\",\"block\"]}";

    final long[] clock = {1_000_000L};
    final TestWire wire = new TestWire(Limits.SERVER_MESSAGE_BYTES);
    final LeafConnect connect = new LeafConnect(ConnectLog.silent(), () -> clock[0]);
    final List<Rejection> rejected = new ArrayList<Rejection>();
    final List<Click> clicks = new ArrayList<Click>();
    long seq;

    void receive(String json) {
        connect.receive(ID, "Steve", wire, json.getBytes(StandardCharsets.UTF_8), wire);
    }

    Session hello() {
        connect.onRejected(rejected::add);
        connect.onClick(clicks::add);
        receive(HELLO);
        return connect.session(ID);
    }

    String token(String id) {
        connect.flush();
        String token = null;
        for (byte[] f : wire.frames) {
            JsonObject batch = TestWire.parse(new String(f, StandardCharsets.UTF_8));
            if (!batch.has("msgs")) continue;
            for (JsonElement m : batch.getAsJsonArray("msgs")) {
                JsonObject o = m.getAsJsonObject();
                if ("panel.set".equals(o.get("t").getAsString()) && id.equals(o.getAsJsonObject("panel").get("id").getAsString())) {
                    token = o.getAsJsonObject("panel").get("token").getAsString();
                }
            }
        }
        return token;
    }

    void click(String panel, String button, String token) {
        receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"" + panel + "\",\"button\":\"" + button + "\",\"token\":\"" + token
                + "\",\"seq\":" + (++seq) + "}");
    }

    @Test
    void messagesOverTheBudgetAreDroppedBeforeTheyAreParsed() {
        hello();
        StringBuilder sb = new StringBuilder("{\"v\":1,\"t\":\"closed\",\"what\":\"menu\",\"pad\":[");
        while (sb.length() < 16000) sb.append("[1,2,3,{\"a\":\"bbbbbbbb\"}],");
        sb.append("0]}");
        byte[] big = sb.toString().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 200; i++) connect.receive(ID, "Steve", wire, big, wire);
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1000; i++) connect.receive(ID, "Steve", wire, big, wire);
        long perMessage = (mx.getThreadAllocatedBytes(thread) - before) / 1000;
        assertTrue(perMessage < 2048, "allocated " + perMessage + " bytes per dropped message");
    }

    @Test
    void numberLiteralsOver32CharactersMakeTheMessageInvalid() {
        String digits32 = new String(new char[32]).replace('\0', '7');
        assertTrue(Json.parseObject("{\"seq\":" + digits32 + "}", 64) != null);
        assertEquals(null, Json.parseObject("{\"seq\":" + digits32 + "7}", 64));
    }

    @Test
    void cooldownLeftIsEnforcedAndZeroClearsAPress() {
        Session s = hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("b", "Claim").cooldown(60000).cooldownLeft(30000)));
        clock[0] += 100;
        click("p", "b", token("p"));
        assertTrue(clicks.isEmpty());
        assertEquals(Rejection.Reason.COOLDOWN, rejected.get(0).reason());
        clock[0] += 30000;
        click("p", "b", token("p"));
        assertEquals(1, clicks.size());
        s.send(Panel.create("p", Place.chat()).content(Ui.button("b", "Claim").cooldown(60000).cooldownLeft(0)));
        clock[0] += 100;
        click("p", "b", token("p"));
        assertEquals(2, clicks.size());
    }

    @Test
    void aBurstOf64ClicksIsAccepted() {
        Session s = hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("b", "Tap")));
        String token = token("p");
        clock[0] += 100;
        for (int i = 0; i < 64; i++) click("p", "b", token);
        assertEquals(64, clicks.size(), "rejected " + rejected.size());
    }

    @Test
    void blockPanelInANamedWorldIsClickableWhileTheClientIsInThatWorld() {
        Object[] world = {"uid-1"};
        LeafConnect worlds = new LeafConnect(ConnectLog.silent(), () -> clock[0]) {
            @Override
            protected Object worldOf(Session session) {
                return world[0];
            }
        };
        worlds.onClick(clicks::add);
        worlds.onRejected(rejected::add);
        worlds.receive(ID, "Steve", wire, HELLO.getBytes(StandardCharsets.UTF_8), wire);
        Session s = worlds.session(ID);
        s.world("lobby");
        s.send(Panel.create("sign", Place.block(0, 64, 0).world("arena")).content(Ui.button("b", "Hi")));
        worlds.flush();
        String token = token("sign");
        world[0] = "uid-2";
        s.world("arena");
        clock[0] += 100;
        worlds.receive(ID, "Steve", wire, ("{\"v\":1,\"t\":\"click\",\"src\":\"block\",\"panel\":\"sign\",\"button\":\"b\",\"token\":\"" + token
                + "\",\"seq\":1}").getBytes(StandardCharsets.UTF_8), wire);
        assertEquals(1, clicks.size(), String.valueOf(rejected));
    }
}
