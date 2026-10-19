package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchingTest {

    static List<Panel> burst() {
        List<Panel> out = new ArrayList<Panel>();
        for (int i = 0; i < 200; i++) {
            int slot = i % 92;
            Panel p;
            if (slot < 60) {
                p = Panel.create("world." + slot, Place.block(slot, 64, 0).offset(0.5, 2, 0.5));
            } else {
                int tab = (slot - 60) / 4;
                p = Panel.create("menu." + slot, Place.menu("tab" + tab));
            }
            out.add(p.content(Ui.text("&lPanel " + slot + " version " + i), Ui.button("buy", "Buy for {n:price." + slot + "}")
                    .tooltip("A tooltip line that makes the panel a little larger", "And another one, number " + i)
                    .badge("NEW"), Ui.bar("{v:hp}", "20")));
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(ints = {ConnectLimits.SERVER_MESSAGE_BYTES, Limits.LEGACY_MESSAGE_BYTES})
    void burstOf200PanelsArrivesAsBatchesWithZeroDrops(int cap) {
        ClientSim sim = new ClientSim(cap);
        Session s = sim.hello();
        for (Panel p : burst()) s.send(p);
        assertTrue(sim.flushUntilIdle(s) > 0, "panels over the per type rate wait for later ticks");
        sim.assertClean();
        assertEquals(0, sim.drops);
        assertEquals(92, sim.state.allPanels(sim.clock[0]).size());
        assertTrue(sim.maxFrameBytes <= cap, "frame of " + sim.maxFrameBytes + " bytes over " + cap);
        assertTrue(sim.frameCount >= 4 && sim.frameCount <= 20, "frames: " + sim.frameCount);
        for (String t : sim.types) assertEquals("batch", t);
        assertTrue(sim.panel("world.7").token.length() >= 12);
    }

    @Test
    void theSameBurstSentOneByOneIsDroppedByTheClient() {
        ClientSim sim = new ClientSim();
        for (Panel p : burst()) sim.deliver(p.toJson().getBytes(StandardCharsets.UTF_8));
        assertEquals(180, sim.drops);
    }

    static Panel huge(String id, int buttons, int lines, String line) {
        Container grid = Ui.grid(4);
        for (int b = 0; b < buttons; b++) {
            String[] tips = new String[lines];
            for (int l = 0; l < lines; l++) tips[l] = line;
            grid.add(Ui.button("b" + b, "Button " + b).tooltip(tips));
        }
        return Panel.create(id, Place.menu("big")).root(grid);
    }

    static String tricky() {
        StringBuilder sb = new StringBuilder();
        String unit = "\"q\" back\\slash été 漢字 😀   tab\t ";
        while (sb.length() + unit.length() <= 250) sb.append(unit);
        return sb.toString();
    }

    @ParameterizedTest
    @ValueSource(ints = {ConnectLimits.SERVER_MESSAGE_BYTES, Limits.LEGACY_MESSAGE_BYTES})
    void oversizedPanelIsChunkedAndReassembledByTheClient(int cap) {
        ClientSim sim = new ClientSim(cap);
        Session s = sim.hello();
        String line = tricky();
        Panel big = huge("big", 40, 20, line);
        s.send(big);
        sim.flush();
        sim.assertClean();
        assertEquals(0, sim.drops);
        assertTrue(sim.types.contains("chunk"));
        assertTrue(sim.maxFrameBytes <= cap);
        assertEquals(40, sim.panel("big").buttonCount);
        ConnectElement b = sim.element("big", "b39");
        assertEquals(20, b.tooltip.size());
        assertEquals(line.replace("\t", "").replace(" ", " "), b.tooltip.get(19).plain().replace("\t", ""));
        assertEquals(0, sim.chunks.openSets());
    }

    @Test
    void messageTooLargeEvenForChunksIsRejectedAtSend() {
        ClientSim sim = new ClientSim(Limits.LEGACY_MESSAGE_BYTES);
        Session s = sim.hello();
        StringBuilder quotes = new StringBuilder();
        for (int i = 0; i < 250; i++) quotes.append('"');
        Panel tooBig = huge("too.big", 48, 20, quotes.toString());
        InvalidUiException e = assertThrows(InvalidUiException.class, () -> s.send(tooBig));
        assertTrue(e.getMessage().contains("CHUNKED_MESSAGE_BYTES") || e.getMessage().contains("CHUNK_PARTS"), e.getMessage());
        sim.flush();
        assertEquals(0, sim.frameCount);
    }

    @Test
    void legacyServersChunkUnderThePluginMessageLimit() {
        ClientSim sim = new ClientSim(Limits.LEGACY_MESSAGE_BYTES);
        Session s = sim.hello();
        s.send(huge("big", 48, 20, tricky()));
        sim.flush();
        sim.assertClean();
        assertTrue(sim.maxFrameBytes <= Limits.LEGACY_MESSAGE_BYTES);
        assertTrue(sim.maxFrameBytes < 32766);
    }

    @Test
    void floodAcrossTicksNeverTripsTheClientRateLimit() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        Panel big = huge("big", 40, 20, tricky());
        for (int i = 0; i < 60; i++) s.send(big);
        for (int i = 0; i < 2000; i++) s.send(Values.of("tick", i));
        int ticks = 0;
        do {
            sim.flush();
            sim.advance(50);
            ticks++;
        } while (!s.idle() && ticks < 2000);
        sim.flush();
        sim.assertClean();
        assertEquals(0, sim.drops);
        assertTrue(s.idle());
        assertEquals("1999", sim.state.values().raw("tick", sim.clock[0]));
        assertTrue(ticks > 1, "pacing should spread the flood over several ticks");
    }

    @Test
    void opensAreSpacedSoTheClientNeverDropsThem() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        s.send(Menu.create("M").add(Menu.tab("a", "A")));
        s.send(Panel.create("vote", Place.front()).content(Ui.button("x", "X")));
        s.openMenu();
        sim.flush();
        sim.advance(100);
        s.openPanel("vote");
        int opens = 0;
        for (int i = 0; i < 60; i++) {
            sim.flush();
            sim.advance(50);
        }
        for (com.leafclient.connect.core.ConnectState.Effect e : sim.effects) {
            if (e.kind == com.leafclient.connect.core.ConnectState.EffectKind.OPEN_MENU || e.kind == com.leafclient.connect.core.ConnectState.EffectKind.OPEN_FRONT) opens++;
        }
        assertEquals(2, opens);
        sim.receive(closed("menu", null, "player"));
        sim.advance(10);
        s.openMenu();
        sim.flush();
        sim.advance(500);
        sim.flush();
        sim.advance(800);
        sim.flush();
        sim.assertClean();
        assertEquals(0, sim.drops);
    }

    @Test
    void locksArePacedToTwoPerSecond() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        for (int i = 0; i < 6; i++) s.lockMods((String) null, "minimap");
        sim.flush();
        for (int i = 0; i < 80; i++) {
            sim.advance(50);
            sim.flush();
        }
        sim.assertClean();
        assertEquals(0, sim.drops);
        assertEquals(6, sim.types.size());
    }

    @Test
    void nestingDeeperThanTheClientReadsIsRejected() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        StringBuilder json = new StringBuilder("\"x\"");
        for (int i = 0; i < 30; i++) json.insert(0, "{\"text\":\"\",\"extra\":[").append("]}");
        Text deep = Text.json(json.toString());
        InvalidUiException e = assertThrows(InvalidUiException.class, () -> s.send(Panel.create("deep", Place.chat()).content(Ui.text(deep))));
        assertTrue(e.getMessage().contains("JSON_DEPTH"), e.getMessage());
    }

    static JsonObject closed(String what, String id, String why) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", "closed");
        o.addProperty("what", what);
        if (id != null) o.addProperty("id", id);
        o.addProperty("why", why);
        return o;
    }

    @Test
    void typeLimitsSplitBatchesAcrossTicksInsteadOfBeingDropped() {
        ClientSim sim = new ClientSim();
        Session s = sim.hello();
        for (int i = 0; i < 12; i++) s.send(Toast.create("t" + i).title("Toast " + i));
        for (int i = 0; i < 40; i++) s.send(Panel.create("c" + (i % 4), Place.chat()).content(Ui.text("v" + i)));
        sim.flush();
        assertTrue(!s.idle(), "the first tick holds back what the type limits do not allow yet");
        int ticks = sim.flushUntilIdle(s);
        sim.assertClean();
        assertEquals(0, sim.drops);
        assertTrue(ticks > 20, "12 toasts at under 1 per second take several seconds, took " + ticks + " ticks");
        assertEquals("v39", sim.panel("c3").root.children.get(0).text.plain());
    }

    @Test
    void queuedValueUpdatesMergeIntoOneMessage() {
        String a = "{\"v\":1,\"t\":\"values.set\",\"set\":{\"x\":1,\"y\":2},\"del\":[\"z\"]}";
        String b = "{\"v\":1,\"t\":\"values.set\",\"set\":{\"z\":3},\"del\":[\"y\"]}";
        JsonObject merged = parse(Outbox.mergeValues(a, b));
        assertEquals(1, merged.getAsJsonObject("set").get("x").getAsInt());
        assertEquals(3, merged.getAsJsonObject("set").get("z").getAsInt());
        assertTrue(!merged.getAsJsonObject("set").has("y"));
        assertEquals(1, merged.getAsJsonArray("del").size());
        assertEquals("y", merged.getAsJsonArray("del").get(0).getAsString());
    }

    @SuppressWarnings("deprecation")
    static JsonObject parse(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }
}
