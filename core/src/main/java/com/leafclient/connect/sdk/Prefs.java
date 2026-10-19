package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * The Server Connect settings the player chose in Leaf Client for this server. The client sends them in its hello and
 * again when they change ({@link LeafConnect#onPrefs}). A missing setting counts as allowed. The SDK does not filter
 * messages by these settings; check them before sending UI the player turned off.
 */
public final class Prefs {

    static final Prefs DEFAULT = new Prefs(true, true, true, true, true, true, true);

    private final boolean enabled;
    private final boolean menu;
    private final boolean screens;
    private final boolean world;
    private final boolean frontAuto;
    private final boolean toasts;
    private final boolean waypoints;

    private Prefs(boolean enabled, boolean menu, boolean screens, boolean world, boolean frontAuto, boolean toasts, boolean waypoints) {
        this.enabled = enabled;
        this.menu = menu;
        this.screens = screens;
        this.world = world;
        this.frontAuto = frontAuto;
        this.toasts = toasts;
        this.waypoints = waypoints;
    }

    static Prefs parse(JsonObject o) {
        if (o == null) return DEFAULT;
        return new Prefs(Json.getBool(o, "enabled", true), Json.getBool(o, "menu", true), Json.getBool(o, "screens", true),
                Json.getBool(o, "world", true), Json.getBool(o, "front_auto", true), Json.getBool(o, "toasts", true),
                Json.getBool(o, "waypoints", true));
    }

    /**
     * Tells whether Server Connect is on for this server. When it is off, every other setting reads false.
     *
     * @return the {@code enabled} setting
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Tells whether the player allows the server menu.
     *
     * @return the {@code menu} setting, false when {@link #enabled()} is false
     */
    public boolean menu() {
        return enabled && menu;
    }

    /**
     * Tells whether the player allows panels on game screens.
     *
     * @return the {@code screens} setting, false when {@link #enabled()} is false
     */
    public boolean screens() {
        return enabled && screens;
    }

    /**
     * Tells whether the player allows panels in the world.
     *
     * @return the {@code world} setting, false when {@link #enabled()} is false
     */
    public boolean world() {
        return enabled && world;
    }

    /**
     * Tells whether the player allows the server to open front panels on its own.
     *
     * @return the {@code front_auto} setting, false when {@link #enabled()} is false
     */
    public boolean frontAuto() {
        return enabled && frontAuto;
    }

    /**
     * Tells whether the player allows toasts.
     *
     * @return the {@code toasts} setting, false when {@link #enabled()} is false
     */
    public boolean toasts() {
        return enabled && toasts;
    }

    /**
     * Tells whether the player allows server waypoints.
     *
     * @return the {@code waypoints} setting, false when {@link #enabled()} is false
     */
    public boolean waypoints() {
        return enabled && waypoints;
    }

    @Override
    public String toString() {
        return "enabled=" + enabled + " menu=" + menu + " screens=" + screens + " world=" + world + " front_auto=" + frontAuto
                + " toasts=" + toasts + " waypoints=" + waypoints;
    }
}
