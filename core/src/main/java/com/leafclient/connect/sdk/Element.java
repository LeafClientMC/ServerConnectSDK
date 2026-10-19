package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * Base class of every panel element. Create elements with the {@link Ui} factory methods and put them in a
 * {@link Panel}. A panel holds at most {@link Limits#ELEMENTS_PER_PANEL} elements nested at most
 * {@link Limits#ELEMENT_DEPTH} deep, and element ids are unique inside one panel.
 *
 * @param <T> the concrete element type, returned by the builder methods
 */
public abstract class Element<T extends Element<T>> {

    String id;
    Integer w;
    Integer h;
    Double grow;
    Integer pad;
    Align align;
    int[] at;
    Condition when;

    Element() {}

    abstract String type();

    abstract void encodeOwn(JsonObject o, Encoder enc, String path, int depth);

    boolean requiresId() {
        return false;
    }

    @SuppressWarnings("unchecked")
    final T self() {
        return (T) this;
    }

    /**
     * Sets the element id. Buttons and inputs need one. {@link Patch} uses it to change the element later.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}, unique in the panel
     * @return this element
     * @throws InvalidUiException if the id is missing or not valid
     */
    public T id(String id) {
        this.id = Check.id(id, type() + ".id");
        return self();
    }

    /**
     * Sets the width.
     *
     * @param pixels the width, 0 to {@link Limits#PANEL_MAX_SIZE}, or -1 for the automatic width, which is the default
     * @return this element
     * @throws InvalidUiException if the width is out of range
     */
    public T width(int pixels) {
        this.w = Check.range(pixels, -1, Limits.PANEL_MAX_SIZE, type() + ".w");
        return self();
    }

    /**
     * Sets the height.
     *
     * @param pixels the height, 0 to {@link Limits#PANEL_MAX_SIZE}, or -1 for the automatic height, which is the default
     * @return this element
     * @throws InvalidUiException if the height is out of range
     */
    public T height(int pixels) {
        this.h = Check.range(pixels, -1, Limits.PANEL_MAX_SIZE, type() + ".h");
        return self();
    }

    /**
     * Sets width and height.
     *
     * @param width the width, -1 to {@link Limits#PANEL_MAX_SIZE}
     * @param height the height, -1 to {@link Limits#PANEL_MAX_SIZE}
     * @return this element
     * @throws InvalidUiException if a value is out of range
     */
    public T size(int width, int height) {
        return width(width).height(height);
    }

    /**
     * Sets how much of the free space in its row or column this element takes. Free space is split between the
     * children in proportion to their weights. The default is 0.
     *
     * @param weight the weight, 0 to 16
     * @return this element
     * @throws InvalidUiException if the weight is out of range or not finite
     */
    public T grow(double weight) {
        this.grow = Check.range(weight, 0, 16, type() + ".grow");
        return self();
    }

    /**
     * Sets the inner padding.
     *
     * @param pixels the padding, 0 to 64
     * @return this element
     * @throws InvalidUiException if the padding is out of range
     */
    public T pad(int pixels) {
        this.pad = Check.range(pixels, 0, 64, type() + ".pad");
        return self();
    }

    /**
     * Sets the alignment. On a row or column it places the children on the cross axis.
     *
     * @param align the alignment, or null for the client default {@link Align#START}
     * @return this element
     */
    public T align(Align align) {
        this.align = align;
        return self();
    }

    /**
     * Sets the position of this element inside a {@link Ui#layer layer}. Other containers ignore it.
     *
     * @param x the left offset, 0 to {@link Limits#PANEL_MAX_SIZE}
     * @param y the top offset, 0 to {@link Limits#PANEL_MAX_SIZE}
     * @return this element
     * @throws InvalidUiException if a value is out of range
     */
    public T at(int x, int y) {
        this.at = new int[]{Check.range(x, 0, Limits.PANEL_MAX_SIZE, type() + ".at"), Check.range(y, 0, Limits.PANEL_MAX_SIZE, type() + ".at")};
        return self();
    }

    /**
     * Shows this element only while a condition is true on the client.
     *
     * @param condition the condition, or null to always show it
     * @return this element
     */
    public T when(Condition condition) {
        this.when = condition;
        return self();
    }

    JsonObject encode(Encoder enc, String path, int depth) {
        enc.depth(depth, path);
        enc.element(path);
        JsonObject o = new JsonObject();
        o.addProperty("type", type());
        if (id != null) {
            o.addProperty("id", id);
            enc.claimId(id, type(), path);
        } else if (requiresId()) {
            throw enc.fail(path + ".id", "a " + type() + " needs an id");
        }
        if (w != null) o.addProperty("w", w);
        if (h != null) o.addProperty("h", h);
        if (grow != null) Json.num(o, "grow", grow);
        if (pad != null) o.addProperty("pad", pad);
        if (align != null) o.addProperty("align", Check.lower(align));
        if (at != null) o.add("at", Json.numbers(at[0], at[1]));
        if (when != null) o.add("when", when.toJson(enc.at(path + ".when")));
        encodeOwn(o, enc, path, depth);
        return o;
    }
}
