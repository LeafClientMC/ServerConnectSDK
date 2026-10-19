package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * The {@code dialog.style} message: the look of the dialogs Leaf Client opens for this server.
 * It is sent only to clients that list the {@code dialog.style} cap.
 */
public final class DialogStyle extends Message {

    /** Background behind a dialog. */
    public enum Background {
        /** The game behind the dialog is blurred. */
        BLUR,
        /** The game behind the dialog is darkened. */
        DIM,
        /** A solid background. */
        SOLID
    }

    /** Alignment of the dialog title. */
    public enum TitleAlign {
        /** Left aligned. */
        LEFT,
        /** Centered. */
        CENTER
    }

    private String accent;
    private Background bg;
    private Icon icon;
    private TitleAlign titleAlign;

    private DialogStyle() {}

    /**
     * Creates an empty dialog style message.
     *
     * @return the new message
     */
    public static DialogStyle create() {
        return new DialogStyle();
    }

    /**
     * Sets the accent color of dialogs.
     *
     * @param color {@code #RGB}, {@code #RRGGBB} or {@code #AARRGGBB}
     * @return this message
     * @throws InvalidUiException if the color is missing or not in one of those forms
     */
    public DialogStyle accent(String color) {
        this.accent = Check.color(color, "dialog.style.accent");
        return this;
    }

    /**
     * Sets the background behind dialogs.
     *
     * @param bg the background, or null to leave it unset
     * @return this message
     */
    public DialogStyle background(Background bg) {
        this.bg = bg;
        return this;
    }

    /**
     * Sets the icon shown in dialogs.
     *
     * @param icon the icon, or null for none
     * @return this message
     */
    public DialogStyle icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Sets the alignment of dialog titles.
     *
     * @param align the alignment, or null to leave it unset
     * @return this message
     */
    public DialogStyle titleAlign(TitleAlign align) {
        this.titleAlign = align;
        return this;
    }

    @Override
    String requiredCap() {
        return "dialog.style";
    }

    @Override
    Outgoing encode(Session session) {
        JsonObject o = Json.envelope("dialog.style");
        Json.str(o, "accent", accent);
        if (bg != null) o.addProperty("bg", Check.lower(bg));
        if (icon != null) o.add("icon", icon.toJson());
        if (titleAlign != null) o.addProperty("title_align", Check.lower(titleAlign));
        return Outgoing.simple(o, "dialog.style");
    }
}
