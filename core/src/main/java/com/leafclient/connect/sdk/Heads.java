package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * Player heads drawn in one horizontal line, left to right. Create it with one of the {@code Ui.heads} methods.
 * It holds at most {@link Limits#HEADS} players. Its width is the head count times the size plus the gaps, and its
 * height is one head.
 */
public final class Heads extends Element<Heads> {

    /** Head size in pixels the client uses when no size is set. */
    public static final int DEFAULT_SIZE = 16;
    /** Gap in pixels between heads the client uses when no gap is set. */
    public static final int DEFAULT_GAP = 2;

    private final List<String> players;
    private Integer size;
    private Integer gap;

    Heads(List<String> players) {
        Check.count(players.size(), Limits.HEADS, "heads.players", "players");
        for (String p : players) Check.uuid(p, "heads.players");
        this.players = players;
    }

    @Override
    String type() {
        return "heads";
    }

    /**
     * Sets the size of each head.
     *
     * @param pixels the size, 8 to 64
     * @return this element
     * @throws InvalidUiException if the size is out of range
     */
    public Heads size(int pixels) {
        this.size = Check.range(pixels, 8, 64, "heads.size");
        return this;
    }

    /**
     * Sets the gap between heads.
     *
     * @param pixels the gap, 0 to 16
     * @return this element
     * @throws InvalidUiException if the gap is out of range
     */
    public Heads gap(int pixels) {
        this.gap = Check.range(pixels, 0, 16, "heads.gap");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        o.add("players", Json.strings(players));
        if (size != null) o.addProperty("size", size);
        if (gap != null) o.addProperty("gap", gap);
    }
}
