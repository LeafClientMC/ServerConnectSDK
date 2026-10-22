package com.leafclient.connect.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private static final int OLD_TOKENS = 4;
    private List<OldToken> oldTokens = Collections.emptyList();

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

    void keepOldToken(String token, PanelRecord record, long until, long now) {
        List<OldToken> kept = new ArrayList<OldToken>(OLD_TOKENS + 1);
        for (OldToken t : record.oldTokens) if (now <= t.until) kept.add(t);
        record.oldTokens = Collections.emptyList();
        kept.add(new OldToken(token, record, until));
        while (kept.size() > OLD_TOKENS) kept.remove(0);
        this.oldTokens = kept;
    }

    PanelRecord oldTokenRecord(String token, long now) {
        prune(now);
        for (OldToken t : oldTokens) if (t.token.equals(token)) return t.record;
        return null;
    }

    void prune(long now) {
        if (oldTokens.isEmpty()) return;
        List<OldToken> kept = null;
        for (OldToken t : oldTokens) {
            if (now > t.until) continue;
            if (kept == null) kept = new ArrayList<OldToken>(oldTokens.size());
            kept.add(t);
        }
        if (kept == null) oldTokens = Collections.emptyList();
        else if (kept.size() != oldTokens.size()) oldTokens = kept;
    }

    private static final class OldToken {
        final String token;
        final PanelRecord record;
        final long until;

        OldToken(String token, PanelRecord record, long until) {
            this.token = token;
            this.record = record;
            this.until = until;
        }
    }

    boolean needsCurrentWorld() {
        return anchor != null && anchor.kind() == WorldAnchor.Kind.BLOCK && placeWorld == null && placeDim == null;
    }

    void rearm(long ttlMs, long now) {
        this.ttlMs = ttlMs;
        this.expiresAt = ttlMs > 0 ? now + ttlMs : 0;
        prune(now);
    }

    boolean world() {
        return "block".equals(in) || "entity".equals(in);
    }

    boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }
}
