package com.leafclient.connect.sdk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Factory methods for every panel element. Legacy text arguments accept {@code &} and {@code §} color codes.
 */
public final class Ui {

    private Ui() {}

    /**
     * Creates a column: children stacked top to bottom.
     *
     * @param children the children
     * @return the container
     * @throws InvalidUiException if a child is null
     */
    public static Container col(Element<?>... children) {
        return new Container("col").add(children);
    }

    /**
     * Creates a row: children side by side, left to right.
     *
     * @param children the children
     * @return the container
     * @throws InvalidUiException if a child is null
     */
    public static Container row(Element<?>... children) {
        return new Container("row").add(children);
    }

    /**
     * Creates a grid with a fixed number of columns.
     *
     * @param columns the number of columns, 1 to 16
     * @param children the children, filled row by row
     * @return the container
     * @throws InvalidUiException if the column count is out of range or a child is null
     */
    public static Container grid(int columns, Element<?>... children) {
        return new Container("grid").columns(columns).add(children);
    }

    /**
     * Creates a layer: children placed at the positions set with {@link Element#at(int, int)}, on top of each other.
     *
     * @param children the children
     * @return the container
     * @throws InvalidUiException if a child is null
     */
    public static Container layer(Element<?>... children) {
        return new Container("layer").add(children);
    }

    /**
     * Creates a text element.
     *
     * @param text legacy text, at most {@link Limits#TEXT_CHARS} visible characters
     * @return the element
     * @throws InvalidUiException if the text is null
     */
    public static TextElement text(String text) {
        return new TextElement(Text.of(text));
    }

    /**
     * Creates a text element.
     *
     * @param text the text, at most {@link Limits#TEXT_CHARS} visible characters
     * @return the element
     * @throws InvalidUiException if the text is null
     */
    public static TextElement text(Text text) {
        return new TextElement(text);
    }

    /**
     * Creates a button with a label.
     *
     * @param id the button id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param label legacy text, at most {@link Limits#TEXT_CHARS} visible characters
     * @return the button
     * @throws InvalidUiException if the id is not valid or the label is null
     */
    public static Button button(String id, String label) {
        return new Button(id).label(label);
    }

    /**
     * Creates a button with a label.
     *
     * @param id the button id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param label the label, at most {@link Limits#TEXT_CHARS} visible characters
     * @return the button
     * @throws InvalidUiException if the id is not valid
     */
    public static Button button(String id, Text label) {
        return new Button(id).label(label);
    }

    /**
     * Creates a button without a label, for example an icon button.
     *
     * @param id the button id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the button
     * @throws InvalidUiException if the id is not valid
     */
    public static Button button(String id) {
        return new Button(id);
    }

    /**
     * Creates an icon element.
     *
     * @param icon the icon
     * @return the element
     * @throws InvalidUiException if the icon is null
     */
    public static IconElement icon(Icon icon) {
        return new IconElement(icon);
    }

    /**
     * Creates an image from a resource pack texture.
     *
     * @param texture a texture such as {@code myserver:textures/gui/banner.png}: a namespace, a path and {@code .png}
     * @return the element
     * @throws InvalidUiException if the texture is missing or not in that form
     */
    public static Image image(String texture) {
        return Image.texture(texture);
    }

    /**
     * Creates an image loaded from the web.
     *
     * @param httpsUrl an {@code https://} link, at most 512 characters
     * @return the element
     * @throws InvalidUiException if the link is missing, too long or not https
     */
    public static Image webImage(String httpsUrl) {
        return Image.web(httpsUrl);
    }

    /**
     * Creates a player model.
     *
     * @param player the player UUID
     * @return the element
     * @throws InvalidUiException if the UUID is null
     */
    public static PlayerModel player(UUID player) {
        return new PlayerModel(Check.notNull(player, "player.player").toString());
    }

    /**
     * Creates a player model.
     *
     * @param playerUuid the player UUID, with or without dashes
     * @return the element
     * @throws InvalidUiException if the text is not a UUID
     */
    public static PlayerModel player(String playerUuid) {
        return new PlayerModel(playerUuid);
    }

    /**
     * Creates a divider line.
     *
     * @return the element
     */
    public static Separator separator() {
        return new Separator();
    }

    /**
     * Creates empty space.
     *
     * @return the element
     */
    public static Spacer space() {
        return new Spacer();
    }

    /**
     * Creates empty space of a given size.
     *
     * @param width the width, -1 to {@link Limits#PANEL_MAX_SIZE}
     * @param height the height, -1 to {@link Limits#PANEL_MAX_SIZE}
     * @return the element
     * @throws InvalidUiException if a value is out of range
     */
    public static Spacer space(int width, int height) {
        return new Spacer().size(width, height);
    }

    /**
     * Creates a progress bar with fixed numbers.
     *
     * @param value the value, -1e15 to 1e15
     * @param max the maximum, -1e15 to 1e15
     * @return the element
     * @throws InvalidUiException if a value is out of range or not finite
     */
    public static Bar bar(double value, double max) {
        return new Bar().value(value).max(max);
    }

    /**
     * Creates a progress bar whose value and maximum are placeholders the client resolves (see {@link Values}).
     *
     * @param valuePlaceholder the value placeholder, such as {@code {v:hp}}, at most 64 characters
     * @param maxPlaceholder the maximum placeholder, at most 64 characters
     * @return the element
     * @throws InvalidUiException if a placeholder is null or too long
     */
    public static Bar bar(String valuePlaceholder, String maxPlaceholder) {
        return new Bar().value(valuePlaceholder).max(maxPlaceholder);
    }

    /**
     * Creates a list of text rows.
     *
     * @param rows legacy text rows, at most {@link Limits#LIST_ROWS}
     * @return the element
     * @throws InvalidUiException if a row is null or there are too many rows
     */
    public static ListElement list(String... rows) {
        List<Text> list = new ArrayList<Text>();
        for (String r : rows) list.add(Text.of(r));
        return new ListElement(list);
    }

    /**
     * Creates a list of text rows.
     *
     * @param rows the rows, at most {@link Limits#LIST_ROWS}
     * @return the element
     * @throws InvalidUiException if there are too many rows
     */
    public static ListElement list(Text... rows) {
        return new ListElement(new ArrayList<Text>(Arrays.asList(rows)));
    }

    /**
     * Creates a list of text rows.
     *
     * @param rows legacy text rows, at most {@link Limits#LIST_ROWS}
     * @return the element
     * @throws InvalidUiException if a row is null or there are too many rows
     */
    public static ListElement list(Collection<String> rows) {
        return list(rows.toArray(new String[0]));
    }

    /**
     * Creates a heads element.
     *
     * @param players the players, at most {@link Limits#HEADS}
     * @return the element
     * @throws InvalidUiException if a player is null or there are too many players
     */
    public static Heads heads(UUID... players) {
        List<String> list = new ArrayList<String>();
        for (UUID p : players) list.add(Check.notNull(p, "heads.players").toString());
        return new Heads(list);
    }

    /**
     * Creates a heads element.
     *
     * @param players the players, at most {@link Limits#HEADS}
     * @return the element
     * @throws InvalidUiException if a player is null or there are too many players
     */
    public static Heads heads(Collection<UUID> players) {
        return heads(players.toArray(new UUID[0]));
    }

    /**
     * Creates a scroll area.
     *
     * @param height the visible height, 16 to {@link Limits#PANEL_MAX_SIZE}
     * @param child the scrolled element; it cannot contain another scroll
     * @return the element
     * @throws InvalidUiException if the height is out of range or the child is null
     */
    public static Scroll scroll(int height, Element<?> child) {
        return new Scroll(height, child);
    }

    /**
     * Creates an empty leaderboard.
     *
     * @return the element
     */
    public static Leaderboard leaderboard() {
        return new Leaderboard();
    }

    /**
     * Creates a text input. Inputs are only allowed in menu panels.
     *
     * @param id the input id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the input
     * @throws InvalidUiException if the id is not valid
     */
    public static TextInput input(String id) {
        return new TextInput(id);
    }

    /**
     * Creates an on/off switch. Inputs are only allowed in menu panels.
     *
     * @param id the input id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the input
     * @throws InvalidUiException if the id is not valid
     */
    public static Toggle toggle(String id) {
        return new Toggle(id, false);
    }

    /**
     * Creates a checkbox. Inputs are only allowed in menu panels.
     *
     * @param id the input id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the input
     * @throws InvalidUiException if the id is not valid
     */
    public static Toggle checkbox(String id) {
        return new Toggle(id, true);
    }

    /**
     * Creates a choice whose option labels are their ids. Inputs are only allowed in menu panels.
     *
     * @param id the input id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param options the option ids, at most {@link Limits#CHOICE_OPTIONS}; add more with {@link Choice#option(String)}
     * @return the input
     * @throws InvalidUiException if an id is not valid or duplicated, or there are too many options
     */
    public static Choice choice(String id, String... options) {
        Choice c = new Choice(id);
        for (String o : options) c.option(o);
        return c;
    }

    /**
     * Creates a slider. Inputs are only allowed in menu panels.
     *
     * @param id the input id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param min the minimum, -1e9 to 1e9
     * @param max the maximum, -1e9 to 1e9, above the minimum
     * @return the input
     * @throws InvalidUiException if the id is not valid or a bound is out of range
     */
    public static Slider slider(String id, double min, double max) {
        return new Slider(id, min, max);
    }
}
