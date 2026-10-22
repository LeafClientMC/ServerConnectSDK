package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * A one line text field. Create it with {@link Ui#input(String)} and read its value in a click handler with
 * {@link FormValues#text(String)}. Inputs are only allowed in menu panels.
 */
public final class TextInput extends InputElement<TextInput> {

    /** Maximum length in characters the client uses when no maximum is set. */
    public static final int DEFAULT_MAX_LENGTH = 64;

    private Text placeholder;
    private String value;
    private Integer maxLength;

    TextInput(String id) {
        super(id);
    }

    @Override
    String type() {
        return "input";
    }

    /**
     * Sets the hint shown while the field is empty.
     *
     * @param placeholder legacy text with {@code &} or {@code §} codes, at most 128 visible characters
     * @return this input
     * @throws InvalidUiException if the placeholder is null
     */
    public TextInput placeholder(String placeholder) {
        return placeholder(Text.of(placeholder));
    }

    /**
     * Sets the hint shown while the field is empty.
     *
     * @param placeholder the hint, at most 128 visible characters, or null for none
     * @return this input
     */
    public TextInput placeholder(Text placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * Sets the starting text. It must fit in the maximum length.
     *
     * @param value the text
     * @return this input
     * @throws InvalidUiException if the value is null
     */
    public TextInput value(String value) {
        Check.notNull(value, "input.value");
        if (!clientCanSend(value)) throw Check.fail("input.value", "has control or bidi characters the client removes");
        this.value = value;
        return this;
    }

    static boolean clientCanSend(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((Character.isISOControl(c) && c != '\n') || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩')
                    || c == '‎' || c == '‏') {
                return false;
            }
        }
        return true;
    }

    static String clean(String s) {
        StringBuilder out = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean drop = c == '§' || c == '؜';
            boolean space = c == '\n' || c == ' ' || c == ' ';
            if ((drop || space) && out == null) {
                out = new StringBuilder(s.length());
                out.append(s, 0, i);
            }
            if (out != null && !drop) out.append(space ? ' ' : c);
        }
        return out == null ? s : out.toString();
    }

    /**
     * Sets the most characters the player can type. Clicks with a longer value are rejected with
     * {@link Rejection.Reason#BAD_INPUTS}.
     *
     * @param chars the maximum, 1 to {@link Limits#INPUT_CHARS}; the default is {@link #DEFAULT_MAX_LENGTH}
     * @return this input
     * @throws InvalidUiException if the maximum is out of range
     */
    public TextInput maxLength(int chars) {
        this.maxLength = Check.range(chars, 1, Limits.INPUT_CHARS, "input.max");
        return this;
    }

    @Override
    Encoder.InputSpec encodeInput(JsonObject o, Encoder enc, String path) {
        int max = maxLength == null ? DEFAULT_MAX_LENGTH : maxLength;
        if (maxLength != null) o.addProperty("max", maxLength);
        if (value != null) {
            if (value.length() > max) throw enc.fail(path + ".value", value.length() + " characters, longer than max " + max);
            o.addProperty("value", value);
        }
        if (placeholder != null) o.add("placeholder", Check.text(placeholder, 128, enc.at(path + ".placeholder")).toJson());
        return new Encoder.InputSpec(id, Encoder.InputKind.TEXT, max, null, 0, 0, 0, value == null ? "" : clean(value));
    }
}
