package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * The {@code welcome} message: the server name, icon and accent color Leaf Client shows for this server.
 */
public final class Welcome extends Message {

    private final Text name;
    private Icon icon;
    private String accent;

    private Welcome(Text name) {
        this.name = name;
    }

    /**
     * Creates a welcome message.
     *
     * @param serverName the server name, legacy text with {@code &} or {@code §} codes, at most 48 visible characters
     * @return the new message
     * @throws InvalidUiException if the name is null
     */
    public static Welcome create(String serverName) {
        return new Welcome(Text.of(serverName));
    }

    /**
     * Creates a welcome message.
     *
     * @param serverName the server name, at most 48 visible characters, or null to send no name
     * @return the new message
     */
    public static Welcome create(Text serverName) {
        return new Welcome(serverName);
    }

    /**
     * Sets the server icon.
     *
     * @param icon the icon, or null for none
     * @return this message
     */
    public Welcome icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Sets the server accent color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Welcome accent(String color) {
        this.accent = Check.color(color, "welcome.accent");
        return this;
    }

    @Override
    Outgoing encode(Session session) {
        JsonObject o = Json.envelope("welcome");
        if (name != null) o.add("name", Check.text(name, 48, "welcome.name").toJson());
        if (icon != null) o.add("icon", icon.toJson());
        Json.str(o, "accent", accent);
        return Outgoing.simple(o, "welcome");
    }
}
