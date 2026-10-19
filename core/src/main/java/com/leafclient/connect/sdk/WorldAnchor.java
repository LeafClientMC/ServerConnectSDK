package com.leafclient.connect.sdk;

import java.util.UUID;

/**
 * Where a block or entity panel is in the world, as the SDK recorded it when the panel was sent. Get it with
 * {@link Click#anchor()}. The platform adapter uses it to reject clicks from players too far away
 * ({@link Rejection.Reason#OUT_OF_REACH}).
 */
public final class WorldAnchor {

    /** What the panel is attached to. */
    public enum Kind {
        /** A block position. */
        BLOCK,
        /** An entity by its entity id. */
        ENTITY,
        /** A player by UUID. */
        PLAYER
    }

    private final Kind kind;
    private final double x;
    private final double y;
    private final double z;
    private final int entityId;
    private final UUID player;
    private final double[] offset;
    private final double reach;
    private final double scale;
    private final double extent;

    WorldAnchor(Kind kind, double x, double y, double z, int entityId, UUID player, double[] offset, double reach, double scale, int[] size) {
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.z = z;
        this.entityId = entityId;
        this.player = player;
        this.offset = offset;
        this.reach = reach;
        this.scale = scale;
        this.extent = (size == null ? Limits.PANEL_MAX_SIZE : Math.max(size[0], size[1])) * scale;
    }

    WorldAnchor resized(int[] size) {
        return new WorldAnchor(kind, x, y, z, entityId, player, offset, reach, scale, size);
    }

    static WorldAnchor of(Place<?> place, int[] size) {
        if (!(place instanceof Place.Anchored)) return null;
        Place.Anchored<?> a = (Place.Anchored<?>) place;
        double scale = a.scale == null ? Place.DEFAULT_SCALE : a.scale;
        double reach = a.reach == null ? 6 : a.reach;
        if (place instanceof Place.Block) {
            Place.Block b = (Place.Block) place;
            double[] off = a.offset == null ? new double[]{0.5, 1.5, 0.5} : a.offset.clone();
            return new WorldAnchor(Kind.BLOCK, b.x, b.y, b.z, -1, null, off, reach, scale, size);
        }
        Place.Entity e = (Place.Entity) place;
        double[] off = a.offset == null ? new double[]{0, 2.4, 0} : a.offset.clone();
        if (e.player != null) return new WorldAnchor(Kind.PLAYER, 0, 0, 0, -1, UUID.fromString(dashed(e.player)), off, reach, scale, size);
        return new WorldAnchor(Kind.ENTITY, 0, 0, 0, e.entityId, null, off, reach, scale, size);
    }

    private static String dashed(String uuid) {
        if (uuid.length() != 32) return uuid;
        return uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20);
    }

    /**
     * Returns what the panel is attached to.
     *
     * @return the kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the x coordinate of the panel. For a block anchor it is the block x plus the offset; for entity and
     * player anchors it is only the offset, so add the entity position.
     *
     * @return the x coordinate
     */
    public double x() {
        return x + offset[0];
    }

    /**
     * Returns the y coordinate of the panel. For a block anchor it is the block y plus the offset; for entity and
     * player anchors it is only the offset, so add the entity position.
     *
     * @return the y coordinate
     */
    public double y() {
        return y + offset[1];
    }

    /**
     * Returns the z coordinate of the panel. For a block anchor it is the block z plus the offset; for entity and
     * player anchors it is only the offset, so add the entity position.
     *
     * @return the z coordinate
     */
    public double z() {
        return z + offset[2];
    }

    /**
     * Returns the entity id of an {@link Kind#ENTITY} anchor.
     *
     * @return the entity id, or -1 for other kinds
     */
    public int entityId() {
        return entityId;
    }

    /**
     * Returns the player of a {@link Kind#PLAYER} anchor.
     *
     * @return the player UUID, or null for other kinds
     */
    public UUID player() {
        return player;
    }

    /**
     * Returns the x offset of the panel from its anchor. The default is 0.5 for blocks and 0 for entities.
     *
     * @return the x offset in blocks
     */
    public double offsetX() {
        return offset[0];
    }

    /**
     * Returns the y offset of the panel from its anchor. The default is 1.5 for blocks and 2.4 for entities.
     *
     * @return the y offset in blocks
     */
    public double offsetY() {
        return offset[1];
    }

    /**
     * Returns the z offset of the panel from its anchor. The default is 0.5 for blocks and 0 for entities.
     *
     * @return the z offset in blocks
     */
    public double offsetZ() {
        return offset[2];
    }

    /**
     * Returns the farthest distance in blocks from the player's eyes to the panel point at which a click is accepted:
     * the place reach (default 6), plus the largest panel side times the place scale ({@link Limits#PANEL_MAX_SIZE}
     * when the panel has no size), plus 2.
     *
     * @return the distance in blocks
     */
    public double maxClickDistance() {
        return reach + extent + 2;
    }
}
