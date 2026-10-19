package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * A block of text. Create it with {@link Ui#text(String)} or {@link Ui#text(Text)}.
 * The text shows at most {@link Limits#TEXT_CHARS} visible characters and can hold value placeholders (see {@link Values}).
 */
public final class TextElement extends Element<TextElement> {

    /** Preset text sizes, from smallest to largest. */
    public enum Size {
        /** Small. */
        S,
        /** Medium. */
        M,
        /** Large. */
        L,
        /** Extra large. */
        XL,
        /** Largest. */
        XXL
    }

    private final Text text;
    private Size size;
    private Double scale;
    private boolean shadow;
    private Integer maxLines;
    private String color;

    TextElement(Text text) {
        this.text = Check.notNull(text, "text.text");
    }

    @Override
    String type() {
        return "text";
    }

    /**
     * Sets a preset text size.
     *
     * @param size the size, or null for the client default
     * @return this element
     */
    public TextElement size(Size size) {
        this.size = size;
        return this;
    }

    /**
     * Sets a text scale factor.
     *
     * @param scale the scale, 0.5 to 8
     * @return this element
     * @throws InvalidUiException if the scale is out of range
     */
    public TextElement scale(double scale) {
        this.scale = Check.range(scale, 0.5, 8, "text.scale");
        return this;
    }

    /**
     * Draws the text with a shadow.
     *
     * @return this element
     */
    public TextElement shadow() {
        this.shadow = true;
        return this;
    }

    /**
     * Sets the most lines shown. Lines past it are not drawn.
     *
     * @param lines the maximum, 0 to 64; 0 means no maximum, which is the default
     * @return this element
     * @throws InvalidUiException if the number is out of range
     */
    public TextElement maxLines(int lines) {
        this.maxLines = Check.range(lines, 0, 64, "text.max_lines");
        return this;
    }

    /**
     * Sets the text color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public TextElement color(String color) {
        this.color = Check.color(color, "text.color");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        o.add("text", Check.text(text, Limits.TEXT_CHARS, enc.at(path + ".text")).toJson());
        if (size != null) o.addProperty("size", Check.lower(size));
        if (scale != null) Json.num(o, "scale", scale);
        if (shadow) o.addProperty("shadow", true);
        if (maxLines != null) o.addProperty("max_lines", maxLines);
        Json.str(o, "color", color);
    }
}
