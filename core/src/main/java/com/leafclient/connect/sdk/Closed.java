package com.leafclient.connect.sdk;

import java.util.Locale;

/**
 * A {@code closed} message from Leaf Client: a menu, panel or toast was closed. Receive it with {@link LeafConnect#onClosed}.
 * After the player closes the menu or a panel, the SDK waits {@link Limits#OPEN_AFTER_CLOSE_MS} before it sends the next open.
 */
public final class Closed {

    /** What was closed. */
    public enum What {
        /** The server menu. */
        MENU,
        /** A panel. {@link Closed#id()} is the panel id. */
        PANEL,
        /** A toast. {@link Closed#id()} is the toast id. */
        TOAST,
        /** A value the SDK does not know, or none. */
        OTHER
    }

    /** Why it was closed. */
    public enum Why {
        /** The player closed it. */
        PLAYER,
        /** The player closed it with a key. */
        KEY,
        /** The player walked away from a front panel. */
        WALK,
        /** Its time to live ended. */
        TTL,
        /** Another screen replaced it. */
        SCREEN,
        /** A value the SDK does not know, or none. */
        OTHER
    }

    private final Session session;
    private final What what;
    private final String id;
    private final Why why;

    Closed(Session session, String what, String id, String why) {
        this.session = session;
        this.what = parse(What.class, what, What.OTHER);
        this.id = id;
        this.why = parse(Why.class, why, Why.OTHER);
    }

    /**
     * Returns the session of the player whose client sent the message.
     *
     * @return the session
     */
    public Session session() {
        return session;
    }

    /**
     * Returns what was closed.
     *
     * @return the kind, {@link What#OTHER} if the client sent an unknown value
     */
    public What what() {
        return what;
    }

    /**
     * Returns the id of the panel or toast that was closed.
     *
     * @return the id, or null if the client sent none
     */
    public String id() {
        return id;
    }

    /**
     * Returns why it was closed.
     *
     * @return the reason, {@link Why#OTHER} if the client sent an unknown value
     */
    public Why why() {
        return why;
    }

    /**
     * Tells whether the player closed it on purpose.
     *
     * @return true for {@link Why#PLAYER} and {@link Why#KEY}
     */
    public boolean byPlayer() {
        return why == Why.PLAYER || why == Why.KEY;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null) return fallback;
        for (E e : type.getEnumConstants()) if (e.name().equals(raw.toUpperCase(Locale.ROOT))) return e;
        return fallback;
    }

    @Override
    public String toString() {
        return "closed " + what + (id != null ? " " + id : "") + " by " + why;
    }
}
