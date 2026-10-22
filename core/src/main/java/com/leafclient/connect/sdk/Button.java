package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * A clickable button. Create it with one of the {@code Ui.button} methods; it needs an id. A panel holds at most
 * {@link Limits#BUTTONS_PER_PANEL} buttons, and the client lays out a button at most {@link Limits#BUTTON_MAX_W} by
 * {@link Limits#BUTTON_MAX_H} pixels. A press runs the {@link #on actions}; without actions the client sends the click.
 * A click that reaches the server passes the session checks (see {@link Rejection.Reason}) before
 * {@link #onClick the handler} runs.
 */
public final class Button extends Element<Button> {

    /** Preset button sizes. */
    public enum Size {
        /** Extra small. */
        XS,
        /** Small. */
        S,
        /** Medium. */
        M,
        /** Large. */
        L,
        /** Wide. */
        WIDE,
        /** Tall. */
        TALL
    }

    /** Where the icon sits relative to the label. */
    public enum IconPos {
        /** Left of the label. */
        LEFT,
        /** Above the label. */
        TOP,
        /** Right of the label. */
        RIGHT,
        /** Only the icon is shown. */
        ONLY
    }

    /** How a running cooldown is shown on the button. */
    public enum CooldownShow {
        /** A sweep over the button. */
        SWEEP,
        /** The time left as text. */
        TEXT,
        /** Both the sweep and the text. */
        BOTH
    }

    /** Corner radius in pixels the client uses when none is set. */
    public static final int DEFAULT_RADIUS = 4;

    private Text label;
    private List<Object> labelParts;
    private Icon icon;
    private IconPos iconPos;
    private Shape shape;
    private Integer radius;
    private Double labelScale;
    private Size sizePreset;
    private final JsonObject style = new JsonObject();
    private List<Text> tooltip;
    private List<Text> disabledTooltip;
    private boolean enabled = true;
    private Badge badge;
    private String sound;
    private Integer key;
    private List<Action> actions;
    private Condition enabledWhen;
    private Long cooldownMs;
    private CooldownShow cooldownShow;
    private Long cooldownLeft;
    private Consumer<Click> handler;

    Button(String id) {
        id(id);
    }

    @Override
    String type() {
        return "button";
    }

    @Override
    boolean requiresId() {
        return true;
    }

    /**
     * Sets the label.
     *
     * @param label legacy text with {@code &} or {@code §} codes, at most {@link Limits#TEXT_CHARS} visible characters
     * @return this button
     * @throws InvalidUiException if the label is null
     */
    public Button label(String label) {
        return label(Text.of(label));
    }

    /**
     * Sets the label. It replaces label parts set with {@link #labelParts(Object...)}.
     *
     * @param label the label, at most {@link Limits#TEXT_CHARS} visible characters, or null for none
     * @return this button
     */
    public Button label(Text label) {
        this.label = label;
        this.labelParts = null;
        return this;
    }

    /**
     * Sets a label made of text and icons in a line. It replaces a label set with {@link #label(Text)}.
     * All text parts together show at most {@link Limits#TEXT_CHARS} visible characters.
     *
     * @param parts at most {@link Limits#LABEL_PARTS} parts, each a legacy String, a {@link Text} or an {@link Icon}
     * @return this button
     * @throws InvalidUiException if there are too many parts or a part has another type
     */
    public Button labelParts(Object... parts) {
        Check.count(parts.length, Limits.LABEL_PARTS, "button.label", "label parts");
        List<Object> list = new ArrayList<Object>();
        for (Object p : parts) {
            if (p instanceof String) list.add(Text.of((String) p));
            else if (p instanceof Text || p instanceof Icon) list.add(p);
            else throw new InvalidUiException("button.label: a label part is a String, Text or Icon");
        }
        this.labelParts = list;
        this.label = null;
        return this;
    }

    /**
     * Sets the icon.
     *
     * @param icon the icon, or null for none
     * @return this button
     */
    public Button icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Sets where the icon sits relative to the label.
     *
     * @param pos the position, or null for the client default
     * @return this button
     */
    public Button iconPos(IconPos pos) {
        this.iconPos = pos;
        return this;
    }

    /**
     * Sets the corner shape. The client default is {@link Shape#ROUND}.
     *
     * @param shape the shape, or null for the default
     * @return this button
     */
    public Button shape(Shape shape) {
        this.shape = shape;
        return this;
    }

    /**
     * Sets the corner radius.
     *
     * @param pixels the radius, 0 to 24; the default is {@link #DEFAULT_RADIUS}
     * @return this button
     * @throws InvalidUiException if the radius is out of range
     */
    public Button radius(int pixels) {
        this.radius = Check.range(pixels, 0, 24, "button.radius");
        return this;
    }

    /**
     * Sets the scale of the label text.
     *
     * @param scale the scale, 0.5 to 3
     * @return this button
     * @throws InvalidUiException if the scale is out of range or not finite
     */
    public Button labelScale(double scale) {
        this.labelScale = Check.range(scale, 0.5, 3, "button.label_scale");
        return this;
    }

    /**
     * Sets a preset size.
     *
     * @param preset the preset, or null for none
     * @return this button
     */
    public Button size(Size preset) {
        this.sizePreset = preset;
        return this;
    }

    /**
     * Sets the background color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button bg(String color) {
        return style("bg", color);
    }

    /**
     * Sets the background color while the pointer is over the button.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button bgHover(String color) {
        return style("bg_hover", color);
    }

    /**
     * Sets the background color while the button is pressed.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button bgPressed(String color) {
        return style("bg_pressed", color);
    }

    /**
     * Sets the background color while the button is disabled.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button bgDisabled(String color) {
        return style("bg_disabled", color);
    }

    /**
     * Sets the border color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button border(String color) {
        return style("border", color);
    }

    /**
     * Sets the border color while the pointer is over the button.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button borderHover(String color) {
        return style("border_hover", color);
    }

    /**
     * Sets the border width.
     *
     * @param pixels the width, 0 to 3
     * @return this button
     * @throws InvalidUiException if the width is out of range
     */
    public Button borderWidth(int pixels) {
        style.addProperty("border_width", Check.range(pixels, 0, 3, "button.style.border_width"));
        return this;
    }

    /**
     * Sets the label color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button textColor(String color) {
        return style("text", color);
    }

    /**
     * Sets the label color while the pointer is over the button.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button textHover(String color) {
        return style("text_hover", color);
    }

    /**
     * Sets the label color while the button is disabled.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button textDisabled(String color) {
        return style("text_disabled", color);
    }

    /**
     * Sets a glow color around the button.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this button
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Button glow(String color) {
        return style("glow", color);
    }

    /**
     * Sets the hover tooltip.
     *
     * @param lines legacy text lines, at most {@link Limits#TOOLTIP_LINES} lines of 256 visible characters each
     * @return this button
     * @throws InvalidUiException if a line is null
     */
    public Button tooltip(String... lines) {
        this.tooltip = texts(lines);
        return this;
    }

    /**
     * Sets the hover tooltip.
     *
     * @param lines the lines, at most {@link Limits#TOOLTIP_LINES} lines of 256 visible characters each
     * @return this button
     */
    public Button tooltip(Text... lines) {
        this.tooltip = Arrays.asList(lines.clone());
        return this;
    }

    /**
     * Sets the tooltip shown while the button is disabled.
     *
     * @param lines legacy text lines, at most {@link Limits#TOOLTIP_LINES} lines of 256 visible characters each
     * @return this button
     * @throws InvalidUiException if a line is null
     */
    public Button disabledTooltip(String... lines) {
        this.disabledTooltip = texts(lines);
        return this;
    }

    /**
     * Sets the tooltip shown while the button is disabled.
     *
     * @param lines the lines, at most {@link Limits#TOOLTIP_LINES} lines of 256 visible characters each
     * @return this button
     */
    public Button disabledTooltip(Text... lines) {
        this.disabledTooltip = Arrays.asList(lines.clone());
        return this;
    }

    /**
     * Enables or disables the button. The default is enabled. The session rejects clicks on a button sent disabled
     * with {@link Rejection.Reason#DISABLED}.
     *
     * @param enabled false to disable the button
     * @return this button
     */
    public Button enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * Disables the button and, when lines are given, sets the disabled tooltip.
     *
     * @param reason legacy text lines for the disabled tooltip, or none to keep the current one
     * @return this button
     * @throws InvalidUiException if a line is null
     */
    public Button disabled(String... reason) {
        this.enabled = false;
        if (reason.length > 0) this.disabledTooltip = texts(reason);
        return this;
    }

    /**
     * Sets a text badge.
     *
     * @param text legacy text, at most 32 visible characters
     * @return this button
     * @throws InvalidUiException if the text is null
     */
    public Button badge(String text) {
        this.badge = Badge.of(text);
        return this;
    }

    /**
     * Sets a badge. Its text shows at most 32 visible characters.
     *
     * @param badge the badge, or null for none
     * @return this button
     */
    public Button badge(Badge badge) {
        this.badge = badge;
        return this;
    }

    /**
     * Sets the sound played on press.
     *
     * @param soundId a sound id such as {@code minecraft:block.note_block.pling}; the namespace is optional
     * @return this button
     * @throws InvalidUiException if the id is missing or not valid
     */
    public Button sound(String soundId) {
        this.sound = Check.pattern(soundId, Check.SOUND, "button.sound", "a sound id such as minecraft:block.note_block.pling");
        return this;
    }

    /**
     * Plays no sound on press.
     *
     * @return this button
     */
    public Button silent() {
        this.sound = "none";
        return this;
    }

    /**
     * Sets the number key that presses this button while the server menu is open.
     *
     * @param number 1 to 9
     * @return this button
     * @throws InvalidUiException if the number is out of range
     */
    public Button key(int number) {
        this.key = Check.range(number, 1, 9, "button.key");
        return this;
    }

    /**
     * Sets the actions the client runs on press, in order. Changing them needs the panel sent again; a {@link Patch}
     * cannot.
     *
     * @param actions at most {@link Limits#ACTIONS} actions, with at most one of command, url and copy
     * @return this button
     * @throws InvalidUiException if there are too many actions
     */
    public Button on(Action... actions) {
        this.actions = Arrays.asList(actions.clone());
        Check.count(this.actions.size(), Limits.ACTIONS, "button.on", "actions");
        return this;
    }

    /**
     * Sets the handler that runs on the server for each accepted click. When {@link #on} actions are set, one of them
     * must be {@link Action#send()}, or encoding throws {@link InvalidUiException}.
     *
     * @param handler the handler, or null for none
     * @return this button
     */
    public Button onClick(Consumer<Click> handler) {
        this.handler = handler;
        return this;
    }

    /**
     * Enables the button on the client only while a condition is true.
     *
     * @param condition the condition, or null for none
     * @return this button
     */
    public Button enabledWhen(Condition condition) {
        this.enabledWhen = condition;
        return this;
    }

    /**
     * Sets a cooldown between presses. When the button sends exactly one click per press (no confirm, command or url
     * action), the session rejects a click that comes more than 250 ms early with {@link Rejection.Reason#COOLDOWN}.
     *
     * @param ms the cooldown in milliseconds, 0 to 3600000 (1 hour)
     * @return this button
     * @throws InvalidUiException if the time is out of range
     */
    public Button cooldown(long ms) {
        this.cooldownMs = Check.range(ms, 0, 3600000, "button.cooldown.ms");
        return this;
    }

    /**
     * Sets a cooldown between presses and how it is shown. See {@link #cooldown(long)}.
     *
     * @param ms the cooldown in milliseconds, 0 to 3600000 (1 hour)
     * @param show how the running cooldown is shown, or null for the client default
     * @return this button
     * @throws InvalidUiException if the time is out of range
     */
    public Button cooldown(long ms, CooldownShow show) {
        cooldown(ms);
        this.cooldownShow = show;
        return this;
    }

    /**
     * Starts the button with part of its cooldown already running on the client.
     *
     * @param ms the time left in milliseconds, 0 to 3600000 (1 hour)
     * @return this button
     * @throws InvalidUiException if the time is out of range
     */
    public Button cooldownLeft(long ms) {
        this.cooldownLeft = Check.range(ms, 0, 3600000, "button.cooldown_left");
        return this;
    }

    private Button style(String key, String color) {
        style.addProperty(key, Check.color(color, "button.style." + key));
        return this;
    }

    static List<Text> texts(String[] lines) {
        List<Text> out = new ArrayList<Text>();
        for (String s : lines) out.add(Text.of(s));
        return out;
    }

    static JsonArray lines(List<Text> lines, int maxLines, String at) {
        Check.count(lines.size(), maxLines, at, "lines");
        JsonArray a = new JsonArray();
        for (int i = 0; i < lines.size(); i++) a.add(Check.text(lines.get(i), 256, at + "[" + i + "]").toJson());
        return a;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        enc.button(path);
        if (label != null) {
            o.add("label", Check.text(label, Limits.TEXT_CHARS, enc.at(path + ".label")).toJson());
        } else if (labelParts != null) {
            JsonArray parts = new JsonArray();
            int chars = 0;
            for (Object p : labelParts) {
                if (p instanceof Icon) {
                    JsonObject part = new JsonObject();
                    part.add("icon", ((Icon) p).toJson());
                    parts.add(part);
                } else {
                    chars += ((Text) p).visibleLength(enc.at(path + ".label"));
                    parts.add(((Text) p).toJson());
                }
            }
            if (chars > Limits.TEXT_CHARS) throw enc.fail(path + ".label", chars + " visible characters, the client shows at most " + Limits.TEXT_CHARS);
            o.add("label", parts);
        }
        if (icon != null) o.add("icon", icon.toJson());
        if (iconPos != null) o.addProperty("icon_pos", Check.lower(iconPos));
        if (shape != null) o.addProperty("shape", Check.lower(shape));
        if (radius != null) o.addProperty("radius", radius);
        if (labelScale != null) Json.num(o, "label_scale", labelScale);
        if (sizePreset != null) o.addProperty("size", Check.lower(sizePreset));
        if (!style.entrySet().isEmpty()) o.add("style", Json.copy(style));
        if (tooltip != null) o.add("tooltip", lines(tooltip, Limits.TOOLTIP_LINES, enc.at(path + ".tooltip")));
        if (disabledTooltip != null) o.add("disabled_tooltip", lines(disabledTooltip, Limits.TOOLTIP_LINES, enc.at(path + ".disabled_tooltip")));
        if (!enabled) o.addProperty("enabled", false);
        if (badge != null) o.add("badge", badge.toJson(32, enc.at(path + ".badge")));
        Json.str(o, "sound", sound);
        if (key != null) o.addProperty("key", key);
        List<Action> effective = actions;
        if (actions != null) o.add("on", Action.list(actions, enc.at(path + ".on")));
        if (enabledWhen != null) o.add("enabled_when", enabledWhen.toJson(enc.at(path + ".enabled_when")));
        if (cooldownMs != null || cooldownShow != null) {
            JsonObject c = new JsonObject();
            if (cooldownMs != null) c.addProperty("ms", cooldownMs);
            if (cooldownShow != null) c.addProperty("show", Check.lower(cooldownShow));
            o.add("cooldown", c);
        }
        if (cooldownLeft != null) o.addProperty("cooldown_left", cooldownLeft);
        if (handler != null && effective != null && !Action.anySends(effective)) {
            throw enc.fail(path + ".on", "button " + id + " has a click handler but none of its actions is Action.send(), so the server would never hear the click");
        }
        enc.buttonSpecs.put(id, new Encoder.ButtonSpec(id, handler, enabled, cooldownMs == null ? 0 : cooldownMs, Action.onePressOneClick(actions),
                Action.sentData(actions)));
    }
}
