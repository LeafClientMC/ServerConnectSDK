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
        /** The panel or toast has no button with that id, or a waypoint click was not on its label. */
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
        /** The player was too far from the world panel. See {@link WorldAnchor#maxClickDistance()}. */
        OUT_OF_REACH
    }

    private final Session session;
    private final Reason reason;
    private final String detail;
    private final String panel;
    private final String button;
    private final long seq;

    Rejection(Session session, Reason reason, String detail, String panel, String button, long seq) {
        this.session = session;
        this.reason = reason;
        this.detail = detail;
        this.panel = panel;
        this.button = button;
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
    public String detail() {
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
        return "rejected click " + panel + "/" + button + " seq=" + seq + " from " + session.name() + ": " + reason + " (" + detail + ")";
    }
}
