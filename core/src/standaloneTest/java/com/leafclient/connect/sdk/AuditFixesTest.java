package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditFixesTest {

    static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final String HELLO = "{\"v\":1,\"t\":\"hello\",\"client\":{\"name\":\"Leaf\"},\"proto\":{\"min\":1,\"max\":1},"
            + "\"caps\":[\"toast\",\"chat\",\"values\",\"block\",\"waypoint\",\"menu\"]}";
    static final String CLOSED_MENU = "{\"v\":1,\"t\":\"closed\",\"what\":\"menu\",\"why\":\"player\"}";

    static final class Lines implements ConnectLog {
        final List<String> warnings = new ArrayList<String>();

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void debug(String message) {
        }
    }

    final long[] clock = {1_000_000L};
    final Lines lines = new Lines();
    final Object player = new Object();
    final TestWire wire = new TestWire(Limits.SERVER_MESSAGE_BYTES);
    final LeafConnect connect = new LeafConnect(lines, () -> clock[0]);
    final List<Rejection> rejected = new ArrayList<Rejection>();
    final List<Click> clicks = new ArrayList<Click>();
    final List<Closed> closed = new ArrayList<Closed>();
    long seq;

    void receive(LeafConnect c, String json) {
        c.receive(ID, "Steve", player, json.getBytes(StandardCharsets.UTF_8), wire);
    }

    void receive(String json) {
        receive(connect, json);
    }

    Session hello(LeafConnect c) {
        c.onRejected(rejected::add);
        c.onClick(clicks::add);
        c.onClosed(closed::add);
        receive(c, HELLO);
        return c.session(ID);
    }

    Session hello() {
        return hello(connect);
    }

    String token(LeafConnect c, String id) {
        c.flush();
        String token = null;
        for (byte[] f : wire.frames) {
            JsonObject batch = TestWire.parse(new String(f, StandardCharsets.UTF_8));
            if (!batch.has("msgs")) continue;
            for (JsonElement m : batch.getAsJsonArray("msgs")) {
                JsonObject o = m.getAsJsonObject();
                JsonObject body = "panel.set".equals(o.get("t").getAsString()) ? o.getAsJsonObject("panel") : o;
                if (id.equals(Json.getString(body, "id")) && body.has("token")) token = body.get("token").getAsString();
            }
        }
        return token;
    }

    String token(String id) {
        return token(connect, id);
    }

    void click(LeafConnect c, String src, String panel, String button, String token, String extra) {
        clock[0] += 100;
        receive(c, "{\"v\":1,\"t\":\"click\",\"src\":\"" + src + "\",\"panel\":\"" + panel + "\",\"button\":\"" + button + "\",\"token\":\""
                + token + "\",\"seq\":" + (++seq) + extra + "}");
    }

    void click(String src, String panel, String button, String token, String extra) {
        click(connect, src, panel, button, token, extra);
    }

    @Test
    void oversizedValueMergesNeitherWedgeTheOutboxNorStarveOtherSessions() {
        TestWire legacy = new TestWire(Limits.LEGACY_MESSAGE_BYTES);
        UUID other = UUID.fromString("00000000-0000-0000-0000-00000000000a");
        connect.receive(other, "Alex", new Object(), HELLO.getBytes(StandardCharsets.UTF_8), legacy);
        receive(HELLO);
        Session alex = connect.session(other);
        Session steve = connect.session(ID);
        for (int m = 0; m < 50; m++) {
            String[] keys = new String[256];
            for (int k = 0; k < keys.length; k++) keys[k] = String.format("m%02d_k%03d_padding_padding_padding_padding_pad", m, k);
            alex.send(Values.create().delete(keys));
        }
        steve.setValue("x", 1);
        for (int i = 0; i < 600 && !(alex.idle() && steve.idle()); i++) {
            clock[0] += 50;
            assertDoesNotThrow(connect::flush);
        }
        assertTrue(alex.idle() && steve.idle());
        assertEquals(1, wire.frames.size());
        assertFalse(legacy.frames.isEmpty());
        for (byte[] f : legacy.frames) assertTrue(f.length <= Limits.LEGACY_MESSAGE_BYTES);
    }

    @Test
    void clickDataMustBeDataTheButtonSends() {
        Session s = hello();
        int[] bought = {0};
        s.send(Panel.create("shop", Place.chat()).content(Ui.button("buy", "Buy").on(Action.send("apple")).onClick(c -> bought[0]++)));
        String token = token("shop");
        click("chat", "shop", "buy", token, ",\"data\":\"netherite_block x64\\n[Server thread/INFO]: forged\"");
        assertEquals(0, bought[0]);
        assertEquals("BAD_DATA", rejected.get(0).reason().name());
        click("chat", "shop", "buy", token, ",\"data\":\"apple\"");
        assertEquals(1, bought[0]);
    }

    @Test
    void buttonsToastsAndWaypointsThatSendNothingAcceptNoClicks() {
        Session s = hello();
        s.send(Panel.create("links", Place.chat()).content(Ui.button("site", "Site").on(Action.url("https://leafclient.com"))));
        s.send(Toast.create("t").title("Hi").button("open", "Open", Action.url("https://leafclient.com")));
        s.send(Waypoint.create("w", "Base", 0, 64, 0).on(Action.url("https://leafclient.com")));
        String panelToken = token("links");
        String toastToken = token("t");
        String waypointToken = token("w");
        click("chat", "links", "site", panelToken, "");
        click("toast", "t", "open", toastToken, "");
        click("waypoint", "w", "label", waypointToken == null ? "none" : waypointToken, "");
        assertTrue(clicks.isEmpty(), "accepted " + clicks);
        assertEquals(3, rejected.size());
    }

    @Test
    void nonHelloMessagesShareOnePerSessionBudget() {
        hello();
        for (int i = 0; i < 1000; i++) receive(CLOSED_MENU);
        assertTrue(closed.size() <= 80, "closed listeners ran " + closed.size() + " times");
        clock[0] += 1000;
        int before = closed.size();
        receive(CLOSED_MENU);
        assertEquals(before + 1, closed.size());
    }

    @Test
    void rejectedClicksAreBoundedByTheSameBudget() {
        hello();
        for (int i = 1; i <= 1000; i++) receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"nope\",\"button\":\"b\",\"seq\":" + i + "}");
        assertTrue(rejected.size() <= 80, "rejection listeners ran " + rejected.size() + " times");
    }

    @Test
    void failingListenersAndHandlersAreLoggedThrottled() {
        hello();
        connect.onClosed(c -> {
            throw new IllegalStateException("plugin bug");
        });
        for (int i = 0; i < 60; i++) {
            clock[0] += 10;
            receive(CLOSED_MENU);
        }
        assertEquals(60, closed.size());
        assertTrue(lines.warnings.size() <= 5, "logged " + lines.warnings.size() + " lines");
    }

    @Test
    void cooldownSurvivesSendingThePanelAgain() {
        Session s = hello();
        s.send(Panel.create("p", Place.chat()).content(Ui.button("b", "Claim").cooldown(60000)));
        click("chat", "p", "b", token("p"), "");
        assertEquals(1, clicks.size());
        clock[0] += 2000;
        s.send(Panel.create("p", Place.chat()).content(Ui.button("b", "Claim").cooldown(60000)));
        click("chat", "p", "b", token("p"), "");
        assertEquals(1, clicks.size());
        assertEquals(Rejection.Reason.COOLDOWN, rejected.get(0).reason());
    }

    @Test
    void clientTextIsCleanedWhereThePublicApiExposesIt() {
        receive("{\"v\":1,\"t\":\"hello\",\"client\":{\"name\":\"Leaf\\n[Server thread/INFO]: forged\\u202e\",\"version\":\""
                + new String(new char[500]).replace('\0', 'x') + "\"},\"proto\":{\"min\":1,\"max\":1},\"caps\":[\"chat\",\"evil\\ncap\"]}");
        Session s = connect.session(ID);
        ClientInfo info = s.client();
        assertFalse(info.name().contains("\n") || info.name().contains("\u202e"), info.name());
        assertTrue(info.version().length() <= Limits.ID_CHARS + 3);
        for (String cap : info.caps()) assertFalse(cap.contains("\n"));
        assertFalse(s.toString().contains("\n"));
        connect.onClosed(closed::add);
        connect.onRejected(rejected::add);
        clock[0] += 1000;
        receive("{\"v\":1,\"t\":\"closed\",\"what\":\"panel\",\"id\":\"p\\n[Server] forged\\u2066\"}");
        assertFalse(closed.get(0).id().contains("\n") || closed.get(0).id().contains("\u2066"));
        assertFalse(closed.get(0).toString().contains("\n"));
        receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"x\\n[Server] forged\\u202e\",\"button\":\"b\\r\",\"seq\":1}");
        Rejection r = rejected.get(0);
        assertFalse(r.panel().contains("\n") || r.panel().contains("\u202e") || r.button().contains("\r"));
        assertFalse(r.toString().contains("\n") || r.detail().contains("\n"));
        assertEquals("a b", Json.clean("a\u202eb", 16));
    }

    @Test
    void blockPanelClicksFromAnotherWorldAreOutOfReach() {
        Object[] world = {"overworld"};
        LeafConnect worlds = new LeafConnect(lines, () -> clock[0]) {
            @Override
            protected Object worldOf(Session session) {
                return world[0];
            }
        };
        Session s = hello(worlds);
        s.send(Panel.create("sign", Place.block(0, 64, 0)).content(Ui.button("b", "Hi")));
        String token = token(worlds, "sign");
        world[0] = "nether";
        click(worlds, "block", "sign", "b", token, "");
        assertTrue(clicks.isEmpty());
        assertEquals(Rejection.Reason.OUT_OF_REACH, rejected.get(0).reason());
        world[0] = "overworld";
        click(worlds, "block", "sign", "b", token, "");
        assertEquals(1, clicks.size());
    }

    @Test
    void formInputsRejectControlCharactersSectionSignsAndOffStepSliderValues() {
        Session s = hello();
        s.send(Panel.create("form", Place.menu("t")).content(Ui.input("team"), Ui.slider("vol", 0, 10).step(2), Ui.button("go", "Go")));
        String token = token("form");
        click("menu", "form", "go", token, ",\"inputs\":{\"team\":\"a\\n[Server thread/INFO]: forged\"}");
        click("menu", "form", "go", token, ",\"inputs\":{\"team\":\"\\u00a7kred\"}");
        click("menu", "form", "go", token, ",\"inputs\":{\"vol\":3}");
        assertTrue(clicks.isEmpty());
        assertEquals(3, rejected.size());
        for (Rejection r : rejected) assertEquals(Rejection.Reason.BAD_INPUTS, r.reason());
        click("menu", "form", "go", token, ",\"inputs\":{\"team\":\"Leafers\",\"vol\":4}");
        click("menu", "form", "go", token, ",\"inputs\":{\"vol\":10}");
        assertEquals(2, clicks.size());
    }

    @Test
    void lenientJsonCannotSlipPastTheDepthCheck() {
        hello();
        receive("{'v':1,'t':'closed','what':'menu'}");
        receive("{/* c */\"v\":1,\"t\":\"closed\",\"what\":\"menu\"}");
        receive("{\"v\":1,\"t\":\"closed\",\"what\":\"menu\"} trailing");
        StringBuilder deep = new StringBuilder("{'x\"':");
        for (int i = 0; i < 8000; i++) deep.append('[');
        for (int i = 0; i < 8000; i++) deep.append(']');
        deep.append('}');
        assertNull(Json.parseObject(deep.toString(), 64));
        assertDoesNotThrow(() -> Json.parseObject("{\"n\":1e99999}", 64).get("n").getAsInt());
        assertDoesNotThrow(() -> receive(deep.toString()));
        assertTrue(closed.isEmpty());
        receive(CLOSED_MENU);
        assertEquals(1, closed.size());
    }

    @Test
    void patchChecksTheTypeOfEnabledAndTheLengthOfLabels() {
        String tooLong = new String(new char[Limits.TEXT_CHARS + 1]).replace('\0', 'x');
        assertThrows(InvalidUiException.class, () -> Patch.of("p").set("b", "enabled", "yes"));
        assertThrows(InvalidUiException.class, () -> Patch.of("p").set("b", "label", tooLong));
        assertDoesNotThrow(() -> Patch.of("p").set("b", "enabled", false).set("b", "label", "Hi").set("b", "enabled", null));
    }

    @Test
    void aFailedSendKeepsTheRestOfTheTicksFrames() {
        Session s = hello();
        connect.flush();
        wire.frames.clear();
        s.sendRaw("{\"t\":\"world.set\",\"id\":\"w1\"}");
        s.sendRaw("{\"t\":\"world.set\",\"id\":\"w2\"}");
        s.sendRaw("{\"t\":\"world.set\",\"id\":\"w3\"}");
        wire.failNext = new IllegalStateException("network");
        connect.flush();
        assertTrue(wire.frames.isEmpty());
        connect.flush();
        assertEquals(2, wire.frames.size());
        assertTrue(wire.text(0).contains("w2") && wire.text(1).contains("w3"));
        assertTrue(s.idle());
    }

    @Test
    void nullListenersAreRefusedWhenRegistered() {
        assertThrows(NullPointerException.class, () -> connect.onReady(null));
        assertThrows(NullPointerException.class, () -> connect.onClick(null));
        assertThrows(NullPointerException.class, () -> connect.onRejected(null));
    }

    @Test
    void utf8LengthMatchesTheEncoderOnLongText() {
        StringBuilder ascii = new StringBuilder();
        while (ascii.length() < 5000) ascii.append("{\"t\":\"panel.set\",\"id\":\"shop\"},");
        String[] samples = {
            ascii.toString(),
            ascii.substring(0, 1023) + "🍁" + ascii.substring(1023),
            ascii.substring(0, 1500) + "é€" + ascii.substring(1500),
            ascii.substring(0, 2047) + "\ud83c" + ascii.substring(2047),
            ascii.substring(0, 200) + "€"
        };
        for (String s : samples) assertEquals(s.getBytes(StandardCharsets.UTF_8).length, Json.utf8Length(s));
    }

    @Test
    void rejectionDetailIsBuiltOnlyWhenRead() throws ReflectiveOperationException {
        hello();
        receive("{\"v\":1,\"t\":\"click\",\"src\":\"chat\",\"panel\":\"nope\",\"button\":\"b\",\"seq\":5}");
        Rejection r = rejected.get(0);
        Field detail = Rejection.class.getDeclaredField("detail");
        detail.setAccessible(true);
        assertNull(detail.get(r));
        assertEquals("this plugin sent no nope", r.detail());
    }
}
