package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * A divider line. Create it with {@link Ui#separator()}. It fills the cross axis of its row or column.
 */
public final class Separator extends Element<Separator> {

    private Integer thickness;
    private String color;

    Separator() {}

    @Override
    String type() {
        return "sep";
    }

    /**
     * Sets the line thickness.
     *
     * @param pixels the thickness, 1 to 8
     * @return this element
     * @throws InvalidUiException if the thickness is out of range
     */
    public Separator thickness(int pixels) {
        this.thickness = Check.range(pixels, 1, 8, "sep.thickness");
        return this;
    }

    /**
     * Sets the line color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Separator color(String color) {
        this.color = Check.color(color, "sep.color");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        if (thickness != null) o.addProperty("thickness", thickness);
        Json.str(o, "color", color);
    }
}
