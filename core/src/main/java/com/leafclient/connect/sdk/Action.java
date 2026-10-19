package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

/**
 * Something the client does when the player presses a button, toast button or waypoint. A button runs its actions
 * in order, at most {@link Limits#ACTIONS}, with at most one of {@link #command}, {@link #url} and {@link #copy}.
 * A button without actions sends the click to the server, the same as {@link #send()}. Command and url actions,
 * and any action with {@link #confirm(String)}, wait for the player to accept a confirm dialog.
 */
public final class Action {

    /** A Leaf Client page that {@link #page(Page)} opens. */
    public enum Page {
        /** The waypoints page. */
        WAYPOINTS,
        /** The friends page. */
        FRIENDS,
        /** The mods page. */
        MODS,
        /** The cosmetics page. */
        COSMETICS,
        /** The settings page. */
        SETTINGS
    }

    private final String kind;
    private final JsonObject fields = new JsonObject();
    private Text confirmTitle;
    private Text confirmBody;
    private boolean confirm;
    private Text waypointName;

    private Action(String kind) {
        this.kind = kind;
    }

    /**
     * Sends the click to the server, where it runs the button handler and the {@link LeafConnect#onClick} listeners.
     *
     * @return the action
     */
    public static Action send() {
        return new Action("send");
    }

    /**
     * Sends the click to the server with data, readable with {@link Click#data()}.
     *
     * @param data at most 256 characters
     * @return the action
     * @throws InvalidUiException if the data is null or too long
     */
    public static Action send(String data) {
        Action a = new Action("send");
        a.fields.addProperty("data", Check.chars(data, 256, "action.data"));
        return a;
    }

    /**
     * Runs a command as the player, after the player accepts a confirm dialog.
     *
     * @param command one line that starts with {@code /} and has a name, at most 256 characters
     * @return the action
     * @throws InvalidUiException if the command is null, too long, has no name or has more than one line
     */
    public static Action command(String command) {
        Check.chars(command, 256, "action.cmd");
        if (!command.startsWith("/") || command.length() < 2) throw Check.fail("action.cmd", "a command starts with / and has a name");
        Check.oneLine(command, "action.cmd");
        Action a = new Action("command");
        a.fields.addProperty("cmd", command);
        return a;
    }

    /**
     * Opens a web link in the player's browser, after the player accepts a confirm dialog.
     *
     * @param url an {@code https://} or {@code http://} link with a host, at most 512 characters, without spaces,
     *            control characters, backslashes, quotes or angle brackets
     * @return the action
     * @throws InvalidUiException if the link is null, too long or not valid
     */
    public static Action url(String url) {
        Check.chars(url, 512, "action.url");
        if (!isWebUrl(url)) throw Check.fail("action.url", "only https:// and http:// links with a host");
        Action a = new Action("url");
        a.fields.addProperty("url", url);
        return a;
    }

    /**
     * Copies text to the player's clipboard and shows a toast that says so.
     *
     * @param text at most 1024 characters
     * @return the action
     * @throws InvalidUiException if the text is null or too long
     */
    public static Action copy(String text) {
        Action a = new Action("copy");
        a.fields.addProperty("text", Check.chars(text, 1024, "action.text"));
        return a;
    }

    /**
     * Puts text in the player's chat box without sending it.
     *
     * @param chatText one line, at most 256 characters
     * @return the action
     * @throws InvalidUiException if the text is null, too long or has more than one line
     */
    public static Action suggest(String chatText) {
        Action a = new Action("suggest");
        a.fields.addProperty("text", Check.oneLine(Check.chars(chatText, 256, "action.text"), "action.text"));
        return a;
    }

    /**
     * Opens the server menu.
     *
     * @return the action
     */
    public static Action openMenu() {
        return new Action("open");
    }

    /**
     * Opens the server menu on a tab.
     *
     * @param tab the tab id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the action
     * @throws InvalidUiException if the id is not valid
     */
    public static Action openMenu(String tab) {
        Action a = new Action("open");
        a.fields.addProperty("menu", Check.id(tab, "action.menu"));
        return a;
    }

    /**
     * Opens a front panel that was already sent.
     *
     * @param frontPanelId the panel id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the action
     * @throws InvalidUiException if the id is not valid
     */
    public static Action openPanel(String frontPanelId) {
        Action a = new Action("open");
        a.fields.addProperty("panel", Check.id(frontPanelId, "action.panel"));
        return a;
    }

    /**
     * Closes the panel the button is on: a front panel closes, a block or entity panel is hidden, and for other
     * places the menu closes.
     *
     * @return the action
     */
    public static Action close() {
        return new Action("close");
    }

    /**
     * Closes a front panel, or hides a block or entity panel, by id.
     *
     * @param panelId the panel id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the action
     * @throws InvalidUiException if the id is not valid
     */
    public static Action close(String panelId) {
        Action a = new Action("close");
        a.fields.addProperty("panel", Check.id(panelId, "action.panel"));
        return a;
    }

    /**
     * Opens a Leaf Client page.
     *
     * @param page the page
     * @return the action
     * @throws InvalidUiException if the page is null
     */
    public static Action page(Page page) {
        Action a = new Action("page");
        a.fields.addProperty("page", Check.lower(Check.notNull(page, "action.page")));
        return a;
    }

    /**
     * Adds a waypoint on the client. Style it with {@link #dim}, {@link #color}, {@link #icon} and {@link #save}.
     *
     * @param name legacy text with {@code &} or {@code §} codes, at most 48 visible characters
     * @param x the x coordinate, -30000000 to 30000000
     * @param y the y coordinate, -30000000 to 30000000
     * @param z the z coordinate, -30000000 to 30000000
     * @return the action
     * @throws InvalidUiException if the name is null or too long, or a coordinate is out of range
     */
    public static Action waypoint(String name, double x, double y, double z) {
        return waypoint(Text.of(name), x, y, z);
    }

    /**
     * Adds a waypoint on the client. Style it with {@link #dim}, {@link #color}, {@link #icon} and {@link #save}.
     *
     * @param name the name, at most 48 visible characters
     * @param x the x coordinate, -30000000 to 30000000
     * @param y the y coordinate, -30000000 to 30000000
     * @param z the z coordinate, -30000000 to 30000000
     * @return the action
     * @throws InvalidUiException if the name is null or too long, or a coordinate is out of range
     */
    public static Action waypoint(Text name, double x, double y, double z) {
        Action a = new Action("waypoint");
        a.waypointName = Check.text(name, 48, "action.name");
        a.fields.add("pos", Json.numbers(coordinate(x), coordinate(y), coordinate(z)));
        return a;
    }

    /**
     * Sets the dimension of a waypoint action.
     *
     * @param dimension the dimension id, at most 64 characters
     * @return this action
     * @throws InvalidUiException if this is not a waypoint action, or the dimension is null or too long
     */
    public Action dim(String dimension) {
        requireWaypoint("dim");
        fields.addProperty("dim", Check.chars(dimension, 64, "action.dim"));
        return this;
    }

    /**
     * Sets the color of a waypoint action.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this action
     * @throws InvalidUiException if this is not a waypoint action or the color is not valid
     */
    public Action color(String color) {
        requireWaypoint("color");
        fields.addProperty("color", Check.color(color, "action.color"));
        return this;
    }

    /**
     * Sets the icon of a waypoint action.
     *
     * @param icon the icon
     * @return this action
     * @throws InvalidUiException if this is not a waypoint action or the icon is null
     */
    public Action icon(Icon icon) {
        requireWaypoint("icon");
        fields.add("icon", Check.notNull(icon, "action.icon").toJson());
        return this;
    }

    /**
     * Offers the player a Save button so they can keep the waypoint of this action.
     *
     * @return this action
     * @throws InvalidUiException if this is not a waypoint action
     */
    public Action save() {
        requireWaypoint("save");
        fields.addProperty("save", true);
        return this;
    }

    /**
     * Asks the player to confirm before this action runs. The later actions wait for the answer.
     *
     * @param title legacy text, at most 96 visible characters
     * @return this action
     * @throws InvalidUiException if the title is null or too long
     */
    public Action confirm(String title) {
        return confirm(Text.of(title), null);
    }

    /**
     * Asks the player to confirm before this action runs. The later actions wait for the answer.
     *
     * @param title legacy text, at most 96 visible characters
     * @param body legacy text, at most 256 visible characters, or null
     * @return this action
     * @throws InvalidUiException if the title is null or a text is too long
     */
    public Action confirm(String title, String body) {
        return confirm(Text.of(title), Text.from(body));
    }

    /**
     * Asks the player to confirm before this action runs. The later actions wait for the answer. Leaf Client labels
     * the buttons itself: "Run command", "Open link", "Copy" or "Confirm", and "Cancel".
     *
     * @param title the title, at most 96 visible characters, or null
     * @param body the body, at most 256 visible characters, or null
     * @return this action
     * @throws InvalidUiException if a text is too long
     */
    public Action confirm(Text title, Text body) {
        this.confirm = true;
        this.confirmTitle = title == null ? null : Check.text(title, 96, "action.confirm.title");
        this.confirmBody = body == null ? null : Check.text(body, 256, "action.confirm.body");
        return this;
    }

    /**
     * Asks the player to confirm before this action runs.
     *
     * @param title legacy text, at most 96 visible characters
     * @param body legacy text, at most 256 visible characters, or null
     * @param yes ignored: Leaf Client labels the buttons itself
     * @param no ignored: Leaf Client labels the buttons itself
     * @return this action
     * @throws InvalidUiException if the title is null or a text is too long
     * @deprecated the client no longer shows server button labels; use {@link #confirm(String, String)}
     */
    @Deprecated
    public Action confirm(String title, String body, String yes, String no) {
        return confirm(title, body);
    }

    /**
     * Asks the player to confirm before this action runs.
     *
     * @param title the title, at most 96 visible characters, or null
     * @param body the body, at most 256 visible characters, or null
     * @param yes ignored: Leaf Client labels the buttons itself
     * @param no ignored: Leaf Client labels the buttons itself
     * @return this action
     * @throws InvalidUiException if a text is too long
     * @deprecated the client no longer shows server button labels; use {@link #confirm(Text, Text)}
     */
    @Deprecated
    public Action confirm(Text title, Text body, String yes, String no) {
        return confirm(title, body);
    }

    boolean sends() {
        return "send".equals(kind);
    }

    boolean delaysClick() {
        return confirm || outward();
    }

    boolean outward() {
        return "command".equals(kind) || "url".equals(kind) || "copy".equals(kind);
    }

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("do", kind);
        if (waypointName != null) o.add("name", waypointName.toJson());
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : fields.entrySet()) o.add(e.getKey(), Json.copy(e.getValue()));
        if (confirm) {
            JsonObject c = new JsonObject();
            if (confirmTitle != null) c.add("title", confirmTitle.toJson());
            if (confirmBody != null) c.add("body", confirmBody.toJson());
            o.add("confirm", c);
        }
        return o;
    }

    static JsonArray list(List<Action> actions, String at) {
        Check.count(actions.size(), Limits.ACTIONS, at, "actions");
        JsonArray a = new JsonArray();
        int outward = 0;
        for (int i = 0; i < actions.size(); i++) {
            Action action = Check.notNull(actions.get(i), at + "[" + i + "]");
            if (action.outward() && ++outward > 1) throw Check.fail(at + "[" + i + "]", "at most one of command, url and copy per button");
            a.add(action.toJson());
        }
        return a;
    }

    static boolean onePressOneClick(List<Action> actions) {
        if (actions == null) return true;
        int sends = 0;
        for (Action a : actions) {
            if (a.delaysClick()) return false;
            if (a.sends()) sends++;
        }
        return sends == 1;
    }

    static boolean anySends(List<Action> actions) {
        for (Action a : actions) if (a.sends()) return true;
        return false;
    }

    private void requireWaypoint(String field) {
        if (!"waypoint".equals(kind)) throw new InvalidUiException("action." + field + ": only a waypoint action has " + field);
    }

    private static double coordinate(double v) {
        return Check.range(v, -30000000, 30000000, "action.pos");
    }

    static boolean isWebUrl(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return false;
        String rest = url.substring(url.indexOf("://") + 3);
        if (rest.isEmpty() || rest.charAt(0) == '/' || rest.charAt(0) == '@') return false;
        for (int i = 0; i < url.length(); i++) {
            char ch = url.charAt(i);
            if (ch <= ' ' || ch == '\\' || ch == '"' || ch == '<' || ch == '>' || ch == 0x7F) return false;
        }
        return true;
    }
}
