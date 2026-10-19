package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;

/**
 * Base class of the form inputs: {@link TextInput}, {@link Toggle}, {@link Choice} and {@link Slider}.
 * An input needs an id. Inputs are only allowed in menu panels, and a panel holds at most
 * {@link Limits#INPUTS_PER_PANEL}. Their values arrive with every click on that panel as {@link Click#inputs()}.
 *
 * @param <T> the concrete input type, returned by the builder methods
 */
public abstract class InputElement<T extends InputElement<T>> extends Element<T> {

    private Text label;
    private boolean enabled = true;
    private List<Text> tooltip;

    InputElement(String id) {
        id(id);
    }

    @Override
    boolean requiresId() {
        return true;
    }

    /**
     * Sets the label shown with the input.
     *
     * @param label legacy text with {@code &} or {@code §} codes, at most 128 visible characters
     * @return this input
     * @throws InvalidUiException if the label is null
     */
    public T label(String label) {
        return label(Text.of(label));
    }

    /**
     * Sets the label shown with the input.
     *
     * @param label the label, at most 128 visible characters, or null for none
     * @return this input
     */
    public T label(Text label) {
        this.label = label;
        return self();
    }

    /**
     * Enables or disables the input. The default is enabled.
     *
     * @param enabled false to show the input disabled
     * @return this input
     */
    public T enabled(boolean enabled) {
        this.enabled = enabled;
        return self();
    }

    /**
     * Sets the hover tooltip.
     *
     * @param lines legacy text lines, at most {@link Limits#TOOLTIP_LINES} lines of 256 visible characters each
     * @return this input
     * @throws InvalidUiException if a line is null
     */
    public T tooltip(String... lines) {
        this.tooltip = Button.texts(lines);
        return self();
    }

    /**
     * Sets the hover tooltip.
     *
     * @param lines the lines, at most {@link Limits#TOOLTIP_LINES} lines of 256 visible characters each
     * @return this input
     */
    public T tooltip(Text... lines) {
        this.tooltip = Arrays.asList(lines.clone());
        return self();
    }

    abstract Encoder.InputSpec encodeInput(JsonObject o, Encoder enc, String path);

    @Override
    final void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        enc.input(path);
        if (label != null) o.add("label", Check.text(label, 128, enc.at(path + ".label")).toJson());
        if (!enabled) o.addProperty("enabled", false);
        if (tooltip != null) o.add("tooltip", Button.lines(tooltip, Limits.TOOLTIP_LINES, enc.at(path + ".tooltip")));
        enc.inputSpecs.put(id, encodeInput(o, enc, path));
    }
}
