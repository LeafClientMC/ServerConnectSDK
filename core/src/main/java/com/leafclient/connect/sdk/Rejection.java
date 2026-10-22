package com.leafclient.connect.sdk;

/**
 * A click from Leaf Client that the SDK refused. No click handler runs for it. Receive it with {@link LeafConnect#onRejected}.
 */
public final class Rejection {

    /** Why the click was refused. */
    public enum Reason {
        /** The click had no source, panel, button or sequence number. */
        MALFORMED,
        /** The sequence number was not above the last one from this client. */
        REPLAY,
        /** The client sent more than 32 clicks per second. */
        RATE,
        /** This plugin sent no panel, toast or waypoint with that id. These are not logged. */
        UNKNOWN_PANEL,
        /** The token did not match the last {@code panel.set}, patch, toast or waypoint with that id. */
        BAD_TOKEN,
        /** The panel or toast has no button with that id, the button has no send action, or a waypoint click was not on its label. */
        UNKNOWN_BUTTON,
        /** The button was sent disabled. */
        DISABLED,
        /** The button was pressed again before its cooldown ended (250 ms tolerance). */
        COOLDOWN,
        /** The time to live of the panel or waypoint ended more than 3 seconds before the click. */
        EXPIRED,
        /** The click came from a different place than the one the panel is in. */
        WRONG_SOURCE,
        /** The form held an unknown input or a value the inputs cannot produce. */
        BAD_INPUTS,
        /** The player was too far from the world panel, or not in the world or dimension of a block panel. See {@link WorldAnchor#maxClickDistance()}. */
        OUT_OF_REACH,
        /** The click carried data that none of the button's {@link Action#send(String)} actions sends. */
        BAD_DATA
    }

    private final Session session;
    private final Reason reason;
    private final Object[] detailParts;
    private String detail;
    private final String panel;
    private final String button;
    private final long seq;

    Rejection(Session session, Reason reason, String panel, String button, long seq, Object... detailParts) {
        this.session = session;
        this.reason = reason;
        this.detailParts = detailParts;
        this.panel = Json.clean(panel, Limits.ID_CHARS);
        this.button = Json.clean(button, Limits.ID_CHARS);
        this.seq = seq;
    }

    /**
     * Returns the session of the player whose client sent the click.
     *
     * @return the session
     */
    public Session session() {
        return session;
    }

    /**
     * Returns why the click was refused.
     *
     * @return the reason
     */
    public Reason reason() {
        return reason;
    }

    /**
     * Returns a description of the problem for logs.
     *
     * @return the detail
     */
    public synchronized String detail() {
        if (detail == null) {
            StringBuilder sb = new StringBuilder();
            for (Object part : detailParts) sb.append(part);
            detail = Json.clean(sb.toString(), 512);
        }
        return detail;
    }

    /**
     * Returns the panel, toast or waypoint id the click named.
     *
     * @return the id, or null if the click had none
     */
    public String panel() {
        return panel;
    }

    /**
     * Returns the button id the click named.
     *
     * @return the id, or null if the click had none
     */
    public String button() {
        return button;
    }

    /**
     * Returns the sequence number of the click.
     *
     * @return the number, or -1 for a {@link Reason#MALFORMED} click
     */
    public long seq() {
        return seq;
    }

    @Override
    public String toString() {
        return "rejected click " + panel + "/" + button + " seq=" + seq + " from " + session.name() + ": " + reason + " (" + detail() + ")";
    }
}
