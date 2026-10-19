package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Text for titles, labels and messages: legacy text with color codes, plain text, a translation, a key name or a
 * Minecraft text component. Text is immutable; the style methods return a new instance. Limits on text count visible
 * characters: color and format codes do not count. Shown text can hold value placeholders (see {@link Values}).
 */
public final class Text {

    private static final String CODES = "0123456789abcdef";
    private static final Set<String> NAMED = new HashSet<String>(Arrays.asList(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"));

    private final JsonElement json;

    private Text(JsonElement json) {
        this.json = json;
    }

    /**
     * Creates legacy text. {@code &} and {@code §} start color and format codes: {@code 0-9 a-f k-o r},
     * {@code &#RRGGBB} and the Bukkit form {@code &x&R&R&G&G&B&B}.
     *
     * @param legacy the text
     * @return the text
     * @throws InvalidUiException if the text is null
     */
    public static Text of(String legacy) {
        if (legacy == null) throw new InvalidUiException("text: missing");
        return new Text(new JsonPrimitive(legacy));
    }

    /**
     * Creates a text component with a {@code text} field, so {@code &} is shown as is.
     *
     * @param text the text
     * @return the text
     * @throws InvalidUiException if the text is null
     */
    public static Text plain(String text) {
        if (text == null) throw new InvalidUiException("text: missing");
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        return new Text(o);
    }

    /**
     * Creates a translated text component, shown in the player's language. The visible length counts the fallback,
     * or the key when there is no fallback.
     *
     * @param key the translation key, at most 256 characters
     * @param fallback the text used when the key is unknown, at most 2048 characters, or null
     * @return the text
     * @throws InvalidUiException if the key is null or a value is too long
     */
    public static Text translate(String key, String fallback) {
        Check.chars(key, 256, "translate");
        JsonObject o = new JsonObject();
        o.addProperty("translate", key);
        if (fallback != null) o.addProperty("fallback", Check.chars(fallback, Limits.TEXT_CHARS * 4, "fallback"));
        return new Text(o);
    }

    /**
     * Creates a keybind text component, shown as the key the player bound.
     *
     * @param key the keybind name such as {@code key.jump}, at most 128 characters
     * @return the text
     * @throws InvalidUiException if the key is null or too long
     */
    public static Text keybind(String key) {
        JsonObject o = new JsonObject();
        o.addProperty("keybind", Check.chars(key, 128, "keybind"));
        return new Text(o);
    }

    /**
     * Creates text from Minecraft text component JSON.
     *
     * @param componentJson the component as a JSON string
     * @return the text
     * @throws InvalidUiException if the string is not valid JSON, or the component has a field of the wrong type or an
     *         unknown color
     */
    @SuppressWarnings("deprecation")
    public static Text json(String componentJson) {
        JsonElement e;
        try {
            e = new JsonParser().parse(componentJson);
        } catch (RuntimeException ex) {
            throw new InvalidUiException("text: not valid JSON");
        }
        return json(e);
    }

    /**
     * Creates text from a Minecraft text component. The component is copied.
     *
     * @param component the component
     * @return the text
     * @throws InvalidUiException if the component is null, has a field of the wrong type or has an unknown color
     */
    public static Text json(JsonElement component) {
        if (component == null || component.isJsonNull()) throw new InvalidUiException("text: missing");
        Text t = new Text(Json.copy(component));
        t.visibleLength("text");
        return t;
    }

    /**
     * Returns this text with a color.
     *
     * @param color a Minecraft color name such as {@code gold} or {@code dark_aqua}, or {@code #RGB}, {@code #RRGGBB}
     *              or {@code #AARRGGBB}
     * @return the new text
     * @throws InvalidUiException if the color is not one of those
     */
    public Text color(String color) {
        if (color == null || !(NAMED.contains(color) || Check.isHex(color)) || color.length() > 16) {
            throw new InvalidUiException("text color: \"" + color + "\" is not a color name or #RGB, #RRGGBB, #AARRGGBB");
        }
        return with("color", new JsonPrimitive(color));
    }

    /**
     * Returns this text in bold.
     *
     * @return the new text
     */
    public Text bold() {
        return with("bold", new JsonPrimitive(true));
    }

    /**
     * Returns this text in italics.
     *
     * @return the new text
     */
    public Text italic() {
        return with("italic", new JsonPrimitive(true));
    }

    /**
     * Returns this text underlined.
     *
     * @return the new text
     */
    public Text underlined() {
        return with("underlined", new JsonPrimitive(true));
    }

    /**
     * Returns this text struck through.
     *
     * @return the new text
     */
    public Text strikethrough() {
        return with("strikethrough", new JsonPrimitive(true));
    }

    /**
     * Returns this text obfuscated.
     *
     * @return the new text
     */
    public Text obfuscated() {
        return with("obfuscated", new JsonPrimitive(true));
    }

    /**
     * Returns this text with a shadow color.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return the new text
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public Text shadowColor(String color) {
        return with("shadow_color", new JsonPrimitive(Check.color(color, "text shadow_color")));
    }

    /**
     * Returns this text in a resource pack font.
     *
     * @param font a font id with a namespace, such as {@code myserver:title}
     * @return the new text
     * @throws InvalidUiException if the font is missing or not a valid font id
     */
    public Text font(String font) {
        return with("font", new JsonPrimitive(Check.pattern(font, Check.FONT, "text font", "a font id such as myserver:title")));
    }

    /**
     * Returns this text followed by more text.
     *
     * @param more the texts to add
     * @return the new text
     * @throws InvalidUiException if a text is null
     */
    public Text append(Text... more) {
        JsonObject o = object();
        JsonArray extra = o.has("extra") && o.get("extra").isJsonArray() ? o.getAsJsonArray("extra") : new JsonArray();
        for (Text t : more) extra.add(Json.copy(Check.notNull(t, "text extra").json));
        o.add("extra", extra);
        return new Text(o);
    }

    /**
     * Returns this text followed by legacy text.
     *
     * @param legacy legacy text with {@code &} or {@code §} codes
     * @return the new text
     * @throws InvalidUiException if the text is null
     */
    public Text append(String legacy) {
        return append(of(legacy));
    }

    /**
     * Counts the characters the client shows, the way the SDK checks text limits. Color and format codes, control
     * characters and bidirectional marks do not count; line breaks do.
     *
     * @return the visible length
     * @throws InvalidUiException if a component has a field of the wrong type or an unknown color
     */
    public int visibleLength() {
        return visibleLength("text");
    }

    /**
     * Returns the text as the JSON sent to the client.
     *
     * @return the JSON string
     */
    @Override
    public String toString() {
        return Json.write(json);
    }

    JsonElement toJson() {
        return Json.copy(json);
    }

    int visibleLength(String at) {
        return visible(json, at);
    }

    private Text with(String key, JsonElement value) {
        JsonObject o = object();
        o.add(key, value);
        return new Text(o);
    }

    private JsonObject object() {
        if (json.isJsonObject()) return (JsonObject) Json.copy(json);
        JsonObject o = new JsonObject();
        o.addProperty("text", "");
        JsonArray extra = new JsonArray();
        extra.add(Json.copy(json));
        o.add("extra", extra);
        return o;
    }

    static Text from(String legacy) {
        return legacy == null ? null : of(legacy);
    }

    private static int visible(JsonElement e, String at) {
        if (e == null || e.isJsonNull()) return 0;
        if (e.isJsonPrimitive()) {
            JsonPrimitive p = e.getAsJsonPrimitive();
            return p.isString() ? legacyLength(p.getAsString(), true) : p.getAsString().length();
        }
        if (e.isJsonArray()) {
            int n = 0;
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) n += visible(a.get(i), at);
            return n;
        }
        JsonObject o = e.getAsJsonObject();
        int n = 0;
        String text = stringField(o, "text", at);
        String translate = stringField(o, "translate", at);
        String keybind = stringField(o, "keybind", at);
        String fallback = stringField(o, "fallback", at);
        for (Map.Entry<String, JsonElement> entry : o.entrySet()) {
            if ("color".equals(entry.getKey())) {
                String c = stringField(o, "color", at);
                if (c != null && !NAMED.contains(c.toLowerCase(java.util.Locale.ROOT)) && !Check.isHex(c)) {
                    throw Check.fail(at, "unknown text color \"" + c + "\"");
                }
            }
        }
        if (text != null) n += legacyLength(text, false);
        else if (translate != null) n += (fallback != null ? fallback : translate).length();
        else if (keybind != null) n += keybind.length();
        JsonElement extra = o.get("extra");
        if (extra != null && !extra.isJsonNull()) {
            if (!extra.isJsonArray()) throw Check.fail(at, "extra must be an array");
            n += visible(extra, at);
        }
        return n;
    }

    private static String stringField(JsonObject o, String key, String at) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw Check.fail(at, key + " must be a string");
        return e.getAsString();
    }

    static int legacyLength(String s, boolean ampersand) {
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            boolean marker = c == '§' || (ampersand && c == '&');
            if (marker && i + 1 < s.length()) {
                char code = Character.toLowerCase(s.charAt(i + 1));
                boolean hex = false;
                int consumed = 0;
                if (code == '#' && i + 8 <= s.length()) {
                    hex = Check.isHex(s.substring(i + 1, i + 8));
                    consumed = 8;
                } else if (code == 'x' && i + 14 <= s.length()) {
                    hex = bukkitHex(s, i, c);
                    consumed = 14;
                }
                if (hex) {
                    i += consumed;
                    continue;
                }
                if (CODES.indexOf(code) >= 0 || "klmno".indexOf(code) >= 0 || code == 'r') {
                    i += 2;
                    continue;
                }
            }
            if (c == '\n' || !(Character.isISOControl(c) || isBidi(c))) n++;
            i++;
        }
        return n;
    }

    private static boolean bukkitHex(String s, int start, char marker) {
        StringBuilder h = new StringBuilder("#");
        for (int k = 0; k < 6; k++) {
            int at = start + 2 + k * 2;
            if (s.charAt(at) != marker) return false;
            h.append(s.charAt(at + 1));
        }
        return Check.isHex(h.toString());
    }

    private static boolean isBidi(char c) {
        return (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩') || c == '‎' || c == '‏';
    }
}
