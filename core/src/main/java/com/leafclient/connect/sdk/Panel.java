package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.LinkedHashMap;

/**
 * The {@code panel.set} message: a panel of elements shown at a {@link Place}. Sending a panel with an id that already
 * exists replaces it. The panel is sent only to clients that list its place (such as {@code menu} or {@code hud})
 * in their caps. When it is sent, the session checks the per player panel limits in {@link Limits} and records the
 * buttons and inputs, so it can check the clicks that come back. Each send gets a new click token unless
 * {@link #token(String)} sets one.
 */
public final class Panel extends Message {

    /** Color theme of the panel. The client default is {@link #LEAF}. */
    public enum Theme {
        /** The Leaf theme. */
        LEAF,
        /** A dark theme. */
        DARK,
        /** A light theme. */
        LIGHT,
        /** The clear theme. */
        CLEAR
    }

    /** When the client drops the panel. */
    public enum Scope {
        /** The client removes the panel when the player joins a server again, such as a switch behind a proxy. The default. */
        SERVER,
        /** The client keeps the panel when the player joins a server again. */
        CONNECTION
    }

    /** Inner padding in pixels the client uses when none is set. */
    public static final int DEFAULT_PAD = 6;
    /** Corner radius in pixels the client uses when none is set. */
    public static final int DEFAULT_RADIUS = 6;

    private final String id;
    private final Place<?> place;
    private Element<?> root;
    private int[] size;
    private Theme theme;
    private Integer pad;
    private Integer radius;
    private String bg;
    private String border;
    private Long ttl;
    private String group;
    private Scope scope;
    private Integer z;
    private Condition when;
    private String token;

    private Panel(String id, Place<?> place) {
        this.id = Check.id(id, "panel.id");
        this.place = Check.notNull(place, "panel \"" + id + "\" place");
    }

    /**
     * Creates a panel. Give it content with {@link #root(Element)} or {@link #content(Element...)} before sending.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param place where the panel is shown
     * @return the new message
     * @throws InvalidUiException if the id is not valid or the place is null
     */
    public static Panel create(String id, Place<?> place) {
        return new Panel(id, place);
    }

    /**
     * Returns the panel id.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Sets the root element.
     *
     * @param root the root element; a panel without one throws {@link InvalidUiException} when it is encoded
     * @return this message
     */
    public Panel root(Element<?> root) {
        this.root = root;
        return this;
    }

    /**
     * Sets the root element to a column holding the given children.
     *
     * @param children the children
     * @return this message
     * @throws InvalidUiException if a child is null
     */
    public Panel content(Element<?>... children) {
        this.root = Ui.col(children);
        return this;
    }

    /**
     * Sets a fixed panel size.
     *
     * @param width the width, 1 to {@link Limits#PANEL_MAX_SIZE}
     * @param height the height, 1 to {@link Limits#PANEL_MAX_SIZE}
     * @return this message
     * @throws InvalidUiException if a value is out of range
     */
    public Panel size(int width, int height) {
        this.size = new int[]{Check.range(width, 1, Limits.PANEL_MAX_SIZE, "panel.size"), Check.range(height, 1, Limits.PANEL_MAX_SIZE, "panel.size")};
        return this;
    }

    /**
     * Sets the color theme.
     *
     * @param theme the theme, or null for the client default
     * @return this message
     */
    public Panel theme(Theme theme) {
        this.theme = theme;
        return this;
    }

    /**
     * Sets the inner padding.
     *
     * @param pixels the padding, 0 to 64; the default is {@link #DEFAULT_PAD}
     * @return this message
     * @throws InvalidUiException if the padding is out of range
     */
    public Panel pad(int pixels) {
        this.pad = Check.range(pixels, 0, 64, "panel.style.pad");
        return this;
    }

    /**
     * Sets the corner radius.
     *
     * @param pixels the radius, 0 to 24; the default is {@link #DEFAULT_RADIUS}
     * @return this message
     * @throws InvalidUiException if the radius is out of range
     */
    public Panel radius(int pixels) {
        this.radius = Check.range(pixels, 0, 24, "panel.style.radius");
        return this;
    }

    /**
     * Sets the background color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Panel bg(String color) {
        this.bg = Check.color(color, "panel.style.bg");
        return this;
    }

    /**
     * Sets the border color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Panel border(String color) {
        this.border = Check.color(color, "panel.style.border");
        return this;
    }

    /**
     * Sets how long the panel lives. Clicks more than 3 seconds after it ends are rejected with
     * {@link Rejection.Reason#EXPIRED}. The SDK restarts its timer for the panel on every {@link Patch} of it.
     *
     * @param ms the time in milliseconds, 0 to 86400000 (24 hours); 0 means no limit
     * @return this message
     * @throws InvalidUiException if the time is out of range
     */
    public Panel ttl(long ms) {
        this.ttl = Check.range(ms, 0, 86400000, "panel.ttl");
        return this;
    }

    /**
     * Puts the panel in a group, so {@link Session#removeGroup(String)} removes it with the others.
     *
     * @param group 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return this message
     * @throws InvalidUiException if the group is missing or not valid
     */
    public Panel group(String group) {
        this.group = Check.id(group, "panel.group");
        return this;
    }

    /**
     * Sets when the client drops the panel.
     *
     * @param scope the scope, or null for the default {@link Scope#SERVER}
     * @return this message
     */
    public Panel scope(Scope scope) {
        this.scope = scope;
        return this;
    }

    /**
     * Sets the z value of the panel. The client default is 0.
     *
     * @param z -1000 to 1000
     * @return this message
     * @throws InvalidUiException if the value is out of range
     */
    public Panel z(int z) {
        this.z = Check.range(z, -1000, 1000, "panel.z");
        return this;
    }

    /**
     * Shows the panel only while a condition is true on the client.
     *
     * @param condition the condition, or null to always show it
     * @return this message
     */
    public Panel when(Condition condition) {
        this.when = condition;
        return this;
    }

    /**
     * Sets the click token instead of a new random one. Clicks must carry the token of the last
     * {@code panel.set} or patch, or they are rejected with {@link Rejection.Reason#BAD_TOKEN}.
     *
     * @param token at most {@link Limits#TOKEN_CHARS} characters
     * @return this message
     * @throws InvalidUiException if the token is null or too long
     */
    public Panel token(String token) {
        this.token = Check.chars(token, Limits.TOKEN_CHARS, "panel.token");
        return this;
    }

    @Override
    String requiredCap() {
        return place.in;
    }

    @Override
    Outgoing encode(final Session session) {
        String prefix = "panel \"" + id + "\"";
        if (root == null) throw Check.fail(prefix + " root", "missing, give the panel a root element");
        Encoder enc = new Encoder(prefix, "menu".equals(place.in));
        String tok = token != null ? token : session == null ? "preview" : session.newToken();
        JsonObject p = new JsonObject();
        p.addProperty("id", id);
        p.addProperty("token", tok);
        p.add("place", place.toJson());
        if (size != null) p.add("size", Json.numbers(size[0], size[1]));
        JsonObject style = new JsonObject();
        if (theme != null) style.addProperty("theme", Check.lower(theme));
        if (pad != null) style.addProperty("pad", pad);
        if (radius != null) style.addProperty("radius", radius);
        Json.str(style, "bg", bg);
        Json.str(style, "border", border);
        if (!style.entrySet().isEmpty()) p.add("style", style);
        p.add("root", root.encode(enc, "panel.root", 1));
        if (ttl != null && ttl > 0) p.addProperty("ttl", ttl);
        Json.str(p, "group", group);
        if (scope != null) p.addProperty("scope", Check.lower(scope));
        if (z != null) p.addProperty("z", z);
        if (when != null) p.add("when", when.toJson(prefix + " panel.when"));
        JsonObject msg = Json.envelope("panel.set");
        msg.add("panel", p);
        if (session == null) return Outgoing.simple(msg, "panel.set " + id);
        final PanelRecord record = new PanelRecord(id, tok, place, size, group, scope == Scope.CONNECTION, ttl == null ? 0 : ttl, session.now(),
                new LinkedHashMap<String, Encoder.ButtonSpec>(enc.buttonSpecs), new LinkedHashMap<String, Encoder.InputSpec>(enc.inputSpecs),
                new LinkedHashMap<String, String>(enc.ids));
        session.checkPanelLimits(record);
        return new Outgoing(msg, "panel.set " + id, new Runnable() {
            @Override
            public void run() {
                session.putPanel(record);
            }
        });
    }
}
