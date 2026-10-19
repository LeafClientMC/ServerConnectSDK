package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code panel.patch} message: changes fields of a panel already sent, without sending it again. When it is
 * sent, the session checks that the panel was sent to this player and has not expired, that every patched element
 * id exists, and that the patch does not change what the server accepts: input options and ranges, button actions,
 * leaderboard rows and podium. The SDK restarts its time to live timer for the panel. Ids, types and children cannot
 * be patched; send the panel again for those.
 */
public final class Patch extends Message {

    private final String panelId;
    private final Map<String, JsonObject> elements = new LinkedHashMap<String, JsonObject>();
    private final JsonObject panel = new JsonObject();
    private final JsonObject style = new JsonObject();
    private String token;
    private boolean rotateToken;
    private Place<?> newPlace;
    private int[] newSize;
    private Long newTtl;

    private Patch(String panelId) {
        this.panelId = Check.id(panelId, "panel.patch.id");
    }

    /**
     * Creates a patch for a panel.
     *
     * @param panelId the panel id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the new message
     * @throws InvalidUiException if the id is not valid
     */
    public static Patch of(String panelId) {
        return new Patch(panelId);
    }

    /**
     * Sets one field of an element, by its JSON field name such as {@code label}, {@code text}, {@code enabled} or
     * {@code color}. A {@link Text} value shows at most {@link Limits#TEXT_CHARS} visible characters and a number is
     * -1e15 to 1e15. An enum is sent as its lower case name. Patching {@code enabled} or {@code cooldown} of a button
     * also changes how the session checks its clicks.
     *
     * @param elementId the element id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @param field the field name; not {@code id}, {@code type}, {@code children} or {@code child}
     * @param value a String, Number, Boolean, Text, Icon, Condition, enum or Gson JsonElement, or null to remove the field
     * @return this message
     * @throws InvalidUiException if the id or field is not allowed, or the value has an unsupported type or is out of range
     */
    public Patch set(String elementId, String field, Object value) {
        Check.id(elementId, "panel.patch.set");
        Check.notNull(field, "panel.patch.set." + elementId);
        if ("id".equals(field) || "type".equals(field) || "children".equals(field) || "child".equals(field)) {
            throw Check.fail("panel.patch.set." + elementId + "." + field, "id, type and children cannot be patched, send the panel again");
        }
        JsonObject fields = elements.get(elementId);
        if (fields == null) {
            fields = new JsonObject();
            elements.put(elementId, fields);
        }
        fields.add(field, toJson(value, "panel.patch.set." + elementId + "." + field));
        return this;
    }

    /**
     * Enables or disables a button or input. The session rejects clicks on a disabled button with
     * {@link Rejection.Reason#DISABLED}.
     *
     * @param elementId the element id
     * @param enabled false to disable it
     * @return this message
     * @throws InvalidUiException if the id is not valid
     */
    public Patch enabled(String elementId, boolean enabled) {
        return set(elementId, "enabled", enabled);
    }

    /**
     * Changes the label of a button, input or bar.
     *
     * @param elementId the element id
     * @param label legacy text with {@code &} or {@code §} codes, at most {@link Limits#TEXT_CHARS} visible characters
     * @return this message
     * @throws InvalidUiException if the id is not valid, or the label is null or too long
     */
    public Patch label(String elementId, String label) {
        return set(elementId, "label", Text.of(label));
    }

    /**
     * Changes the label of a button, input or bar.
     *
     * @param elementId the element id
     * @param label the label, at most {@link Limits#TEXT_CHARS} visible characters, or null to remove it
     * @return this message
     * @throws InvalidUiException if the id is not valid or the label is too long
     */
    public Patch label(String elementId, Text label) {
        return set(elementId, "label", label);
    }

    /**
     * Changes the text of a text element.
     *
     * @param elementId the element id
     * @param text legacy text, at most {@link Limits#TEXT_CHARS} visible characters
     * @return this message
     * @throws InvalidUiException if the id is not valid, or the text is null or too long
     */
    public Patch text(String elementId, String text) {
        return set(elementId, "text", Text.of(text));
    }

    /**
     * Changes the text of a text element.
     *
     * @param elementId the element id
     * @param text the text, at most {@link Limits#TEXT_CHARS} visible characters
     * @return this message
     * @throws InvalidUiException if the id is not valid or the text is too long
     */
    public Patch text(String elementId, Text text) {
        return set(elementId, "text", text);
    }

    /**
     * Sets or removes the text badge of a button.
     *
     * @param elementId the button id
     * @param badge legacy text, at most 32 visible characters, or null to remove the badge
     * @return this message
     * @throws InvalidUiException if the id is not valid or the badge is too long or empty
     */
    public Patch badge(String elementId, String badge) {
        return set(elementId, "badge", badge == null ? null : Badge.of(badge).toJson(32, "panel.patch.set." + elementId + ".badge"));
    }

    /**
     * Sets or removes the badge of a button.
     *
     * @param elementId the button id
     * @param badge the badge, text at most 32 visible characters, or null to remove it
     * @return this message
     * @throws InvalidUiException if the id is not valid or the badge is too long or has no text or icon
     */
    public Patch badge(String elementId, Badge badge) {
        return set(elementId, "badge", badge == null ? null : badge.toJson(32, "panel.patch.set." + elementId + ".badge"));
    }

    /**
     * Changes the condition that shows an element.
     *
     * @param elementId the element id
     * @param condition the condition, or null to always show the element
     * @return this message
     * @throws InvalidUiException if the id is not valid or the condition breaks a limit
     */
    public Patch elementWhen(String elementId, Condition condition) {
        return set(elementId, "when", condition);
    }

    /**
     * Removes a field of an element, so the client uses its default.
     *
     * @param elementId the element id
     * @param field the field name
     * @return this message
     * @throws InvalidUiException if the id or field is not allowed
     */
    public Patch remove(String elementId, String field) {
        return set(elementId, field, null);
    }

    /**
     * Moves the panel to another place. The client must list the new place in its caps, the panel limits of the new
     * place apply, and a panel with inputs can only stay in the menu.
     *
     * @param place the new place
     * @return this message
     * @throws InvalidUiException if the place is null
     */
    public Patch place(Place<?> place) {
        panel.add("place", Check.notNull(place, "panel.patch.panel.place").toJson());
        this.newPlace = place;
        return this;
    }

    /**
     * Changes the panel size.
     *
     * @param width the width, 1 to {@link Limits#PANEL_MAX_SIZE}
     * @param height the height, 1 to {@link Limits#PANEL_MAX_SIZE}
     * @return this message
     * @throws InvalidUiException if a value is out of range
     */
    public Patch size(int width, int height) {
        this.newSize = new int[]{Check.range(width, 1, Limits.PANEL_MAX_SIZE, "panel.patch.panel.size"),
                Check.range(height, 1, Limits.PANEL_MAX_SIZE, "panel.patch.panel.size")};
        panel.add("size", Json.numbers(newSize[0], newSize[1]));
        return this;
    }

    /**
     * Changes the condition that shows the panel.
     *
     * @param condition the condition, or null to always show the panel
     * @return this message
     * @throws InvalidUiException if the condition breaks a limit
     */
    public Patch when(Condition condition) {
        panel.add("when", condition == null ? com.google.gson.JsonNull.INSTANCE : condition.toJson("panel.patch.panel.when"));
        return this;
    }

    /**
     * Changes the z value of the panel.
     *
     * @param z -1000 to 1000
     * @return this message
     * @throws InvalidUiException if the value is out of range
     */
    public Patch z(int z) {
        panel.addProperty("z", Check.range(z, -1000, 1000, "panel.patch.panel.z"));
        return this;
    }

    /**
     * Changes how long the panel lives, counted from this patch.
     *
     * @param ms the time in milliseconds, 0 to 86400000 (24 hours); 0 means no limit
     * @return this message
     * @throws InvalidUiException if the time is out of range
     */
    public Patch ttl(long ms) {
        this.newTtl = Check.range(ms, 0, 86400000, "panel.patch.panel.ttl");
        panel.addProperty("ttl", newTtl);
        return this;
    }

    /**
     * Changes the panel theme.
     *
     * @param theme the theme
     * @return this message
     * @throws InvalidUiException if the theme is null
     */
    public Patch theme(Panel.Theme theme) {
        style.addProperty("theme", Check.lower(Check.notNull(theme, "panel.patch.panel.style.theme")));
        return this;
    }

    /**
     * Changes the panel background color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Patch bg(String color) {
        style.addProperty("bg", Check.color(color, "panel.patch.panel.style.bg"));
        return this;
    }

    /**
     * Changes the panel border color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Patch border(String color) {
        style.addProperty("border", Check.color(color, "panel.patch.panel.style.border"));
        return this;
    }

    /**
     * Sets a new click token. Clicks with the old token are then rejected with {@link Rejection.Reason#BAD_TOKEN}.
     *
     * @param token at most {@link Limits#TOKEN_CHARS} characters
     * @return this message
     * @throws InvalidUiException if the token is null or too long
     */
    public Patch token(String token) {
        this.token = Check.chars(token, Limits.TOKEN_CHARS, "panel.patch.token");
        this.rotateToken = false;
        return this;
    }

    /**
     * Gives the panel a new random click token. Clicks with the old token are then rejected with
     * {@link Rejection.Reason#BAD_TOKEN}.
     *
     * @return this message
     */
    public Patch newToken() {
        this.rotateToken = true;
        this.token = null;
        return this;
    }

    private static final java.util.List<String> INPUT_TYPES = java.util.Arrays.asList("input", "toggle", "checkbox", "choice", "slider");
    private static final java.util.List<String> INPUT_SHAPE = java.util.Arrays.asList("options", "min", "max", "step");

    private void checkElement(PanelRecord record, String elementId, JsonObject fields) {
        String at = "panel.patch " + panelId + " set." + elementId;
        String type = record.elementTypes.get(elementId);
        if (type == null) throw Check.fail(at, "panel " + panelId + " has no element with that id");
        for (Map.Entry<String, JsonElement> f : fields.entrySet()) {
            String key = f.getKey();
            if (INPUT_TYPES.contains(type) && INPUT_SHAPE.contains(key)) {
                throw Check.fail(at + "." + key, "the allowed values of an input cannot be patched, send the panel again");
            }
            if ("leaderboard".equals(type) && ("rows".equals(key) || "podium".equals(key))) {
                throw Check.fail(at + "." + key, "leaderboard rows change the element count, send the panel again");
            }
            if ("button".equals(type) && "on".equals(key)) {
                throw Check.fail(at + ".on", "button actions decide which clicks the server accepts, send the panel again");
            }
            if ("button".equals(type) && "cooldown".equals(key) && !f.getValue().isJsonNull() && !f.getValue().isJsonObject()) {
                throw Check.fail(at + ".cooldown", "expected {\"ms\": ..., \"show\": ...}");
            }
        }
    }

    private static long cooldownMs(JsonElement cooldown) {
        if (cooldown == null || !cooldown.isJsonObject()) return 0;
        JsonElement ms = cooldown.getAsJsonObject().get("ms");
        return ms != null && ms.isJsonPrimitive() && ms.getAsJsonPrimitive().isNumber() ? Math.max(0, Math.min(3600000, ms.getAsLong())) : 0;
    }

    private static JsonElement toJson(Object value, String at) {
        if (value == null) return com.google.gson.JsonNull.INSTANCE;
        if (value instanceof JsonElement) return Json.copy((JsonElement) value);
        if (value instanceof Text) return Check.text((Text) value, Limits.TEXT_CHARS, at).toJson();
        if (value instanceof Icon) return ((Icon) value).toJson();
        if (value instanceof Condition) return ((Condition) value).toJson(at);
        if (value instanceof Boolean) return new JsonPrimitive((Boolean) value);
        if (value instanceof Number) return Json.number(Check.range(((Number) value).doubleValue(), -1e15, 1e15, at));
        if (value instanceof String) return new JsonPrimitive((String) value);
        if (value instanceof Enum) return new JsonPrimitive(Check.lower((Enum<?>) value));
        throw Check.fail(at, "a patch value is a String, Number, Boolean, Text, Icon, Condition, enum or JsonElement");
    }

    @Override
    Outgoing encode(final Session session) {
        if (elements.isEmpty() && panel.entrySet().isEmpty() && style.entrySet().isEmpty() && token == null && !rotateToken) {
            throw Check.fail("panel.patch " + panelId, "nothing to change");
        }
        JsonObject msg = Json.envelope("panel.patch");
        msg.addProperty("id", panelId);
        final String newToken = rotateToken ? (session == null ? "preview" : session.newToken()) : token;
        if (newToken != null) msg.addProperty("token", newToken);
        JsonObject p = (JsonObject) Json.copy(panel);
        if (!style.entrySet().isEmpty()) p.add("style", Json.copy(style));
        if (!p.entrySet().isEmpty()) msg.add("panel", p);
        if (!elements.isEmpty()) {
            JsonObject set = new JsonObject();
            for (Map.Entry<String, JsonObject> e : elements.entrySet()) set.add(e.getKey(), Json.copy(e.getValue()));
            msg.add("set", set);
        }
        if (session == null) return Outgoing.simple(msg, "panel.patch " + panelId);
        final PanelRecord record = session.panelRecord(panelId);
        if (record == null) throw Check.fail("panel.patch " + panelId, "no panel " + panelId + " was sent to " + session.name());
        for (Map.Entry<String, JsonObject> e : elements.entrySet()) checkElement(record, e.getKey(), e.getValue());
        if (newPlace != null) {
            if (!session.has(newPlace.in)) throw Check.fail("panel.patch " + panelId + " panel.place", "the client does not list " + newPlace.in + " in its caps");
            if (!record.inputs.isEmpty() && !"menu".equals(newPlace.in)) {
                throw Check.fail("panel.patch " + panelId + " panel.place", "the panel has inputs, which are only allowed in menu panels");
            }
            session.checkPanelLimits(panelId, newPlace.in, newPlace.rightSide(), newPlace.tab());
        }
        final long now = session.now();
        return new Outgoing(msg, "panel.patch " + panelId, new Runnable() {
            @Override
            public void run() {
                if (newToken != null) record.token = newToken;
                if (newSize != null) record.size = newSize.clone();
                if (newPlace != null) record.moveTo(newPlace);
                else if (newSize != null && record.anchor != null) record.anchor = record.anchor.resized(newSize);
                record.rearm(newTtl != null ? newTtl : record.ttlMs, now);
                for (Map.Entry<String, JsonObject> e : elements.entrySet()) {
                    Encoder.ButtonSpec b = record.buttons.get(e.getKey());
                    if (b == null) continue;
                    JsonElement enabled = e.getValue().get("enabled");
                    if (enabled != null) b.enabled = enabled.isJsonNull() || enabled.getAsBoolean();
                    JsonElement cooldown = e.getValue().get("cooldown");
                    if (cooldown != null) b.cooldownMs = cooldownMs(cooldown);
                }
            }
        });
    }
}
