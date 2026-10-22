package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The {@code toast} message: a short notification with a title or body and up to {@link Limits#TOAST_BUTTONS}
 * buttons. It is sent only to clients that list the {@code toast} cap. Remove it early with
 * {@link Session#removeToast(String)}. The session remembers the buttons of the last 32 toasts with buttons, so
 * clicks on older toasts are rejected.
 */
public final class Toast extends Message {

    /** Kind of toast. The client default is {@link #INFO}. */
    public enum Kind {
        /** Information. */
        INFO,
        /** Success. */
        SUCCESS,
        /** Warning. */
        WARN,
        /** Error. */
        ERROR
    }

    /** Time in milliseconds the client shows a toast when no duration is set. */
    public static final long DEFAULT_MS = 5000;

    private static final class ToastButton {
        final String id;
        final Text label;
        final List<Action> actions;
        final Consumer<Click> handler;

        ToastButton(String id, Text label, List<Action> actions, Consumer<Click> handler) {
            this.id = id;
            this.label = label;
            this.actions = actions;
            this.handler = handler;
        }
    }

    private final String id;
    private Text title;
    private Text body;
    private Icon icon;
    private Kind kind;
    private String accent;
    private Long ms;
    private final List<ToastButton> buttons = new ArrayList<ToastButton>();

    private Toast(String id) {
        this.id = Check.id(id, "toast.id");
    }

    /**
     * Creates a toast. It needs a title or a body before it is sent. Clicks are checked against the last toast sent
     * with this id.
     *
     * @param id 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the new message
     * @throws InvalidUiException if the id is not valid
     */
    public static Toast create(String id) {
        return new Toast(id);
    }

    /**
     * Sets the title.
     *
     * @param title legacy text with {@code &} or {@code §} codes, at most 64 visible characters
     * @return this message
     * @throws InvalidUiException if the title is null
     */
    public Toast title(String title) {
        return title(Text.of(title));
    }

    /**
     * Sets the title.
     *
     * @param title the title, at most 64 visible characters, or null for none
     * @return this message
     */
    public Toast title(Text title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the body.
     *
     * @param body legacy text with {@code &} or {@code §} codes, at most 256 visible characters
     * @return this message
     * @throws InvalidUiException if the body is null
     */
    public Toast body(String body) {
        return body(Text.of(body));
    }

    /**
     * Sets the body.
     *
     * @param body the body, at most 256 visible characters, or null for none
     * @return this message
     */
    public Toast body(Text body) {
        this.body = body;
        return this;
    }

    /**
     * Sets the icon.
     *
     * @param icon the icon, or null for none
     * @return this message
     */
    public Toast icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Sets the kind of toast.
     *
     * @param kind the kind, or null for the client default
     * @return this message
     */
    public Toast kind(Kind kind) {
        this.kind = kind;
        return this;
    }

    /**
     * Sets the accent color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Toast accent(String color) {
        this.accent = Check.color(color, "toast.accent");
        return this;
    }

    /**
     * Sets how long the toast is shown.
     *
     * @param ms the time in milliseconds, 1000 to 15000; the default is {@link #DEFAULT_MS}
     * @return this message
     * @throws InvalidUiException if the time is out of range
     */
    public Toast duration(long ms) {
        this.ms = Check.range(ms, 1000, 15000, "toast.ms");
        return this;
    }

    /**
     * Adds a button whose click goes to a handler on the server. Without actions the client sends the click.
     *
     * @param id the button id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}, unique in this toast
     * @param label legacy text, at most 24 visible characters
     * @param onClick the handler, or null for none
     * @return this message
     * @throws InvalidUiException if the id is not valid, the label is null, or the toast already has
     *         {@link Limits#TOAST_BUTTONS} buttons
     */
    public Toast button(String id, String label, Consumer<Click> onClick) {
        return add(id, Text.of(label), null, onClick);
    }

    /**
     * Adds a button that runs actions on the client.
     *
     * @param id the button id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}, unique in this toast
     * @param label legacy text, at most 24 visible characters
     * @param actions at most {@link Limits#ACTIONS} actions, with at most one of command, url and copy
     * @return this message
     * @throws InvalidUiException if the id is not valid, the label is null, or the toast already has
     *         {@link Limits#TOAST_BUTTONS} buttons
     */
    public Toast button(String id, String label, Action... actions) {
        return add(id, Text.of(label), Arrays.asList(actions.clone()), null);
    }

    /**
     * Adds a button with a handler and actions. When actions are given and a handler is set, one action must be
     * {@link Action#send()}, or encoding throws {@link InvalidUiException}.
     *
     * @param id the button id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}, unique in this toast
     * @param label the label, at most 24 visible characters
     * @param onClick the handler, or null for none
     * @param actions at most {@link Limits#ACTIONS} actions, or none to let the client send the click
     * @return this message
     * @throws InvalidUiException if the id is not valid, the label is null, or the toast already has
     *         {@link Limits#TOAST_BUTTONS} buttons
     */
    public Toast button(String id, Text label, Consumer<Click> onClick, Action... actions) {
        return add(id, label, actions.length == 0 ? null : Arrays.asList(actions.clone()), onClick);
    }

    private Toast add(String buttonId, Text label, List<Action> actions, Consumer<Click> handler) {
        if (buttons.size() >= Limits.TOAST_BUTTONS) throw Check.fail("toast.buttons", "more than " + Limits.TOAST_BUTTONS + " buttons");
        buttons.add(new ToastButton(Check.id(buttonId, "toast.buttons.id"), Check.notNull(label, "toast.buttons.label"), actions, handler));
        return this;
    }

    @Override
    String requiredCap() {
        return "toast";
    }

    @Override
    Outgoing encode(final Session session) {
        JsonObject o = Json.envelope("toast");
        o.addProperty("id", id);
        int shown = 0;
        if (title != null) {
            o.add("title", Check.text(title, 64, "toast \"" + id + "\" title").toJson());
            shown += title.visibleLength();
        }
        if (body != null) {
            o.add("body", Check.text(body, 256, "toast \"" + id + "\" body").toJson());
            shown += body.visibleLength();
        }
        if (shown == 0) throw Check.fail("toast \"" + id + "\"", "a toast needs a title or a body");
        if (icon != null) o.add("icon", icon.toJson());
        if (kind != null) o.addProperty("kind", Check.lower(kind));
        Json.str(o, "accent", accent);
        if (ms != null) o.addProperty("ms", ms);
        final String token = buttons.isEmpty() ? null : session == null ? "preview" : session.newToken();
        Json.str(o, "token", token);
        final Map<String, Encoder.ButtonSpec> specs = new LinkedHashMap<String, Encoder.ButtonSpec>();
        if (!buttons.isEmpty()) {
            JsonArray a = new JsonArray();
            for (int i = 0; i < buttons.size(); i++) {
                ToastButton b = buttons.get(i);
                String at = "toast \"" + id + "\" buttons[" + i + "]";
                if (specs.containsKey(b.id)) throw Check.fail(at + ".id", "duplicate button id " + b.id);
                JsonObject bj = new JsonObject();
                bj.addProperty("id", b.id);
                bj.add("label", Check.text(b.label, 24, at + ".label").toJson());
                if (b.actions != null) {
                    bj.add("on", Action.list(b.actions, at + ".on"));
                    if (b.handler != null && !Action.anySends(b.actions)) throw Check.fail(at + ".on", "has a click handler but no Action.send()");
                }
                a.add(bj);
                specs.put(b.id, new Encoder.ButtonSpec(b.id, b.handler, true, 0, false, Action.sentData(b.actions)));
            }
            o.add("buttons", a);
        }
        if (session == null) return Outgoing.simple(o, "toast " + id);
        return new Outgoing(o, "toast " + id, new Runnable() {
            @Override
            public void run() {
                session.putToast(id, token, specs);
            }
        });
    }
}
