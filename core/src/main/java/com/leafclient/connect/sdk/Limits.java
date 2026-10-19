package com.leafclient.connect.sdk;

/**
 * The limits Leaf Client enforces, as the SDK checks them. The SDK throws {@link InvalidUiException} before sending
 * anything that would break one, and its tests compare these values with the client's own limits.
 */
public final class Limits {

    /** Largest plugin message the SDK sends, in bytes. */
    public static final int SERVER_MESSAGE_BYTES = 64 * 1024;
    /** Plugin message size the Bukkit adapter uses when the server's plugin message limit is 32766 bytes or less, or unknown. */
    public static final int LEGACY_MESSAGE_BYTES = 30000;
    /** Largest message the client accepts after it joins the chunks of a message split into parts, in bytes. */
    public static final int CHUNKED_MESSAGE_BYTES = 512 * 1024;
    /** Most chunks one message may be split into. */
    public static final int CHUNK_PARTS = 16;
    /** Deepest JSON nesting the client reads. Two levels are taken by the batch around each message. */
    public static final int JSON_DEPTH = 32;
    /** Most messages in one batch frame. */
    public static final int BATCH_MESSAGES = 64;

    /** Most panels one player can have at once. */
    public static final int PANELS = 96;
    /** Most block and entity panels one player can have at once. */
    public static final int WORLD_PANELS = 64;
    /** Most front panels one player can have at once. */
    public static final int FRONT_PANELS = 8;
    /** Most inventory panels on one side of the inventory. */
    public static final int INVENTORY_PANELS_PER_SIDE = 4;
    /** Most container panels on one side of a container screen. */
    public static final int CONTAINER_PANELS_PER_SIDE = 4;
    /** Most chat panels one player can have at once. */
    public static final int CHAT_PANELS = 4;
    /** Most HUD panels one player can have at once. */
    public static final int HUD_PANELS = 6;
    /** Most tabs in the server menu. */
    public static final int MENU_TABS = 8;
    /** Most panels in one menu tab. */
    public static final int PANELS_PER_TAB = 4;
    /** Most elements in one panel, counting every nested element. */
    public static final int ELEMENTS_PER_PANEL = 128;
    /** Most buttons in one panel. */
    public static final int BUTTONS_PER_PANEL = 48;
    /** Deepest element nesting in one panel. The root element is depth 1. */
    public static final int ELEMENT_DEPTH = 8;
    /** Largest panel width or height, and largest element size and position, in pixels. */
    public static final int PANEL_MAX_SIZE = 512;
    /** Largest width the client lays out for a button, in pixels. */
    public static final int BUTTON_MAX_W = 512;
    /** Largest height the client lays out for a button, in pixels. */
    public static final int BUTTON_MAX_H = 256;
    /** Most visible characters in one text element, button label or patched text. */
    public static final int TEXT_CHARS = 512;
    /** Most values the client holds for one server. */
    public static final int VALUES = 256;
    /** Longest value key, in characters. */
    public static final int VALUE_KEY_CHARS = 48;
    /** Longest value, in characters. */
    public static final int VALUE_CHARS = 256;
    /** Most server waypoints one player can have at once. */
    public static final int WAYPOINTS = 64;
    /** Longest id, in characters. Ids use {@code a-z 0-9 _ . : / -}. */
    public static final int ID_CHARS = 64;
    /** Longest click token, in characters. */
    public static final int TOKEN_CHARS = 64;
    /** Most inputs in one panel. */
    public static final int INPUTS_PER_PANEL = 16;
    /** Longest text input value, in characters. */
    public static final int INPUT_CHARS = 256;
    /** Most options in one choice input. */
    public static final int CHOICE_OPTIONS = 16;
    /** Most images in one panel. */
    public static final int IMAGES_PER_PANEL = 16;
    /** Most player models in one panel, counting leaderboard podium places. */
    public static final int PLAYERS_PER_PANEL = 8;
    /** Most actions on one button, toast button or waypoint. */
    public static final int ACTIONS = 4;
    /** Most buttons on one toast. */
    public static final int TOAST_BUTTONS = 2;
    /** Most rows in one leaderboard. */
    public static final int LEADERBOARD_ROWS = 20;
    /** Deepest nesting of {@link Condition#all}, {@link Condition#any} and {@link Condition#not}. A single test is depth 1. */
    public static final int CONDITION_DEPTH = 4;
    /** Most tests in one condition. */
    public static final int CONDITION_TESTS = 16;
    /** Most lines in one tooltip. */
    public static final int TOOLTIP_LINES = 20;
    /** Most rows in one list element. */
    public static final int LIST_ROWS = 64;
    /** Most players in one heads element. */
    public static final int HEADS = 32;
    /** Most parts in one button label made with {@link Button#labelParts(Object...)}. */
    public static final int LABEL_PARTS = 32;
    /** Most mod ids in one lock or unlock message. */
    public static final int MOD_IDS = 128;

    /** Messages per second the client accepts from a server. The SDK sends at most 80% of this. */
    public static final int MESSAGES_PER_SECOND = 40;
    /** Burst of messages the client accepts from a server. The SDK uses at most 80% of this. */
    public static final int MESSAGES_BURST = 200;
    /** Mod lock and unlock messages per second the client accepts. The SDK sends at most 80% of this. */
    public static final int LOCKS_PER_SECOND = 2;
    /** Shortest time between two menu or panel opens, in milliseconds. The SDK adds 150 ms of margin. */
    public static final long OPEN_INTERVAL_MS = 2000;
    /** Shortest time between the player closing the menu or a panel and the next open, in milliseconds. The SDK adds 150 ms of margin. */
    public static final long OPEN_AFTER_CLOSE_MS = 1000;

    private Limits() {}
}
