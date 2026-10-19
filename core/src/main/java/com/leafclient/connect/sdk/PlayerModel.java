package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

/**
 * A 3D model of a player with their skin. Create it with {@link Ui#player(java.util.UUID)} or {@link Ui#player(String)}.
 * A panel holds at most {@link Limits#PLAYERS_PER_PANEL} player models; leaderboard podium places count as player models.
 */
public final class PlayerModel extends Element<PlayerModel> {

    /** Width in pixels the client uses when no width is set. */
    public static final int DEFAULT_WIDTH = 48;
    /** Height in pixels the client uses when no height is set. */
    public static final int DEFAULT_HEIGHT = 96;
    /** Yaw in degrees the client uses when no yaw is set. */
    public static final double DEFAULT_YAW = 20;

    private final String player;
    private Double yaw;
    private Double pitch;

    PlayerModel(String player) {
        this.player = Check.uuid(player, "player.player");
    }

    @Override
    String type() {
        return "player";
    }

    /**
     * Sets the horizontal turn of the model.
     *
     * @param degrees the yaw, -180 to 180
     * @return this element
     * @throws InvalidUiException if the yaw is out of range
     */
    public PlayerModel yaw(double degrees) {
        this.yaw = Check.range(degrees, -180, 180, "player.yaw");
        return this;
    }

    /**
     * Sets the vertical tilt of the model.
     *
     * @param degrees the pitch, -45 to 45
     * @return this element
     * @throws InvalidUiException if the pitch is out of range
     */
    public PlayerModel pitch(double degrees) {
        this.pitch = Check.range(degrees, -45, 45, "player.pitch");
        return this;
    }

    @Override
    void encodeOwn(JsonObject o, Encoder enc, String path, int depth) {
        enc.player(path);
        o.addProperty("player", player);
        if (yaw != null) Json.num(o, "yaw", yaw);
        if (pitch != null) Json.num(o, "pitch", pitch);
    }
}
