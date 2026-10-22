package com.leafclient.connect.sdk;

import java.util.Map;

final class PanelRecord {

    final String id;
    String token;
    String in;
    boolean rightSide;
    String tab;
    final String group;
    final boolean connectionScope;
    long ttlMs;
    long expiresAt;
    final Map<String, Encoder.ButtonSpec> buttons;
    final Map<String, Encoder.InputSpec> inputs;
    final Map<String, String> elementTypes;
    int[] size;
    WorldAnchor anchor;
    Object world;

    PanelRecord(String id, String token, Place<?> place, int[] size, String group, boolean connectionScope, long ttlMs, long now,
                Map<String, Encoder.ButtonSpec> buttons, Map<String, Encoder.InputSpec> inputs, Map<String, String> elementTypes) {
        this.id = id;
        this.token = token;
        this.group = group;
        this.connectionScope = connectionScope;
        this.buttons = buttons;
        this.inputs = inputs;
        this.elementTypes = elementTypes;
        this.size = size;
        moveTo(place);
        rearm(ttlMs, now);
    }

    void moveTo(Place<?> place) {
        this.in = place.in;
        this.rightSide = place.rightSide();
        this.tab = place.tab();
        this.anchor = WorldAnchor.of(place, size);
    }

    void rearm(long ttlMs, long now) {
        this.ttlMs = ttlMs;
        this.expiresAt = ttlMs > 0 ? now + ttlMs : 0;
    }

    boolean world() {
        return "block".equals(in) || "entity".equals(in);
    }

    boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }
}
