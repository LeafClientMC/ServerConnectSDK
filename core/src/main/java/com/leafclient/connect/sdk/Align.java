package com.leafclient.connect.sdk;

/**
 * Alignment set with {@link Element#align(Align)}. On a row or column it places the children on the cross axis.
 * On text it aligns each line inside the element. The client default is {@link #START}.
 */
public enum Align {
    /** At the start: left, or top on the cross axis of a row. */
    START,
    /** Centered. */
    CENTER,
    /** At the end: right, or bottom on the cross axis of a row. */
    END,
    /** Children of a row or column fill the whole cross axis. */
    STRETCH
}
