package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A small label drawn on the corner or edge of a button or menu tab. Set it with {@link Button#badge(Badge)},
 * {@link Menu.Tab#badge(Badge)} or {@link Patch#badge(String, Badge)}. A badge needs a visible text or an icon.
 * Its text shows at most 32 visible characters on a button and 16 on a menu tab.
 */
public final class Badge {

    /** Where the badge sits on its button or tab. */
    public enum Pos {
        /** Top right corner. */
        TOP_RIGHT,
        /** Top left corner. */
        TOP_LEFT,
        /** Bottom right corner. */
        BOTTOM_RIGHT,
        /** Bottom left corner. */
        BOTTOM_LEFT,
        /** Top edge. */
        TOP,
        /** Bottom edge. */
        BOTTOM
    }

    /** Background color the client uses when none is set. */
    public static final String DEFAULT_BG = "#FFE5484D";
    /** Text color the client uses when none is set. */
    public static final String DEFAULT_COLOR = "#FFFFFFFF";

    private Text text;
    private Icon icon;
    private String bg;
    private String border;
    private String color;
    private Double scale;
    private Double textScale;
    private Integer iconSize;
    private Integer pad;
    private Shape shape;
    private Pos pos;
    private int offsetX;
    private int offsetY;

    private Badge() {}

    /**
     * Creates a text badge.
     *
     * @param text legacy text with {@code &} or {@code §} codes
     * @return the badge
     * @throws InvalidUiException if the text is null
     */
    public static Badge of(String text) {
        return of(Text.of(text));
    }

    /**
     * Creates a text badge.
     *
     * @param text the text
     * @return the badge
     * @throws InvalidUiException if the text is null
     */
    public static Badge of(Text text) {
        Badge b = new Badge();
        b.text = Check.notNull(text, "badge.text");
        return b;
    }

    /**
     * Creates an icon badge.
     *
     * @param icon the icon
     * @return the badge
     * @throws InvalidUiException if the icon is null
     */
    public static Badge of(Icon icon) {
        Badge b = new Badge();
        b.icon = Check.notNull(icon, "badge.icon");
        return b;
    }

    /**
     * Sets the icon shown in the badge.
     *
     * @param icon the icon, or null for none
     * @return this badge
     */
    public Badge icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Sets the background color. The default is {@link #DEFAULT_BG}.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this badge
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Badge bg(String color) {
        this.bg = Check.color(color, "badge.bg");
        return this;
    }

    /**
     * Sets the border color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this badge
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Badge border(String color) {
        this.border = Check.color(color, "badge.border");
        return this;
    }

    /**
     * Sets the text color. The default is {@link #DEFAULT_COLOR}.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this badge
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Badge color(String color) {
        this.color = Check.color(color, "badge.color");
        return this;
    }

    /**
     * Sets the scale of the whole badge.
     *
     * @param scale the scale, 0.5 to 3
     * @return this badge
     * @throws InvalidUiException if the scale is out of range
     */
    public Badge scale(double scale) {
        this.scale = Check.range(scale, 0.5, 3, "badge.scale");
        return this;
    }

    /**
     * Sets the scale of the badge text.
     *
     * @param scale the scale, 0.5 to 3
     * @return this badge
     * @throws InvalidUiException if the scale is out of range
     */
    public Badge textScale(double scale) {
        this.textScale = Check.range(scale, 0.5, 3, "badge.text_scale");
        return this;
    }

    /**
     * Sets the icon size.
     *
     * @param pixels the size, 4 to 32
     * @return this badge
     * @throws InvalidUiException if the size is out of range
     */
    public Badge iconSize(int pixels) {
        this.iconSize = Check.range(pixels, 4, 32, "badge.icon_size");
        return this;
    }

    /**
     * Sets the inner padding.
     *
     * @param pixels the padding, 0 to 12
     * @return this badge
     * @throws InvalidUiException if the padding is out of range
     */
    public Badge pad(int pixels) {
        this.pad = Check.range(pixels, 0, 12, "badge.pad");
        return this;
    }

    /**
     * Sets the corner shape. The client default is {@link Shape#PILL}.
     *
     * @param shape the shape, or null for the default
     * @return this badge
     */
    public Badge shape(Shape shape) {
        this.shape = shape;
        return this;
    }

    /**
     * Sets where the badge sits. The client default is {@link Pos#TOP_RIGHT}.
     *
     * @param pos the position, or null for the default
     * @return this badge
     */
    public Badge pos(Pos pos) {
        this.pos = pos;
        return this;
    }

    /**
     * Moves the badge from its position.
     *
     * @param x the horizontal offset in pixels, -32 to 32
     * @param y the vertical offset in pixels, -32 to 32
     * @return this badge
     * @throws InvalidUiException if an offset is out of range
     */
    public Badge offset(int x, int y) {
        this.offsetX = Check.range(x, -32, 32, "badge.offset_x");
        this.offsetY = Check.range(y, -32, 32, "badge.offset_y");
        return this;
    }

    boolean styled() {
        return icon != null || bg != null || border != null || color != null || scale != null || textScale != null
                || iconSize != null || pad != null || shape != null || pos != null || offsetX != 0 || offsetY != 0;
    }

    JsonElement toJson(int maxChars, String at) {
        if (text != null) Check.text(text, maxChars, at + ".text");
        if (!styled()) {
            JsonElement plain = text.toJson();
            if (!plain.isJsonObject()) return plain;
            if (text.visibleLength(at) == 0) throw Check.fail(at, "a badge needs a text or an icon");
            JsonObject wrapped = new JsonObject();
            wrapped.add("text", plain);
            return wrapped;
        }
        if ((text == null || text.visibleLength(at) == 0) && icon == null) throw Check.fail(at, "a badge needs a text or an icon");
        JsonObject o = new JsonObject();
        if (text != null) o.add("text", text.toJson());
        if (icon != null) o.add("icon", icon.toJson());
        Json.str(o, "bg", bg);
        Json.str(o, "border", border);
        Json.str(o, "color", color);
        if (scale != null) Json.num(o, "scale", scale);
        if (textScale != null) Json.num(o, "text_scale", textScale);
        if (iconSize != null) o.addProperty("icon_size", iconSize);
        if (pad != null) o.addProperty("pad", pad);
        if (shape != null) o.addProperty("shape", Check.lower(shape));
        if (pos != null) o.addProperty("pos", Check.lower(pos));
        if (offsetX != 0) o.addProperty("offset_x", offsetX);
        if (offsetY != 0) o.addProperty("offset_y", offsetY);
        return o;
    }
}
