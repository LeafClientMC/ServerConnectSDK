package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * An on/off input, drawn as a switch ({@link Ui#toggle(String)}) or a checkbox ({@link Ui#checkbox(String)}).
 * Read its value in a click handler with {@link FormValues#isOn(String)}. Inputs are only allowed in menu panels.
 */
public final class Toggle extends InputElement<Toggle> {

    private final boolean checkbox;
    private boolean value;

    Toggle(String id, boolean checkbox) {
        super(id);
        this.checkbox = checkbox;
    }

    @Override
    String type() {
        return checkbox ? "checkbox" : "toggle";
    }

    /**
     * Sets the starting state. The default is off.
     *
     * @param on true to start switched on
     * @return this input
     */
    public Toggle value(boolean on) {
        this.value = on;
        return this;
    }

    @Override
    Encoder.InputSpec encodeInput(JsonObject o, Encoder enc, String path) {
        if (value) o.addProperty("value", true);
        return new Encoder.InputSpec(id, Encoder.InputKind.TOGGLE, 0, null, 0, 0, 0, value);
    }
}
