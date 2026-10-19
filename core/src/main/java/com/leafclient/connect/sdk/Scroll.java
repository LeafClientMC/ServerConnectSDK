package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * A fixed height area that scrolls one child. Create it with {@link Ui#scroll(int, Element)}.
 * The height is 16 to {@link Limits#PANEL_MAX_SIZE} pixels, and a scroll cannot contain another scroll.
 */
public final class Scroll extends Element<Scroll> {

    private final Element<?> child;

    Scroll(int height, Element<?> child) {
        this.child = Check.notNull(child, "scroll.child");
        height(Check.range(height, 16, Limits.PANEL_MAX_SIZE, "scroll.h"));
    }

    @Override
    String type() {
        return "scroll";
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        if (h == null || h < 16) throw enc.fail(path + ".h", "a scroll needs a height of at least 16");
        if (enc.inScroll) throw enc.fail(path, "a scroll cannot contain another scroll");
        enc.inScroll = true;
        try {
            o.add("child", child.encode(enc, path + ".child", depth + 1));
        } finally {
            enc.inScroll = false;
        }
    }
}
