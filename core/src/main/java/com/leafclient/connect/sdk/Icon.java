package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * An icon: an item, a player head, a built-in Leaf icon or a resource pack sprite. Used by buttons, tabs, toasts,
 * badges, waypoints and {@link IconElement}.
 */
public final class Icon {

    /** Size in pixels the client uses for item, head and sprite icons when no size is set. */
    public static final int DEFAULT_SIZE = 16;
    /** Size in pixels the client uses for Leaf icons when no size is set. */
    public static final int LEAF_DEFAULT_SIZE = 12;

    private final String kind;
    private final String value;
    private Integer size;
    private Integer count;
    private boolean glint;
    private String tint;

    private Icon(String kind, String value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * Creates an item icon.
     *
     * @param itemId an item id such as {@code minecraft:diamond}, at most 128 characters; the namespace is optional
     * @return the icon
     * @throws InvalidUiException if the id is missing, too long or not a valid resource id
     */
    public static Icon item(String itemId) {
        return new Icon("item", Check.pattern(Check.chars(itemId, 128, "icon.item"), Check.RESOURCE, "icon.item", "an item id such as minecraft:diamond"));
    }

    /**
     * Creates a player head icon.
     *
     * @param player the player UUID
     * @return the icon
     * @throws InvalidUiException if the UUID is null
     */
    public static Icon head(UUID player) {
        return new Icon("head", Check.notNull(player, "icon.head").toString());
    }

    /**
     * Creates a player head icon.
     *
     * @param playerUuid the player UUID, with or without dashes
     * @return the icon
     * @throws InvalidUiException if the text is not a UUID
     */
    public static Icon head(String playerUuid) {
        return new Icon("head", Check.uuid(playerUuid, "icon.head"));
    }

    /**
     * Creates a built-in Leaf icon.
     *
     * @param name the icon name such as {@code star}, 1 to 32 characters of {@code a-z 0-9 _}
     * @return the icon
     * @throws InvalidUiException if the name is missing or not valid
     */
    public static Icon leaf(String name) {
        return new Icon("leaf", Check.pattern(name, Check.LEAF_ICON, "icon.leaf", "a Leaf icon name such as star"));
    }

    /**
     * Creates a sprite icon from a resource pack.
     *
     * @param sprite a sprite id such as {@code myserver:gui/coin}, at most 128 characters
     * @return the icon
     * @throws InvalidUiException if the id is missing, too long or not a valid resource id
     */
    public static Icon sprite(String sprite) {
        return new Icon("sprite", Check.pattern(Check.chars(sprite, 128, "icon.sprite"), Check.RESOURCE, "icon.sprite", "a sprite id such as myserver:gui/coin"));
    }

    /**
     * Sets the icon size.
     *
     * @param pixels the size, 4 to 128
     * @return this icon
     * @throws InvalidUiException if the size is out of range
     */
    public Icon size(int pixels) {
        this.size = Check.range(pixels, 4, 128, "icon.size");
        return this;
    }

    /**
     * Sets the stack count drawn on an item icon.
     *
     * @param count the count, 1 to 99
     * @return this icon
     * @throws InvalidUiException if the count is out of range
     */
    public Icon count(int count) {
        this.count = Check.range(count, 1, 99, "icon.count");
        return this;
    }

    /**
     * Draws the enchantment glint on an item icon.
     *
     * @return this icon
     */
    public Icon glint() {
        this.glint = true;
        return this;
    }

    /**
     * Turns the enchantment glint on an item icon on or off.
     *
     * @param glint true to draw the glint
     * @return this icon
     */
    public Icon glint(boolean glint) {
        this.glint = glint;
        return this;
    }

    /**
     * Sets a color the icon is tinted with.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this icon
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Icon tint(String color) {
        this.tint = Check.color(color, "icon.tint");
        return this;
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty(kind, value);
        if (size != null) o.addProperty("size", size);
        if (count != null) o.addProperty("count", count);
        if (glint) o.addProperty("glint", true);
        Json.str(o, "tint", tint);
        return o;
    }
}
