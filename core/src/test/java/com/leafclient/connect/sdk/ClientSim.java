package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.leafclient.connect.core.ConnectChunks;
import com.leafclient.connect.core.ConnectClicks;
import com.leafclient.connect.core.ConnectElement;
import com.leafclient.connect.core.ConnectException;
import com.leafclient.connect.core.ConnectGuard;
import com.leafclient.connect.core.ConnectInbound;
import com.leafclient.connect.core.ConnectLimits;
import com.leafclient.connect.core.ConnectOutbound;
import com.leafclient.connect.core.ConnectPanel;
import com.leafclient.connect.core.ConnectState;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

final class ClientSim {

    static final List<String> CAPS = Arrays.asList("menu", "inventory", "chat", "front", "block", "entity", "hud",
            "container", "toast", "waypoint", "values", "timers", "conditions", "cooldowns", "batch", "chunk", "mods.lock", "forms",
            "items", "menu.style", "dialog.style", "badge.style", "styles", "layers", "images", "players");

    static final class Wire implements Transport {
        final List<byte[]> frames = new ArrayList<byte[]>();
        final int max;

        Wire(int max) {
            this.max = max;
        }

        @Override
        public void send(byte[] data) {
            frames.add(data);
        }

        @Override
        public int maxMessageBytes() {
            return max;
        }
    }

    final UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    final Object player = new Object();
    final long[] clock = {1_000_000L};
    final Wire wire;
    final LeafConnect connect;
    final ConnectState state = new ConnectState();
    final ConnectGuard guard = new ConnectGuard();
    final ConnectChunks chunks = new ConnectChunks();
    final ConnectClicks clicks = new ConnectClicks();
    final List<String> errors = new ArrayList<String>();
    final List<ConnectState.Effect> effects = new ArrayList<ConnectState.Effect>();
    final List<String> types = new ArrayList<String>();
    final List<String> allTypes = new ArrayList<String>();
    final List<JsonObject> replies = new ArrayList<JsonObject>();
    boolean inReach = true;
    int drops;
    int frameCount;
    int maxFrameBytes;
    int delivered;

    ClientSim() {
        this(ConnectLimits.SERVER_MESSAGE_BYTES);
    }

    ClientSim(int maxBytes) {
        wire = new Wire(maxBytes);
        connect = new LeafConnect(ConnectLog.silent(), () -> clock[0]) {
            @Override
            protected boolean inReach(Session session, WorldAnchor anchor) {
                return inReach;
            }
        };
    }

    Session hello() {
        return hello(CAPS);
    }

    Session hello(List<String> caps) {
        JsonObject hello = ConnectOutbound.hello("2.0.0", "1.21.11", "fabric", caps, Arrays.asList("freelook", "minimap"), ConnectOutbound.defaultPrefs());
        receive(ConnectOutbound.encode(hello));
        return connect.session(id);
    }

    void receive(JsonObject clientMessage) {
        receive(ConnectOutbound.encode(clientMessage));
    }

    void receive(byte[] data) {
        connect.receive(id, "Steve", player, data, wire);
    }

    void advance(long ms) {
        clock[0] += ms;
    }

    void flush() {
        connect.flush();
        deliverAll();
    }

    int flushUntilIdle(Session s) {
        int ticks = 0;
        flush();
        while (!s.idle() && ticks < 4000) {
            advance(50);
            flush();
            ticks++;
        }
        return ticks;
    }

    void deliverAll() {
        List<byte[]> pending = new ArrayList<byte[]>(wire.frames);
        wire.frames.clear();
        for (byte[] frame : pending) deliver(frame);
    }

    void deliver(byte[] data) {
        frameCount++;
        maxFrameBytes = Math.max(maxFrameBytes, data.length);
        long now = clock[0];
        ConnectInbound message;
        try {
            message = ConnectInbound.read(data);
        } catch (ConnectException e) {
            errors.add(e.code() + " " + e.detail());
            replies.add(ConnectOutbound.error(e.code(), e.ref(), e.detail()));
            return;
        }
        dispatch(message, now);
    }

    private void dispatch(ConnectInbound message, long now) {
        if (!message.known) {
            errors.add("unknown_type " + message.type);
            return;
        }
        types.add(message.type);
        allTypes.add(message.type);
        if ("batch".equals(message.type) && message.body.has("msgs")) {
            for (com.google.gson.JsonElement m : message.body.getAsJsonArray("msgs")) allTypes.add(m.getAsJsonObject().get("t").getAsString());
        }
        if (!guard.admit(message, now)) {
            drops++;
            errors.add("rate " + message.type);
            return;
        }
        if ("chunk".equals(message.type)) {
            try {
                String text = chunks.add(message.body, now);
                if (text == null) return;
                dispatch(ConnectInbound.readAssembled(text), now);
            } catch (ConnectException e) {
                e.withRef(message.ref());
                errors.add(e.code() + " " + e.detail());
                replies.add(ConnectOutbound.error(e.code(), e.ref(), e.detail()));
            }
            return;
        }
        try {
            List<ConnectState.Effect> list = state.apply(message, now);
            for (ConnectState.Effect e : list) {
                if (e.kind == ConnectState.EffectKind.OPEN_MENU || e.kind == ConnectState.EffectKind.OPEN_FRONT) {
                    if (!guard.admitOpen(now)) {
                        drops++;
                        errors.add("rate open");
                        continue;
                    }
                }
                effects.add(e);
            }
            delivered++;
        } catch (ConnectException e) {
            e.withRef(message.ref());
            errors.add(e.code() + " ref=" + e.ref() + " " + e.detail());
            replies.add(ConnectOutbound.error(e.code(), e.ref(), e.detail()));
        }
    }

    void replyErrors() {
        List<JsonObject> pending = new ArrayList<JsonObject>(replies);
        replies.clear();
        for (JsonObject o : pending) receive(o);
    }

    void assertClean() {
        if (!errors.isEmpty()) throw new AssertionError("client rejected messages: " + errors);
    }

    ConnectPanel panel(String id) {
        ConnectPanel p = state.panel(id);
        if (p == null) throw new AssertionError("client has no panel " + id + "; errors " + errors);
        return p;
    }

    ConnectElement element(String panelId, String elementId) {
        ConnectElement e = panel(panelId).find(elementId);
        if (e == null) throw new AssertionError("panel " + panelId + " has no element " + elementId);
        return e;
    }

    JsonObject clickJson(String src, String panelId, String button, String data, boolean confirmed) {
        ConnectPanel p = state.panel(panelId);
        JsonObject inputs = p != null && p.inputCount > 0 ? state.forms().collect(p) : null;
        return clicks.click(src, panelId, button, p == null ? null : p.token, data, confirmed, inputs);
    }

    void press(String src, String panelId, String button) {
        receive(clickJson(src, panelId, button, null, false));
    }

    static String text(byte[] frame) {
        return new String(frame, StandardCharsets.UTF_8);
    }
}
