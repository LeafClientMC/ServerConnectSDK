package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.UUID;

/**
 * Where a {@link Panel} is shown: the server menu, beside the inventory or a container screen, above the chat, in front
 * of the player, on a block, on an entity or on the HUD. Create one with the static methods. A panel is sent only to
 * clients that list its place in their caps, and each place has a per player panel limit in {@link Limits}.
 *
 * @param <T> the concrete place type, returned by the builder methods
 */
public abstract class Place<T extends Place<T>> {

    /** A side of the screen. */
    public enum Side {
        /** Left side. */
        LEFT,
        /** Right side. */
        RIGHT
    }

    /** Vertical alignment beside a screen. The client default is {@link #TOP}. */
    public enum VAlign {
        /** Top. */
        TOP,
        /** Center. */
        CENTER,
        /** Bottom. */
        BOTTOM
    }

    /** How a world panel turns toward the player. The client default is {@link #BILLBOARD_Y}. */
    public enum Face {
        /** Always faces the camera. */
        BILLBOARD,
        /** Turns around the vertical axis to face the camera. */
        BILLBOARD_Y,
        /** Keeps the rotation set with yaw, pitch and roll. */
        FIXED
    }

    /** The block face a block panel is mounted on. A mounted panel has a fixed rotation. */
    public enum Mount {
        /** North face. */
        NORTH,
        /** South face. */
        SOUTH,
        /** East face. */
        EAST,
        /** West face. */
        WEST,
        /** Top face. */
        UP,
        /** Bottom face. */
        DOWN
    }

    /** Screen anchor of a HUD panel. The client default is {@link #TOP_RIGHT}. */
    public enum Anchor {
        /** Top left corner. */
        TOP_LEFT,
        /** Top center. */
        TOP,
        /** Top right corner. */
        TOP_RIGHT,
        /** Left center. */
        LEFT,
        /** Right center. */
        RIGHT,
        /** Bottom left corner. */
        BOTTOM_LEFT,
        /** Bottom right corner. */
        BOTTOM_RIGHT
    }

    /** Size of one panel pixel in blocks that the client uses for world panels when no scale is set. */
    public static final double DEFAULT_SCALE = 0.025;

    final String in;
    private Integer order;

    Place(String in) {
        this.in = in;
    }

    @SuppressWarnings("unchecked")
    final T self() {
        return (T) this;
    }

    /**
     * Sets the sort order among panels in the same spot. Lower values come first; the client default is 0.
     *
     * @param order -1000 to 1000
     * @return this place
     * @throws InvalidUiException if the order is out of range
     */
    public T order(int order) {
        this.order = Check.range(order, -1000, 1000, "place.order");
        return self();
    }

    abstract void encodeOwn(JsonObject o);

    JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("in", in);
        if (order != null) o.addProperty("order", order);
        encodeOwn(o);
        return o;
    }

    boolean rightSide() {
        return false;
    }

    String tab() {
        return null;
    }

    boolean world() {
        return false;
    }

    /**
     * Places the panel in the server menu, outside any tab. Only menu panels can hold inputs.
     *
     * @return the place
     */
    public static Menu menu() {
        return new Menu(null);
    }

    /**
     * Places the panel in a tab of the server menu. A tab shows at most {@link Limits#PANELS_PER_TAB} panels.
     *
     * @param tab the tab id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the place
     * @throws InvalidUiException if the id is not valid
     */
    public static Menu menu(String tab) {
        return new Menu(Check.id(tab, "place.tab"));
    }

    /**
     * Places the panel beside the player inventory. Each side holds at most {@link Limits#INVENTORY_PANELS_PER_SIDE}.
     *
     * @param side the side, or null for left
     * @return the place
     */
    public static Inventory inventory(Side side) {
        return new Inventory(side);
    }

    /**
     * Places the panel with the chat. A player has at most {@link Limits#CHAT_PANELS} chat panels.
     *
     * @return the place
     */
    public static Chat chat() {
        return new Chat();
    }

    /**
     * Places the panel in the world in front of the player. Open it with {@link Session#openPanel(String)} or
     * {@link Action#openPanel(String)}. A player has at most {@link Limits#FRONT_PANELS} front panels.
     *
     * @return the place
     */
    public static Front front() {
        return new Front();
    }

    /**
     * Places the panel at a block. A player has at most {@link Limits#WORLD_PANELS} block and entity panels.
     *
     * @param x the block x, -30000000 to 30000000
     * @param y the block y, -30000000 to 30000000
     * @param z the block z, -30000000 to 30000000
     * @return the place
     * @throws InvalidUiException if a coordinate is out of range
     */
    public static Block block(int x, int y, int z) {
        return new Block(x, y, z);
    }

    /**
     * Places the panel on an entity. A player has at most {@link Limits#WORLD_PANELS} block and entity panels.
     *
     * @param entityId the entity id, 0 or more
     * @return the place
     * @throws InvalidUiException if the id is negative
     */
    public static Entity entity(int entityId) {
        return new Entity(Check.range(entityId, 0, Integer.MAX_VALUE, "place.entity"), null);
    }

    /**
     * Places the panel on a player. A player has at most {@link Limits#WORLD_PANELS} block and entity panels.
     *
     * @param player the UUID of the player the panel follows
     * @return the place
     * @throws InvalidUiException if the UUID is null
     */
    public static Entity player(UUID player) {
        return new Entity(-1, Check.notNull(player, "place.player").toString());
    }

    /**
     * Places the panel on the HUD. A player has at most {@link Limits#HUD_PANELS} HUD panels.
     *
     * @param anchor the screen anchor, or null for the client default
     * @return the place
     */
    public static Hud hud(Anchor anchor) {
        return new Hud(anchor);
    }

    /**
     * Places the panel beside container screens such as chests. Each side holds at most
     * {@link Limits#CONTAINER_PANELS_PER_SIDE}.
     *
     * @param side the side, or null for left
     * @return the place
     */
    public static Container container(Side side) {
        return new Container(side);
    }

    /** A place in the server menu. Create it with {@link Place#menu()} or {@link Place#menu(String)}. */
    public static final class Menu extends Place<Menu> {
        private String tab;
        private Side side;

        Menu(String tab) {
            super("menu");
            this.tab = tab;
        }

        /**
         * Sets the menu tab.
         *
         * @param tab the tab id, 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
         * @return this place
         * @throws InvalidUiException if the id is not valid
         */
        public Menu tab(String tab) {
            this.tab = Check.id(tab, "place.tab");
            return this;
        }

        /**
         * Sets which side of the menu the panel is shown on.
         *
         * @param side the side, or null for left
         * @return this place
         */
        public Menu side(Side side) {
            this.side = side;
            return this;
        }

        @Override
        void encodeOwn(JsonObject o) {
            Json.str(o, "tab", tab);
            if (side != null) o.addProperty("side", Check.lower(side));
        }

        @Override
        boolean rightSide() {
            return side == Side.RIGHT;
        }

        @Override
        String tab() {
            return tab;
        }
    }

    /** A place beside the player inventory. Create it with {@link Place#inventory(Side)}. */
    public static final class Inventory extends Place<Inventory> {
        private final Side side;
        private VAlign align;

        Inventory(Side side) {
            super("inventory");
            this.side = side;
        }

        /**
         * Sets the vertical alignment beside the inventory.
         *
         * @param align the alignment, or null for the client default {@link VAlign#TOP}
         * @return this place
         */
        public Inventory align(VAlign align) {
            this.align = align;
            return this;
        }

        @Override
        void encodeOwn(JsonObject o) {
            if (side != null) o.addProperty("side", Check.lower(side));
            if (align != null) o.addProperty("align", Check.lower(align));
        }

        @Override
        boolean rightSide() {
            return side == Side.RIGHT;
        }
    }

    /** A place beside container screens. Create it with {@link Place#container(Side)}. */
    public static final class Container extends Place<Container> {
        private final Side side;
        private VAlign align;
        private String title;

        Container(Side side) {
            super("container");
            this.side = side;
        }

        /**
         * Sets the vertical alignment beside the container screen.
         *
         * @param align the alignment, or null for the client default {@link VAlign#TOP}
         * @return this place
         */
        public Container align(VAlign align) {
            this.align = align;
            return this;
        }

        /**
         * Shows the panel only on container screens whose title contains this text. The client ignores case and
         * color codes.
         *
         * @param screenTitleContains the text, at most 64 characters
         * @return this place
         * @throws InvalidUiException if the text is null or too long
         */
        public Container title(String screenTitleContains) {
            this.title = Check.chars(screenTitleContains, 64, "place.title");
            return this;
        }

        @Override
        void encodeOwn(JsonObject o) {
            Json.str(o, "title", title);
            if (side != null) o.addProperty("side", Check.lower(side));
            if (align != null) o.addProperty("align", Check.lower(align));
        }

        @Override
        boolean rightSide() {
            return side == Side.RIGHT;
        }
    }

    /** A place with the chat. Create it with {@link Place#chat()}. */
    public static final class Chat extends Place<Chat> {
        Chat() {
            super("chat");
        }

        @Override
        void encodeOwn(JsonObject o) {
        }
    }

    /** A place on the HUD. Create it with {@link Place#hud(Anchor)}. */
    public static final class Hud extends Place<Hud> {
        private final Anchor anchor;

        Hud(Anchor anchor) {
            super("hud");
            this.anchor = anchor;
        }

        @Override
        void encodeOwn(JsonObject o) {
            if (anchor != null) o.addProperty("anchor", Check.lower(anchor));
        }
    }

    /**
     * Base class of the places in the world: front, block and entity.
     *
     * @param <T> the concrete place type, returned by the builder methods
     */
    public abstract static class World<T extends World<T>> extends Place<T> {
        private Face face;
        private Double yaw;
        private Double pitch;
        private Double roll;
        Double scale;
        Double reach;
        private Boolean blockAttack;

        World(String in) {
            super(in);
        }

        /**
         * Sets how the panel turns toward the player.
         *
         * @param face the mode, or null for the client default {@link Face#BILLBOARD_Y}
         * @return this place
         */
        public T face(Face face) {
            this.face = face;
            return self();
        }

        /**
         * Sets the yaw rotation.
         *
         * @param degrees -360 to 360
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public T yaw(double degrees) {
            this.yaw = Check.range(degrees, -360, 360, "place.yaw");
            return self();
        }

        /**
         * Sets the pitch rotation.
         *
         * @param degrees -90 to 90
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public T pitch(double degrees) {
            this.pitch = Check.range(degrees, -90, 90, "place.pitch");
            return self();
        }

        /**
         * Sets the roll rotation.
         *
         * @param degrees -360 to 360
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public T roll(double degrees) {
            this.roll = Check.range(degrees, -360, 360, "place.roll");
            return self();
        }

        /**
         * Sets the size of one panel pixel in the world.
         *
         * @param blocksPerPixel 0.005 to 0.1; the default is {@link Place#DEFAULT_SCALE}
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public T scale(double blocksPerPixel) {
            this.scale = Check.range(blocksPerPixel, 0.005, 0.1, "place.scale");
            return self();
        }

        /**
         * Sets how far away the player can click the panel. For block and entity panels the SDK uses it, default 6,
         * in {@link WorldAnchor#maxClickDistance()}.
         *
         * @param blocks 0.5 to 16
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public T reach(double blocks) {
            this.reach = Check.range(blocks, 0.5, 16, "place.reach");
            return self();
        }

        /**
         * Sets whether the attack key is held back from the world while the player aims at a button of this panel.
         * The client default is true.
         *
         * @param blockAttack false to let attacks through
         * @return this place
         */
        public T blockAttack(boolean blockAttack) {
            this.blockAttack = blockAttack;
            return self();
        }

        @Override
        boolean world() {
            return true;
        }

        void encodeWorld(JsonObject o) {
            if (face != null) o.addProperty("face", Check.lower(face));
            if (yaw != null) Json.num(o, "yaw", yaw);
            if (pitch != null) Json.num(o, "pitch", pitch);
            if (roll != null) Json.num(o, "roll", roll);
            if (scale != null) Json.num(o, "scale", scale);
            if (reach != null) Json.num(o, "reach", reach);
            if (blockAttack != null) o.addProperty("block_attack", blockAttack);
        }
    }

    /**
     * Base class of the places fixed to a block or an entity.
     *
     * @param <T> the concrete place type, returned by the builder methods
     */
    public abstract static class Anchored<T extends Anchored<T>> extends World<T> {
        double[] offset;
        private Double view;
        private boolean throughWalls;
        private String worldId;
        private String dim;
        private String stack;

        Anchored(String in) {
            super(in);
        }

        /**
         * Sets the panel position relative to the block corner or the entity.
         *
         * @param x the x offset in blocks, -16 to 16
         * @param y the y offset in blocks, -16 to 16
         * @param z the z offset in blocks, -16 to 16
         * @return this place
         * @throws InvalidUiException if a value is out of range
         */
        public T offset(double x, double y, double z) {
            this.offset = new double[]{Check.range(x, -16, 16, "place.offset"), Check.range(y, -16, 16, "place.offset"), Check.range(z, -16, 16, "place.offset")};
            return self();
        }

        /**
         * Sets the distance within which the panel is drawn. The client default is 48, and it never draws past the
         * player's render distance.
         *
         * @param blocks 4 to 128
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public T view(double blocks) {
            this.view = Check.range(blocks, 4, 128, "place.view");
            return self();
        }

        /**
         * Draws the panel through walls.
         *
         * @return this place
         */
        public T throughWalls() {
            this.throughWalls = true;
            return self();
        }

        /**
         * Sets the world the panel belongs to, as named with {@link Session#world(String)}.
         *
         * @param worldId 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
         * @return this place
         * @throws InvalidUiException if the id is missing or not valid
         */
        public T world(String worldId) {
            this.worldId = Check.id(worldId, "place.world");
            return self();
        }

        /**
         * Sets the dimension the panel belongs to.
         *
         * @param dimension the dimension id, at most 64 characters
         * @return this place
         * @throws InvalidUiException if the dimension is null or too long
         */
        public T dim(String dimension) {
            this.dim = Check.chars(dimension, 64, "place.dim");
            return self();
        }

        /**
         * Groups this panel with other panels that have the same stack id on the same block position and offset, or
         * on the same entity. The group is sorted by {@link #order(int)}, then by panel id. Ignored on a mounted block
         * panel.
         *
         * @param stackId 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
         * @return this place
         * @throws InvalidUiException if the id is missing or not valid
         */
        public T stack(String stackId) {
            this.stack = Check.id(stackId, "place.stack");
            return self();
        }

        String worldId() {
            return worldId;
        }

        String dim() {
            return dim;
        }

        void encodeAnchored(JsonObject o) {
            if (offset != null) o.add("offset", Json.numbers(offset));
            if (view != null) Json.num(o, "view", view);
            if (throughWalls) o.addProperty("through_walls", true);
            Json.str(o, "world", worldId);
            Json.str(o, "dim", dim);
            Json.str(o, "stack", stack);
            encodeWorld(o);
        }
    }

    /**
     * A place in the world in front of the player. Create it with {@link Place#front()}. The panel opens with
     * {@link Session#openPanel(String)} or {@link Action#openPanel(String)}.
     */
    public static final class Front extends World<Front> {
        private Double dist;
        private Double height;
        private Boolean closeOnKey;
        private Double closeWalk;
        private Long closeAfterMs;
        private boolean closeOnClick;

        Front() {
            super("front");
        }

        /**
         * Sets how far in front of the player the panel opens. The client default is 2.2.
         *
         * @param blocks 1 to 4
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public Front distance(double blocks) {
            this.dist = Check.range(blocks, 1, 4, "place.dist");
            return this;
        }

        /**
         * Sets the height of the panel relative to the player's eyes. The client default is -0.2.
         *
         * @param blocks -2 to 2
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public Front height(double blocks) {
            this.height = Check.range(blocks, -2, 2, "place.height");
            return this;
        }

        /**
         * Sets whether the close key closes the panel. The client default is true.
         *
         * @param close false to keep the panel open on the key
         * @return this place
         */
        public Front closeOnKey(boolean close) {
            this.closeOnKey = close;
            return this;
        }

        /**
         * Closes the panel when the player walks this far from it. The client default is 6.
         *
         * @param blocks 1 to 16
         * @return this place
         * @throws InvalidUiException if the value is out of range
         */
        public Front closeOnWalk(double blocks) {
            this.closeWalk = Check.range(blocks, 1, 16, "place.close.walk");
            return this;
        }

        /**
         * Closes the panel after a time.
         *
         * @param ms the time in milliseconds, 0 to 3600000 (1 hour); 0 means never, which is the client default
         * @return this place
         * @throws InvalidUiException if the time is out of range
         */
        public Front closeAfter(long ms) {
            this.closeAfterMs = Check.range(ms, 0, 3600000, "place.close.after_ms");
            return this;
        }

        /**
         * Closes the panel after any of its buttons is pressed.
         *
         * @return this place
         */
        public Front closeOnClick() {
            this.closeOnClick = true;
            return this;
        }

        @Override
        void encodeOwn(JsonObject o) {
            if (dist != null) Json.num(o, "dist", dist);
            if (height != null) Json.num(o, "height", height);
            if (closeOnKey != null || closeWalk != null || closeAfterMs != null || closeOnClick) {
                JsonObject close = new JsonObject();
                if (closeOnKey != null) close.addProperty("key", closeOnKey);
                if (closeWalk != null) Json.num(close, "walk", closeWalk);
                if (closeAfterMs != null) close.addProperty("after_ms", closeAfterMs);
                if (closeOnClick) close.addProperty("on_click", true);
                o.add("close", close);
            }
            encodeWorld(o);
        }
    }

    /**
     * A place at a block. Create it with {@link Place#block(int, int, int)}. The client default offset is 0.5, 1.5, 0.5.
     */
    public static final class Block extends Anchored<Block> {
        final int x;
        final int y;
        final int z;
        private Mount mount;

        Block(int x, int y, int z) {
            super("block");
            this.x = Check.range(x, -30000000, 30000000, "place.pos");
            this.y = Check.range(y, -30000000, 30000000, "place.pos");
            this.z = Check.range(z, -30000000, 30000000, "place.pos");
        }

        /**
         * Mounts the panel on a face of the block. The client then uses {@link Face#FIXED}.
         *
         * @param face the block face, or null to not mount it
         * @return this place
         */
        public Block mount(Mount face) {
            this.mount = face;
            return this;
        }

        @Override
        void encodeOwn(JsonObject o) {
            o.add("pos", Json.numbers(x, y, z));
            if (mount != null) o.addProperty("mount", Check.lower(mount));
            encodeAnchored(o);
        }
    }

    /**
     * A place on an entity or a player. Create it with {@link Place#entity(int)} or {@link Place#player(UUID)}.
     */
    public static final class Entity extends Anchored<Entity> {
        final int entityId;
        final String player;

        Entity(int entityId, String player) {
            super("entity");
            this.entityId = entityId;
            this.player = player;
        }

        @Override
        void encodeOwn(JsonObject o) {
            if (player != null) o.addProperty("player", player);
            else o.addProperty("entity", entityId);
            encodeAnchored(o);
        }
    }
}
