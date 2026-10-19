package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.List;

/**
 * A list of text rows. Create it with one of the {@code Ui.list} methods.
 * It holds at most {@link Limits#LIST_ROWS} rows, and each row shows at most 256 visible characters.
 */
public final class ListElement extends Element<ListElement> {

    private final List<Text> rows;
    private Integer visible;
    private String color;

    ListElement(List<Text> rows) {
        Check.count(rows.size(), Limits.LIST_ROWS, "list.rows", "rows");
        this.rows = rows;
    }

    @Override
    String type() {
        return "list";
    }

    /**
     * Sets how many rows are visible at once.
     *
     * @param rows the number of rows, 1 to 64
     * @return this element
     * @throws InvalidUiException if the number is out of range
     */
    public ListElement visibleRows(int rows) {
        this.visible = Check.range(rows, 1, 64, "list.visible");
        return this;
    }

    /**
     * Sets the color of the list.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this element
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public ListElement color(String color) {
        this.color = Check.color(color, "list.color");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        o.add("rows", Button.lines(rows, Limits.LIST_ROWS, enc.at(path + ".rows")));
        if (visible != null) o.addProperty("visible", visible);
        Json.str(o, "color", color);
    }
}
