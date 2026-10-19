package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A ranked list of players. Create it with {@link Ui#leaderboard()} and add rows in rank order. The first rows
 * (up to the podium size) are shown as cards with a player model, the rest as lines. It holds at most
 * {@link Limits#LEADERBOARD_ROWS} rows. The client expands it into several elements, and the SDK counts those against
 * {@link Limits#ELEMENTS_PER_PANEL}, {@link Limits#ELEMENT_DEPTH} and {@link Limits#PLAYERS_PER_PANEL}.
 * A leaderboard takes id, width, grow and when; height, pad, align and at throw {@link InvalidUiException} when the
 * panel is encoded.
 */
public final class Leaderboard extends Element<Leaderboard> {

    /** Number of podium places the client uses when none is set. */
    public static final int DEFAULT_PODIUM = 3;
    /** Podium card width in pixels the client uses when none is set. */
    public static final int DEFAULT_CARD_WIDTH = 96;

    private static final class Row {
        final String player;
        final Text name;
        final Text value;

        Row(String player, Text name, Text value) {
            this.player = player;
            this.name = name;
            this.value = value;
        }
    }

    private final List<Row> rows = new ArrayList<Row>();
    private Integer podium;
    private Integer cardWidth;
    private String accent;
    private List<String> colors;

    Leaderboard() {}

    @Override
    String type() {
        return "leaderboard";
    }

    /**
     * Adds the next row.
     *
     * @param player the player UUID
     * @param name the shown name, legacy text with {@code &} or {@code §} codes, at most 510 visible characters
     * @param value the shown score, legacy text, at most 510 visible characters, or null for none
     * @return this element
     * @throws InvalidUiException if the player or name is null, or the leaderboard already has
     *         {@link Limits#LEADERBOARD_ROWS} rows
     */
    public Leaderboard row(UUID player, String name, String value) {
        return row(Check.notNull(player, "leaderboard.rows.player").toString(), Text.of(name), Text.from(value));
    }

    /**
     * Adds the next row.
     *
     * @param player the player UUID
     * @param name the shown name, at most 510 visible characters
     * @param value the shown score, at most 510 visible characters, or null for none
     * @return this element
     * @throws InvalidUiException if the player or name is null, or the leaderboard already has
     *         {@link Limits#LEADERBOARD_ROWS} rows
     */
    public Leaderboard row(UUID player, Text name, Text value) {
        return row(Check.notNull(player, "leaderboard.rows.player").toString(), name, value);
    }

    /**
     * Adds the next row.
     *
     * @param playerUuid the player UUID, with or without dashes
     * @param name the shown name, at most 510 visible characters
     * @param value the shown score, at most 510 visible characters, or null for none
     * @return this element
     * @throws InvalidUiException if the UUID is not valid, the name is null, or the leaderboard already has
     *         {@link Limits#LEADERBOARD_ROWS} rows
     */
    public Leaderboard row(String playerUuid, Text name, Text value) {
        if (rows.size() >= Limits.LEADERBOARD_ROWS) throw Check.fail("leaderboard.rows", "more than " + Limits.LEADERBOARD_ROWS + " rows");
        rows.add(new Row(Check.uuid(playerUuid, "leaderboard.rows.player"), Check.notNull(name, "leaderboard.rows.name"), value));
        return this;
    }

    /**
     * Sets how many of the first rows are shown as podium cards.
     *
     * @param places 0 to 3; the default is {@link #DEFAULT_PODIUM}
     * @return this element
     * @throws InvalidUiException if the number is out of range
     */
    public Leaderboard podium(int places) {
        this.podium = Check.range(places, 0, 3, "leaderboard.podium");
        return this;
    }

    /**
     * Sets the width of each podium card.
     *
     * @param pixels the width, 72 to 160; the default is {@link #DEFAULT_CARD_WIDTH}
     * @return this element
     * @throws InvalidUiException if the width is out of range
     */
    public Leaderboard cardWidth(int pixels) {
        this.cardWidth = Check.range(pixels, 72, 160, "leaderboard.card_w");
        return this;
    }

    /**
     * Sets the accent color. The client tints the background of the lines below the podium with it.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Leaderboard accent(String color) {
        this.accent = Check.color(color, "leaderboard.accent");
        return this;
    }

    /**
     * Sets the colors of the first, second and third place. Places without a color keep the client default.
     *
     * @param colors up to 3 colors, each {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if there are more than 3 colors or a color is not valid
     */
    public Leaderboard placeColors(String... colors) {
        Check.count(colors.length, 3, "leaderboard.colors", "colors");
        List<String> list = new ArrayList<String>();
        for (String c : colors) list.add(Check.color(c, "leaderboard.colors"));
        this.colors = list;
        return this;
    }

    @Override
    JsonObject encode(Encoder enc, String path, int depth) {
        if (h != null || pad != null || align != null || at != null) {
            throw enc.fail(path, "a leaderboard takes id, width, grow and when; height, pad, align and at are not used");
        }
        int places = Math.min(podium == null ? DEFAULT_PODIUM : podium, rows.size());
        enc.depth(depth, path);
        enc.element(path);
        if (id != null) enc.claimId(id, "leaderboard", path);
        if (places > 0) {
            enc.depth(depth + 1, path);
            enc.element(path);
            for (int i = 0; i < places; i++) {
                enc.depth(depth + 3, path);
                for (int k = 0; k < (rows.get(i).value != null ? 5 : 4); k++) enc.element(path);
                enc.player(path);
            }
        }
        if (rows.size() > places) {
            enc.depth(depth + 2, path);
            enc.element(path);
            for (int i = places; i < rows.size(); i++) {
                enc.depth(depth + 3, path);
                for (int k = 0; k < (rows.get(i).value != null ? 5 : 4); k++) enc.element(path);
            }
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "leaderboard");
        Json.str(o, "id", id);
        if (w != null) o.addProperty("w", w);
        if (grow != null) Json.num(o, "grow", grow);
        if (when != null) o.add("when", when.toJson(enc.at(path + ".when")));
        JsonArray list = new JsonArray();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            String rp = path + ".rows[" + i + "]";
            JsonObject row = new JsonObject();
            row.addProperty("player", r.player);
            row.add("name", Check.text(r.name, Limits.TEXT_CHARS - 2, enc.at(rp + ".name")).toJson());
            if (r.value != null) row.add("value", Check.text(r.value, Limits.TEXT_CHARS - 2, enc.at(rp + ".value")).toJson());
            list.add(row);
        }
        o.add("rows", list);
        if (podium != null) o.addProperty("podium", podium);
        if (cardWidth != null) o.addProperty("card_w", cardWidth);
        Json.str(o, "accent", accent);
        if (colors != null) o.add("colors", Json.strings(colors));
        return o;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        throw new IllegalStateException("leaderboard encodes itself");
    }
}
