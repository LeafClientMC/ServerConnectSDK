package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * A progress bar. Create it with {@link Ui#bar(double, double)} or {@link Ui#bar(String, String)}.
 * Value and maximum are numbers or value placeholders (see {@link Values}), so the bar can follow a value or a timer.
 * When they are not set, the client uses 0 and 100. On a bar, {@link #height(int)} is the thickness, 1 to 32.
 */
public final class Bar extends Element<Bar> {

    /** Thickness in pixels the client uses when no thickness is set. */
    public static final int DEFAULT_THICKNESS = 6;

    private JsonElement value;
    private JsonElement max;
    private String color;
    private String bg;
    private Text label;

    Bar() {}

    @Override
    String type() {
        return "bar";
    }

    /**
     * Sets the current value.
     *
     * @param value the value, -1e15 to 1e15
     * @return this element
     * @throws InvalidUiException if the value is out of range or not finite
     */
    public Bar value(double value) {
        this.value = Json.number(Check.range(value, -1e15, 1e15, "bar.value"));
        return this;
    }

    /**
     * Sets the current value to a placeholder the client resolves, such as {@code {v:hp}}.
     *
     * @param placeholder the placeholder, at most 64 characters
     * @return this element
     * @throws InvalidUiException if the placeholder is null or too long
     */
    public Bar value(String placeholder) {
        this.value = new JsonPrimitive(Check.chars(placeholder, 64, "bar.value"));
        return this;
    }

    /**
     * Sets the maximum.
     *
     * @param max the maximum, -1e15 to 1e15
     * @return this element
     * @throws InvalidUiException if the maximum is out of range or not finite
     */
    public Bar max(double max) {
        this.max = Json.number(Check.range(max, -1e15, 1e15, "bar.max"));
        return this;
    }

    /**
     * Sets the maximum to a placeholder the client resolves, such as {@code {v:max_hp}}.
     *
     * @param placeholder the placeholder, at most 64 characters
     * @return this element
     * @throws InvalidUiException if the placeholder is null or too long
     */
    public Bar max(String placeholder) {
        this.max = new JsonPrimitive(Check.chars(placeholder, 64, "bar.max"));
        return this;
    }

    /**
     * Sets the fill color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Bar color(String color) {
        this.color = Check.color(color, "bar.color");
        return this;
    }

    /**
     * Sets the background color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Bar bg(String color) {
        this.bg = Check.color(color, "bar.bg");
        return this;
    }

    /**
     * Sets a label for the bar.
     *
     * @param label legacy text with {@code &} or {@code §} codes, at most 128 visible characters
     * @return this element
     * @throws InvalidUiException if the label is null
     */
    public Bar label(String label) {
        return label(Text.of(label));
    }

    /**
     * Sets a label for the bar.
     *
     * @param label the label, at most 128 visible characters, or null for none
     * @return this element
     */
    public Bar label(Text label) {
        this.label = label;
        return this;
    }

    /**
     * Sets the thickness. It is stored as the element height.
     *
     * @param pixels the thickness, 1 to 32
     * @return this element
     * @throws InvalidUiException if the thickness is out of range
     */
    public Bar thickness(int pixels) {
        this.h = Check.range(pixels, 1, 32, "bar.h");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        if (h != null && (h < 1 || h > 32)) throw enc.fail(path + ".h", "a bar's h is its thickness, 1 to 32");
        if (value != null) o.add("value", value);
        if (max != null) o.add("max", max);
        Json.str(o, "color", color);
        Json.str(o, "bg", bg);
        if (label != null) o.add("label", Check.text(label, 128, enc.at(path + ".label")).toJson());
    }
}
