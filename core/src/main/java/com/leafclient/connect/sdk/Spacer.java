package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * Empty space in a layout. Create it with {@link Ui#space()} or {@link Ui#space(int, int)} and size it with the
 * {@link Element} methods.
 */
public final class Spacer extends Element<Spacer> {

    Spacer() {}

    @Override
    String type() {
        return "space";
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
    }
}
