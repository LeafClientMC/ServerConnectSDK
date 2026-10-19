package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * An {@link Icon} shown as a layout element. Create it with {@link Ui#icon(Icon)}.
 */
public final class IconElement extends Element<IconElement> {

    private final Icon icon;
    private Integer size;

    IconElement(Icon icon) {
        this.icon = Check.notNull(icon, "icon.icon");
    }

    @Override
    String type() {
        return "icon";
    }

    /**
     * Sets the icon size.
     *
     * @param pixels the size, 4 to 128
     * @return this element
     * @throws InvalidUiException if the size is out of range
     */
    public IconElement size(int pixels) {
        this.size = Check.range(pixels, 4, 128, "icon.size");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        o.add("icon", icon.toJson());
        if (size != null) o.addProperty("size", size);
    }
}
