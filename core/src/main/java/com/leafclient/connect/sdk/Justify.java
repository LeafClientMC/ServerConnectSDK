package com.leafclient.connect.sdk;

/**
 * Main axis placement of the children of a row or column, set with {@link Container#justify(Justify)}.
 * It applies only when the children leave free space and none of them has a {@link Element#grow(double) grow} weight.
 */
public enum Justify {
    /** Children start at the beginning of the main axis. */
    START,
    /** Children are centered on the main axis. */
    CENTER,
    /** Children end at the end of the main axis. */
    END,
    /** The free space is split evenly between the children. */
    BETWEEN
}
