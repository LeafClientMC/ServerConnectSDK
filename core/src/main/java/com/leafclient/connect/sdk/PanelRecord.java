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
    String placeWorld;
    String placeDim;
    private String oldToken;
    private PanelRecord oldRecord;
    private long oldTokenUntil;

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
        Place.Anchored<?> a = place instanceof Place.Anchored ? (Place.Anchored<?>) place : null;
        this.placeWorld = a == null ? null : a.worldId();
        this.placeDim = a == null ? null : a.dim();
    }

    void keepOldToken(String token, PanelRecord record, long until) {
        record.oldToken = null;
        record.oldRecord = null;
        this.oldToken = token;
        this.oldRecord = record;
        this.oldTokenUntil = until;
    }

    PanelRecord oldTokenRecord(String token, long now) {
        if (oldToken == null || now > oldTokenUntil || !oldToken.equals(token)) return null;
        return oldRecord;
    }

    boolean needsCurrentWorld() {
        return anchor != null && anchor.kind() == WorldAnchor.Kind.BLOCK && placeWorld == null && placeDim == null;
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
