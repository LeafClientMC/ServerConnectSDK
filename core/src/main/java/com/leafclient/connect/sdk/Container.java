package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * An element that holds other elements: a column, row, grid or layer. Create it with {@link Ui#col},
 * {@link Ui#row}, {@link Ui#grid} or {@link Ui#layer}. It can also draw a background, border and accent stripe.
 */
public final class Container extends Element<Container> {

    /** Side of the accent stripe. */
    public enum Side {
        /** Top edge. */
        TOP,
        /** Bottom edge. */
        BOTTOM,
        /** Left edge. */
        LEFT,
        /** Right edge. */
        RIGHT
    }

    /** Gap in pixels between children the client uses when none is set. */
    public static final int DEFAULT_GAP = 4;
    /** Number of grid columns the client uses when none is set. */
    public static final int DEFAULT_GRID_COLUMNS = 3;

    private final String type;
    private final List<Element<?>> children = new ArrayList<Element<?>>();
    private Integer gap;
    private Justify justify;
    private boolean wrap;
    private Integer cols;
    private int[] cell;
    private String bg;
    private String bg2;
    private String border;
    private Integer borderWidth;
    private Integer radius;
    private Side accentSide;
    private String accentColor;
    private Integer accentWidth;

    Container(String type) {
        this.type = type;
    }

    @Override
    String type() {
        return type;
    }

    /**
     * Adds children at the end.
     *
     * @param elements the children
     * @return this container
     * @throws InvalidUiException if a child is null
     */
    public Container add(Element<?>... elements) {
        for (Element<?> e : elements) children.add(Check.notNull(e, type + ".children"));
        return this;
    }

    /**
     * Adds children at the end.
     *
     * @param elements the children
     * @return this container
     * @throws InvalidUiException if a child is null
     */
    public Container addAll(Collection<? extends Element<?>> elements) {
        for (Element<?> e : elements) children.add(Check.notNull(e, type + ".children"));
        return this;
    }

    /**
     * Sets the gap between children.
     *
     * @param pixels the gap, 0 to 64; the default is {@link #DEFAULT_GAP}
     * @return this container
     * @throws InvalidUiException if the gap is out of range
     */
    public Container gap(int pixels) {
        this.gap = Check.range(pixels, 0, 64, type + ".gap");
        return this;
    }

    /**
     * Sets how a row or column places its children on the main axis when they leave free space.
     *
     * @param justify the placement, or null for the client default
     * @return this container
     */
    public Container justify(Justify justify) {
        this.justify = justify;
        return this;
    }

    /**
     * Lets a row continue on a new line when its children do not fit its width.
     *
     * @return this container
     * @throws InvalidUiException if this container is not a row
     */
    public Container wrap() {
        if (!"row".equals(type)) throw new InvalidUiException(type + ".wrap: only a row wraps");
        this.wrap = true;
        return this;
    }

    /**
     * Sets the number of grid columns. Children fill the grid row by row.
     *
     * @param columns 1 to 16; the default is {@link #DEFAULT_GRID_COLUMNS}
     * @return this container
     * @throws InvalidUiException if this container is not a grid or the number is out of range
     */
    public Container columns(int columns) {
        requireGrid("cols");
        this.cols = Check.range(columns, 1, 16, "grid.cols");
        return this;
    }

    /**
     * Sets a fixed size for every grid cell.
     *
     * @param width the cell width, 1 to {@link Limits#PANEL_MAX_SIZE}
     * @param height the cell height, 1 to {@link Limits#PANEL_MAX_SIZE}
     * @return this container
     * @throws InvalidUiException if this container is not a grid or a value is out of range
     */
    public Container cell(int width, int height) {
        requireGrid("cell");
        this.cell = new int[]{Check.range(width, 1, Limits.PANEL_MAX_SIZE, "grid.cell"), Check.range(height, 1, Limits.PANEL_MAX_SIZE, "grid.cell")};
        return this;
    }

    /**
     * Sets the background color. With {@link #bg2(String)} it is the top color of a vertical gradient.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this container
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Container bg(String color) {
        this.bg = Check.color(color, type + ".style.bg");
        return this;
    }

    /**
     * Sets the bottom color of a vertical background gradient that starts at {@link #bg(String)}.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this container
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Container bg2(String color) {
        this.bg2 = Check.color(color, type + ".style.bg2");
        return this;
    }

    /**
     * Sets the border color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this container
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Container border(String color) {
        this.border = Check.color(color, type + ".style.border");
        return this;
    }

    /**
     * Sets the border width.
     *
     * @param pixels the width, 0 to 8
     * @return this container
     * @throws InvalidUiException if the width is out of range
     */
    public Container borderWidth(int pixels) {
        this.borderWidth = Check.range(pixels, 0, 8, type + ".style.border_width");
        return this;
    }

    /**
     * Sets the corner radius of the background and border.
     *
     * @param pixels the radius, 0 to 64
     * @return this container
     * @throws InvalidUiException if the radius is out of range
     */
    public Container radius(int pixels) {
        this.radius = Check.range(pixels, 0, 64, type + ".style.radius");
        return this;
    }

    /**
     * Draws an accent stripe on one side.
     *
     * @param side the side
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this container
     * @throws InvalidUiException if the side is null or the color is not valid
     */
    public Container accent(Side side, String color) {
        this.accentSide = Check.notNull(side, type + ".style.accent.side");
        this.accentColor = Check.color(color, type + ".style.accent.color");
        return this;
    }

    /**
     * Draws an accent stripe on one side with a given width.
     *
     * @param side the side
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @param width the stripe width in pixels, 1 to 16
     * @return this container
     * @throws InvalidUiException if the side is null, the color is not valid or the width is out of range
     */
    public Container accent(Side side, String color, int width) {
        accent(side, color);
        this.accentWidth = Check.range(width, 1, 16, type + ".style.accent.width");
        return this;
    }

    private void requireGrid(String field) {
        if (!"grid".equals(type)) throw new InvalidUiException(type + "." + field + ": only a grid has " + field);
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        if (gap != null) o.addProperty("gap", gap);
        if (justify != null) o.addProperty("justify", Check.lower(justify));
        if (wrap) o.addProperty("wrap", true);
        if (cols != null) o.addProperty("cols", cols);
        if (cell != null) o.add("cell", Json.numbers(cell[0], cell[1]));
        JsonObject style = new JsonObject();
        Json.str(style, "bg", bg);
        Json.str(style, "bg2", bg2);
        Json.str(style, "border", border);
        if (borderWidth != null) style.addProperty("border_width", borderWidth);
        if (radius != null) style.addProperty("radius", radius);
        if (accentSide != null) {
            JsonObject accent = new JsonObject();
            accent.addProperty("side", Check.lower(accentSide));
            accent.addProperty("color", accentColor);
            if (accentWidth != null) accent.addProperty("width", accentWidth);
            style.add("accent", accent);
        }
        if (!style.entrySet().isEmpty()) o.add("style", style);
        Check.count(children.size(), Limits.ELEMENTS_PER_PANEL, enc.at(path + ".children"), "children");
        JsonArray list = new JsonArray();
        for (int i = 0; i < children.size(); i++) {
            list.add(children.get(i).encode(enc, path + ".children[" + i + "]", depth + 1));
        }
        if (list.size() > 0) o.add("children", list);
    }
}
