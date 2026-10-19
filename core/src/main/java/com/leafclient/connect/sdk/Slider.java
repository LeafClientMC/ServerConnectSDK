package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * A number slider. Create it with {@link Ui#slider(String, double, double)} and read its value in a click handler with
 * {@link FormValues#number(String)} or {@link FormValues#integer(String)}. Inputs are only allowed in menu panels.
 * Minimum and maximum are -1e9 to 1e9, the maximum is above the minimum, and the range holds at most 100000 steps.
 */
public final class Slider extends InputElement<Slider> {

    private final double min;
    private final double max;
    private Double step;
    private Double value;

    Slider(String id, double min, double max) {
        super(id);
        this.min = Check.range(min, -1e9, 1e9, "slider.min");
        this.max = Check.range(max, -1e9, 1e9, "slider.max");
        if (!(max > min)) throw Check.fail("slider.max", "must be above min");
    }

    @Override
    String type() {
        return "slider";
    }

    /**
     * Sets the step between values. The default is 1. A step of 0 allows any value in the range.
     *
     * @param step the step, 0 to 1e9
     * @return this input
     * @throws InvalidUiException if the step is out of range or gives more than 100000 steps
     */
    public Slider step(double step) {
        this.step = Check.range(step, 0, 1e9, "slider.step");
        if (step > 0 && (max - min) / step > 100000) throw Check.fail("slider.step", "too small for this range, at most 100000 steps");
        return this;
    }

    /**
     * Sets the starting value. The default is the minimum.
     *
     * @param value the value, between the minimum and the maximum
     * @return this input
     * @throws InvalidUiException if the value is out of range
     */
    public Slider value(double value) {
        this.value = Check.range(value, min, max, "slider.value");
        return this;
    }

    static double snap(double min, double max, double step, double v) {
        double clamped = Math.max(min, Math.min(max, v));
        double snapped = step > 0 ? min + Math.round((clamped - min) / step) * step : clamped;
        snapped = Math.max(min, Math.min(max, snapped));
        return Math.round(snapped * 1e6) / 1e6;
    }

    @Override
    Encoder.InputSpec encodeInput(JsonObject o, Encoder enc, String path) {
        double s = step == null ? 1 : step;
        if (s > 0 && (max - min) / s > 100000) throw enc.fail(path + ".step", "too small for this range, at most 100000 steps");
        Json.num(o, "min", min);
        Json.num(o, "max", max);
        if (step != null) Json.num(o, "step", step);
        if (value != null) Json.num(o, "value", value);
        double initial = snap(min, max, s, value == null ? min : value);
        return new Encoder.InputSpec(id, Encoder.InputKind.SLIDER, 0, null, min, max, s, initial);
    }
}
