package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The {@code waypoint.set} message: a server waypoint shown in the world. Sending a waypoint with an id that already
 * exists replaces it. A player has at most {@link Limits#WAYPOINTS} server waypoints, and the session checks that
 * before sending. It is sent only to clients that list the {@code waypoint} cap. Remove waypoints with
 * {@link Session#removeWaypoints(String...)}.
 */
public final class Waypoint extends Message {

    /** Color the client uses when none is set. */
    public static final String DEFAULT_COLOR = "#FF8BE9A8";

    private final String id;
    private final Text name;
    private final double x;
    private final double y;
    private final double z;
    private String dim;
    private String world;
    private String color;
    private Icon icon;
    private Boolean beam;
    private Boolean label;
    private Boolean distance;
    private Long ttl;
    private List<Action> actions;
    private Consumer<Click> handler;

    private Waypoint(String id, Text name, double x, double y, double z) {
        this.id = Check.id(id, "waypoint.id");
        this.name = Check.text(name, 48, "waypoint \"" + id + "\" name");
        this.x = Check.range(x, -30000000, 30000000, "waypoint.pos");
        this.y = Check.range(y, -30000000, 30000000, "waypoint.pos");
        this.z = Check.range(z, -30000000, 30000000, "waypoint.pos");
    }

    /**
     * Creates a waypoint.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param name legacy text with {@code &} or {@code §} codes, at most 48 visible characters
     * @param x the x coordinate, -30000000 to 30000000
     * @param y the y coordinate, -30000000 to 30000000
     * @param z the z coordinate, -30000000 to 30000000
     * @return the new message
     * @throws InvalidUiException if a value is missing or out of range
     */
    public static Waypoint create(String id, String name, double x, double y, double z) {
        return new Waypoint(id, Text.of(name), x, y, z);
    }

    /**
     * Creates a waypoint.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param name the name, at most 48 visible characters
     * @param x the x coordinate, -30000000 to 30000000
     * @param y the y coordinate, -30000000 to 30000000
     * @param z the z coordinate, -30000000 to 30000000
     * @return the new message
     * @throws InvalidUiException if a value is missing or out of range
     */
    public static Waypoint create(String id, Text name, double x, double y, double z) {
        return new Waypoint(id, name, x, y, z);
    }

    /**
     * Sets the dimension the waypoint belongs to.
     *
     * @param dimension the dimension id, at most 64 characters
     * @return this message
     * @throws InvalidUiException if the dimension is null or too long
     */
    public Waypoint dim(String dimension) {
        this.dim = Check.chars(dimension, 64, "waypoint.dim");
        return this;
    }

    /**
     * Sets the world the waypoint belongs to, as named with {@link Session#world(String)}.
     *
     * @param worldId 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return this message
     * @throws InvalidUiException if the id is missing or not valid
     */
    public Waypoint world(String worldId) {
        this.world = Check.id(worldId, "waypoint.world");
        return this;
    }

    /**
     * Sets the waypoint color. The default is {@link #DEFAULT_COLOR}.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Waypoint color(String color) {
        this.color = Check.color(color, "waypoint.color");
        return this;
    }

    /**
     * Sets the waypoint icon.
     *
     * @param icon the icon, or null for none
     * @return this message
     */
    public Waypoint icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Shows or hides the beam. The client default is shown.
     *
     * @param beam false to hide the beam
     * @return this message
     */
    public Waypoint beam(boolean beam) {
        this.beam = beam;
        return this;
    }

    /**
     * Shows or hides the name label. The client default is shown.
     *
     * @param label false to hide the label
     * @return this message
     */
    public Waypoint label(boolean label) {
        this.label = label;
        return this;
    }

    /**
     * Shows or hides the distance. The client default is shown.
     *
     * @param distance false to hide the distance
     * @return this message
     */
    public Waypoint distance(boolean distance) {
        this.distance = distance;
        return this;
    }

    /**
     * Sets how long the waypoint lives. Clicks more than 3 seconds after it ends are rejected with
     * {@link Rejection.Reason#EXPIRED}.
     *
     * @param ms the time in milliseconds, 0 to 86400000 (24 hours); 0 means no limit
     * @return this message
     * @throws InvalidUiException if the time is out of range
     */
    public Waypoint ttl(long ms) {
        this.ttl = Check.range(ms, 0, 86400000, "waypoint.ttl");
        return this;
    }

    /**
     * Sets the actions run when the player clicks the waypoint label.
     *
     * @param actions at most {@link Limits#ACTIONS} actions, with at most one of command, url and copy
     * @return this message
     * @throws InvalidUiException if there are too many actions
     */
    public Waypoint on(Action... actions) {
        this.actions = Arrays.asList(actions.clone());
        Check.count(this.actions.size(), Limits.ACTIONS, "waypoint.on", "actions");
        return this;
    }

    /**
     * Sets the handler that runs on the server when the player clicks the waypoint label. Without {@link #on} actions,
     * the waypoint gets {@link Action#send()}. With actions, one of them must be {@link Action#send()}, or encoding
     * throws {@link InvalidUiException}.
     *
     * @param handler the handler, or null for none
     * @return this message
     */
    public Waypoint onClick(Consumer<Click> handler) {
        this.handler = handler;
        return this;
    }

    @Override
    String requiredCap() {
        return "waypoint";
    }

    @Override
    Outgoing encode(final Session session) {
        String at = "waypoint \"" + id + "\"";
        JsonObject o = Json.envelope("waypoint.set");
        o.addProperty("id", id);
        o.add("name", name.toJson());
        o.add("pos", Json.numbers(x, y, z));
        Json.str(o, "dim", dim);
        Json.str(o, "world", world);
        Json.str(o, "color", color);
        if (icon != null) o.add("icon", icon.toJson());
        if (beam != null) o.addProperty("beam", beam);
        if (label != null) o.addProperty("label", label);
        if (distance != null) o.addProperty("distance", distance);
        if (ttl != null && ttl > 0) o.addProperty("ttl", ttl);
        List<Action> effective = actions;
        if (effective == null && handler != null) effective = Collections.singletonList(Action.send());
        if (handler != null && !Action.anySends(effective)) throw Check.fail(at + " on", "has a click handler but no Action.send()");
        final String token = effective == null ? null : session == null ? "preview" : session.newToken();
        Json.str(o, "token", token);
        if (effective != null) o.add("on", Action.list(effective, at + " on"));
        if (session == null) return Outgoing.simple(o, "waypoint.set " + id);
        session.checkWaypointLimit(id);
        final long expires = ttl != null && ttl > 0 ? session.now() + ttl : 0;
        final Consumer<Click> h = handler;
        return new Outgoing(o, "waypoint.set " + id, new Runnable() {
            @Override
            public void run() {
                session.putWaypoint(id, token, h, expires);
            }
        });
    }
}
