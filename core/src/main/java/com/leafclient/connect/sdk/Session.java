package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * One Leaf Client player. Get it from {@link LeafConnect#onReady} or {@link LeafConnect#session(UUID)}.
 * Messages are checked against the client limits when queued, then batched, split into chunks when too large, and sent
 * on {@link #flush()} within the client rate limits. The session records the panels, toasts, waypoints and values it
 * sent, so it can enforce the per player limits and check the clicks that come back. Methods are thread safe.
 *
 * <p>At most 512 messages wait to be sent per player, about 16 seconds of sending at the client rate limit. While
 * that many wait, new messages are dropped: {@link #send(Message)} returns false, the other methods change nothing,
 * and a warning is logged at most once every 5 seconds. A new {@code hello} from the client drops what was still
 * waiting, except mod locks and unlocks, and the session forgets the panels whose messages were dropped.
 */
public final class Session {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int TOKEN_LENGTH = 12;
    private static final long TTL_GRACE_MS = 3000;
    private static final long COOLDOWN_TOLERANCE_MS = 250;
    static final long TOKEN_GRACE_MS = 3000;
    private static final int MAX_PRESSES = Limits.PANELS * Limits.BUTTONS_PER_PANEL + Limits.TOAST_BUTTONS * 32;
    static final int MESSAGES_PER_SECOND = 40;
    private static final Set<String> RAW_TYPES = new HashSet<String>(java.util.Arrays.asList(
            "welcome", "panel.set", "panel.patch", "panel.remove", "panel.open", "panel.close", "values.set", "menu.set", "menu.remove",
            "menu.open", "menu.close", "key.set", "toast", "toast.remove", "waypoint.set", "waypoint.remove", "world.set", "mods.lock",
            "mods.unlock", "dialog.style", "reset"));

    private static final class ToastRecord {
        final String token;
        final Map<String, Encoder.ButtonSpec> buttons;

        ToastRecord(String token, Map<String, Encoder.ButtonSpec> buttons) {
            this.token = token;
            this.buttons = buttons;
        }
    }

    private static final class WaypointRecord {
        final String token;
        final Consumer<Click> handler;
        final Set<String> data;
        final long expiresAt;

        WaypointRecord(String token, Consumer<Click> handler, Set<String> data, long expiresAt) {
            this.token = token;
            this.handler = handler;
            this.data = data;
            this.expiresAt = expiresAt;
        }
    }

    static final class Resolved {
        final Click click;
        final Consumer<Click> handler;
        final Rejection rejection;
        final boolean unknown;

        Resolved(Click click, Consumer<Click> handler, Rejection rejection, boolean unknown) {
            this.click = click;
            this.handler = handler;
            this.rejection = rejection;
            this.unknown = unknown;
        }
    }

    private final LeafConnect connect;
    private final UUID id;
    private final String name;
    private final Object player;
    private final Transport transport;
    private final int cap;
    private ClientInfo client;
    private Prefs prefs;
    private final Outbox outbox;
    final Object sendLock = new Object();
    private List<byte[]> unsent;
    private final Map<String, PanelRecord> panels = new LinkedHashMap<String, PanelRecord>();
    private final Map<String, ToastRecord> toasts = new LinkedHashMap<String, ToastRecord>(16, 0.75f, false) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ToastRecord> eldest) {
            return size() > 32;
        }
    };
    private final Map<String, WaypointRecord> waypoints = new LinkedHashMap<String, WaypointRecord>();
    private final Set<String> values = new HashSet<String>();
    private final Map<String, Long> lastPress = new LinkedHashMap<String, Long>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_PRESSES;
        }
    };
    private final Bucket clickRate = new Bucket(32, 64);
    private String worldId;
    final Bucket messageRate = new Bucket(MESSAGES_PER_SECOND, 2 * MESSAGES_PER_SECOND);
    final Throttle warnings = new Throttle(0.2, 5);
    final Throttle failures = new Throttle(0.2, 5);
    private long lastSeq;
    private long nextExpire = Long.MIN_VALUE;

    Session(LeafConnect connect, UUID id, String name, Object player, Transport transport, ClientInfo client, Prefs prefs) {
        this.connect = connect;
        this.id = id;
        this.name = name;
        this.player = player;
        this.transport = transport;
        this.cap = Math.min(Limits.SERVER_MESSAGE_BYTES, transport.maxMessageBytes());
        this.outbox = new Outbox(cap);
        this.client = client;
        this.prefs = prefs;
    }

    /**
     * Returns the player UUID.
     *
     * @return the UUID
     */
    public UUID id() {
        return id;
    }

    /**
     * Returns the player name.
     *
     * @return the name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the platform player object, such as a Bukkit {@code Player}.
     *
     * @param <P> the platform player type
     * @return the player
     * @throws ClassCastException if {@code P} is not the platform player type
     */
    @SuppressWarnings("unchecked")
    public <P> P player() {
        return (P) player;
    }

    /**
     * Returns what the client sent in its last hello.
     *
     * @return the client info
     */
    public synchronized ClientInfo client() {
        return client;
    }

    /**
     * Tells whether the client supports a feature.
     *
     * @param cap the cap name, such as {@code toast} or {@code mods.lock}
     * @return true if the client listed it
     */
    public boolean has(String cap) {
        return client().has(cap);
    }

    /**
     * Returns the player's Server Connect settings for this server.
     *
     * @return the settings
     */
    public synchronized Prefs prefs() {
        return prefs;
    }

    /**
     * Returns the largest frame this session sends: the smaller of {@link Limits#SERVER_MESSAGE_BYTES} and the
     * transport maximum.
     *
     * @return the size in bytes
     */
    public int maxMessageBytes() {
        return cap;
    }

    /**
     * Checks and queues a message. A message that needs a cap the client did not list is skipped and logged at debug.
     *
     * @param message the message
     * @return true if it was queued, false if the client lacks the cap or 512 messages already wait to be sent
     * @throws InvalidUiException if the message breaks a client limit, including the per player limits
     */
    public synchronized boolean send(Message message) {
        String required = message.requiredCap();
        if (required != null && !client.has(required)) {
            if (connect.log().debugEnabled()) connect.log().debug("not sent to " + name + ": the client does not list " + required + " in its caps");
            return false;
        }
        return enqueue(message.encode(this));
    }

    /**
     * Sends messages in order with {@link #send(Message)}.
     *
     * @param messages the messages
     * @return true if all were queued
     * @throws InvalidUiException if a message breaks a client limit; the messages before it stay queued
     */
    public synchronized boolean sendAll(Message... messages) {
        boolean all = true;
        for (Message m : messages) all &= send(m);
        return all;
    }

    /**
     * Sets one value, as {@code send(Values.of(key, value))}.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param value a number, boolean or text
     * @return this session
     * @throws InvalidUiException if the key or value is not valid, or the client would hold more than
     *         {@link Limits#VALUES} values
     */
    public Session setValue(String key, Object value) {
        send(Values.of(key, value));
        return this;
    }

    /**
     * Opens the server menu. See {@link #openMenu(String)}.
     *
     * @return this session
     */
    public synchronized Session openMenu() {
        return openMenu(null);
    }

    /**
     * Opens the server menu on a tab. Opens are paced: the SDK sends one after everything queued before it, at
     * least {@link Limits#OPEN_INTERVAL_MS} after the last open and {@link Limits#OPEN_AFTER_CLOSE_MS} after the player
     * closed the menu or a panel, plus 150 ms. A newer open replaces one that is still waiting.
     *
     * @param tab the tab id, or null for the default tab
     * @return this session
     * @throws InvalidUiException if the tab id is not valid
     */
    public synchronized Session openMenu(String tab) {
        JsonObject o = Json.envelope("menu.open");
        if (tab != null) o.addProperty("tab", Check.id(tab, "menu.open.tab"));
        outbox.open(new Outbox.Item(check(o), "menu.open" + (tab != null ? " " + tab : ""), "menu.open"));
        return this;
    }

    /**
     * Closes the server menu and cancels a menu open that is still waiting.
     *
     * @return this session
     */
    public synchronized Session closeMenu() {
        if ("menu.open".equals(outbox.pendingOpenType())) outbox.cancelOpen();
        return control(Json.envelope("menu.close"), "menu.close", null);
    }

    /**
     * Removes the server menu from the client and cancels a menu open that is still waiting.
     *
     * @return this session
     */
    public synchronized Session removeMenu() {
        if ("menu.open".equals(outbox.pendingOpenType())) outbox.cancelOpen();
        return control(Json.envelope("menu.remove"), "menu.remove", null);
    }

    /**
     * Opens a front panel already sent to this player. Paced like {@link #openMenu(String)}.
     *
     * @param frontPanelId the panel id
     * @return this session
     * @throws InvalidUiException if the id is not valid or no front panel with that id was sent to this player
     */
    public synchronized Session openPanel(String frontPanelId) {
        PanelRecord p = panels.get(Check.id(frontPanelId, "panel.open.id"));
        if (p == null || !"front".equals(p.in)) throw Check.fail("panel.open " + frontPanelId, "no front panel " + frontPanelId + " was sent to " + name);
        JsonObject o = Json.envelope("panel.open");
        o.addProperty("id", frontPanelId);
        outbox.open(new Outbox.Item(check(o), "panel.open " + frontPanelId, "panel.open"));
        return this;
    }

    /**
     * Closes a front panel and cancels an open of it that is still waiting. The panel stays on the client.
     *
     * @param frontPanelId the panel id
     * @return this session
     * @throws InvalidUiException if the id is not valid or no front panel with that id was sent to this player
     */
    public synchronized Session closePanel(String frontPanelId) {
        PanelRecord p = panels.get(Check.id(frontPanelId, "panel.close.id"));
        if (p == null || !"front".equals(p.in)) throw Check.fail("panel.close " + frontPanelId, "no front panel " + frontPanelId + " was sent to " + name);
        if (("panel.open " + frontPanelId).equals(outbox.pendingOpenLabel())) outbox.cancelOpen();
        JsonObject o = Json.envelope("panel.close");
        o.addProperty("id", frontPanelId);
        return control(o, "panel.close " + frontPanelId, null);
    }

    /**
     * Removes panels from the client. Clicks on them are then rejected.
     *
     * @param ids at most 128 panel ids; none does nothing
     * @return this session
     * @throws InvalidUiException if an id is not valid or there are more than 128
     */
    public synchronized Session removePanels(String... ids) {
        if (ids.length == 0) return this;
        Check.count(ids.length, 128, "panel.remove.ids", "ids");
        final List<String> list = new ArrayList<String>();
        for (String i : ids) list.add(Check.id(i, "panel.remove.ids"));
        JsonObject o = Json.envelope("panel.remove");
        o.add("ids", Json.strings(list));
        return control(o, "panel.remove " + list, new Runnable() {
            @Override
            public void run() {
                for (String i : list) panels.remove(i);
                cancelOpenIfGone();
            }
        });
    }

    /**
     * Removes every panel in a group set with {@link Panel#group(String)}.
     *
     * @param group the group id
     * @return this session
     * @throws InvalidUiException if the id is not valid
     */
    public synchronized Session removeGroup(final String group) {
        JsonObject o = Json.envelope("panel.remove");
        o.addProperty("group", Check.id(group, "panel.remove.group"));
        return control(o, "panel.remove group " + group, new Runnable() {
            @Override
            public void run() {
                Iterator<PanelRecord> it = panels.values().iterator();
                while (it.hasNext()) if (group.equals(it.next().group)) it.remove();
                cancelOpenIfGone();
            }
        });
    }

    /**
     * Removes every panel from the client.
     *
     * @return this session
     */
    public synchronized Session removeAllPanels() {
        JsonObject o = Json.envelope("panel.remove");
        o.addProperty("all", true);
        return control(o, "panel.remove all", new Runnable() {
            @Override
            public void run() {
                panels.clear();
                cancelOpenIfGone();
            }
        });
    }

    /**
     * Makes the client's Server Connect key open the server menu.
     *
     * @return this session
     */
    public synchronized Session keyOpensMenu() {
        return key("menu");
    }

    /**
     * Makes the client's Server Connect key open a front panel.
     *
     * @param panelId the panel id
     * @return this session
     * @throws InvalidUiException if the id is not valid
     */
    public synchronized Session keyOpensPanel(String panelId) {
        return key("panel:" + Check.id(panelId, "key.set.opens"));
    }

    /**
     * Makes the client's Server Connect key do nothing for this server.
     *
     * @return this session
     */
    public synchronized Session keyDoesNothing() {
        return key("none");
    }

    /**
     * Tells the client which world the player is in. Places, waypoints and conditions that name a world use it.
     *
     * @param worldId 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return this session
     * @throws InvalidUiException if the id is not valid
     */
    public synchronized Session world(final String worldId) {
        JsonObject o = Json.envelope("world.set");
        o.addProperty("id", Check.id(worldId, "world.set.id"));
        return control(o, "world.set " + worldId, new Runnable() {
            @Override
            public void run() {
                Session.this.worldId = worldId;
            }
        });
    }

    /**
     * Removes a toast.
     *
     * @param toastId the toast id
     * @return this session
     * @throws InvalidUiException if the id is not valid
     */
    public synchronized Session removeToast(final String toastId) {
        JsonObject o = Json.envelope("toast.remove");
        o.addProperty("id", Check.id(toastId, "toast.remove.id"));
        return control(o, "toast.remove " + toastId, new Runnable() {
            @Override
            public void run() {
                toasts.remove(toastId);
            }
        });
    }

    /**
     * Removes server waypoints.
     *
     * @param ids at most 128 waypoint ids; none does nothing
     * @return this session
     * @throws InvalidUiException if an id is not valid or there are more than 128
     */
    public synchronized Session removeWaypoints(String... ids) {
        if (ids.length == 0) return this;
        Check.count(ids.length, 128, "waypoint.remove.ids", "ids");
        final List<String> list = new ArrayList<String>();
        for (String i : ids) list.add(Check.id(i, "waypoint.remove.ids"));
        JsonObject o = Json.envelope("waypoint.remove");
        o.add("ids", Json.strings(list));
        return control(o, "waypoint.remove " + list, new Runnable() {
            @Override
            public void run() {
                for (String i : list) waypoints.remove(i);
            }
        });
    }

    /**
     * Removes every server waypoint.
     *
     * @return this session
     */
    public synchronized Session removeAllWaypoints() {
        JsonObject o = Json.envelope("waypoint.remove");
        o.addProperty("all", true);
        return control(o, "waypoint.remove all", new Runnable() {
            @Override
            public void run() {
                waypoints.clear();
            }
        });
    }

    /**
     * Locks client mods for this server. Does nothing when the client does not list the {@code mods.lock} cap.
     * Lock and unlock messages are paced separately, below {@link Limits#LOCKS_PER_SECOND}.
     *
     * @param reason legacy text shown to the player, at most 128 visible characters, or null
     * @param modIds 1 to {@link Limits#MOD_IDS} mod ids, see {@link ClientInfo#lockableMods()}
     * @return this session
     * @throws InvalidUiException if no id is given, an id is not valid, there are too many, or the reason is too long
     */
    public synchronized Session lockMods(String reason, String... modIds) {
        return lockMods(Text.from(reason), modIds);
    }

    /**
     * Locks client mods for this server. Does nothing when the client does not list the {@code mods.lock} cap.
     *
     * @param reason the reason shown to the player, at most 128 visible characters, or null
     * @param modIds 1 to {@link Limits#MOD_IDS} mod ids, see {@link ClientInfo#lockableMods()}
     * @return this session
     * @throws InvalidUiException if no id is given, an id is not valid, there are too many, or the reason is too long
     */
    public synchronized Session lockMods(Text reason, String... modIds) {
        if (!client.has("mods.lock")) return this;
        JsonObject o = Json.envelope("mods.lock");
        o.add("ids", modIds("mods.lock.ids", modIds));
        if (reason != null) o.add("reason", Check.text(reason, 128, "mods.lock.reason").toJson());
        return control(o, "mods.lock", null);
    }

    /**
     * Unlocks client mods. Does nothing when the client does not list the {@code mods.lock} cap.
     *
     * @param modIds 1 to {@link Limits#MOD_IDS} mod ids
     * @return this session
     * @throws InvalidUiException if no id is given, an id is not valid or there are too many
     */
    public synchronized Session unlockMods(String... modIds) {
        if (!client.has("mods.lock")) return this;
        JsonObject o = Json.envelope("mods.unlock");
        o.add("ids", modIds("mods.unlock.ids", modIds));
        return control(o, "mods.unlock", null);
    }

    /**
     * Unlocks every mod this server locked. Does nothing when the client does not list the {@code mods.lock} cap.
     *
     * @return this session
     */
    public synchronized Session unlockAllMods() {
        if (!client.has("mods.lock")) return this;
        JsonObject o = Json.envelope("mods.unlock");
        o.addProperty("all", true);
        return control(o, "mods.unlock all", null);
    }

    /**
     * Clears the UI this server sent and keeps mod locks. See {@link #reset(boolean)}.
     *
     * @return this session
     */
    public synchronized Session reset() {
        return reset(true);
    }

    /**
     * Sends {@code reset}: clears the UI this server sent. The session forgets its panels, toasts, waypoints and
     * values, and cancels an open that is still waiting.
     *
     * @param keepLocks false to also release mod locks
     * @return this session
     */
    public synchronized Session reset(boolean keepLocks) {
        JsonObject o = Json.envelope("reset");
        if (!keepLocks) o.addProperty("keep_locks", false);
        outbox.cancelOpen();
        return control(o, "reset", new Runnable() {
            @Override
            public void run() {
                panels.clear();
                toasts.clear();
                waypoints.clear();
                values.clear();
            }
        });
    }

    /**
     * Queues a hand written Server Connect message in its own batch. The SDK adds {@code "v":1} when missing and
     * checks the JSON depth and size, but not the fields, and does not record it for click checks. Lock messages are
     * dropped when the client does not list the {@code mods.lock} cap.
     *
     * @param rawJson a JSON object whose {@code t} is a server message type such as {@code panel.set} or
     *                {@code toast}; not {@code batch} or {@code chunk}
     * @return this session
     * @throws InvalidUiException if the text is not a JSON object, has no {@code t}, names an unknown type, or is too
     *         deep or too large
     */
    public synchronized Session sendRaw(String rawJson) {
        JsonObject o;
        try {
            @SuppressWarnings("deprecation")
            JsonElement e = new com.google.gson.JsonParser().parse(rawJson);
            o = e.getAsJsonObject();
        } catch (RuntimeException ex) {
            throw new InvalidUiException("raw message: not a JSON object");
        }
        if (!o.has("v")) o.addProperty("v", 1);
        if (!o.has("t") || !o.get("t").isJsonPrimitive()) throw new InvalidUiException("raw message: missing t");
        String type = o.get("t").getAsString();
        if ("batch".equals(type) || "chunk".equals(type)) throw new InvalidUiException("raw message: the SDK batches and chunks for you, send the inner messages");
        if (!RAW_TYPES.contains(type)) throw new InvalidUiException("raw message: " + type + " is not a Server Connect message type");
        if (("mods.lock".equals(type) || "mods.unlock".equals(type)) && !client.has("mods.lock")) return this;
        enqueue(new Outgoing(o, type + " (raw)", null), true);
        return this;
    }

    /**
     * Sends what this session has queued now, as far as the client rate limits allow, instead of waiting for the
     * platform timer.
     */
    public void flush() {
        connect.flushSession(this);
    }

    synchronized List<byte[]> drain(long now) {
        if (now >= nextExpire) {
            expire(now);
            nextExpire = now + 1000;
        }
        if (unsent != null) {
            List<byte[]> retry = unsent;
            unsent = null;
            return retry;
        }
        if (outbox.idle()) return java.util.Collections.emptyList();
        List<byte[]> out = outbox.drain(now, cap);
        String problem = outbox.takeProblem();
        if (problem != null) {
            String line = warnings.admit("dropped Server Connect messages for " + name + ": " + problem, now);
            if (line != null) connect.log().warn(line);
        }
        return out;
    }

    synchronized void keepUnsent(List<byte[]> frames) {
        if (!frames.isEmpty()) unsent = new ArrayList<byte[]>(frames);
    }

    Transport transport() {
        return transport;
    }

    synchronized boolean idle() {
        return unsent == null && outbox.idle();
    }

    long now() {
        return connect.now();
    }

    String newToken() {
        char[] out = new char[TOKEN_LENGTH];
        byte[] random = new byte[TOKEN_LENGTH + 4];
        int limit = 256 - 256 % ALPHABET.length;
        int filled = 0;
        while (filled < out.length) {
            RANDOM.nextBytes(random);
            for (int i = 0; i < random.length && filled < out.length; i++) {
                int b = random[i] & 0xff;
                if (b < limit) out[filled++] = ALPHABET[b % ALPHABET.length];
            }
        }
        return new String(out);
    }

    synchronized void rehello(ClientInfo info, Prefs prefs) {
        this.client = info;
        this.prefs = prefs;
        Iterator<PanelRecord> it = panels.values().iterator();
        while (it.hasNext()) if (!it.next().connectionScope) it.remove();
        toasts.clear();
        waypoints.clear();
        values.clear();
        unsent = null;
        for (String dropped : outbox.clearQueued()) panels.remove(dropped);
        long now = now();
        for (PanelRecord p : new ArrayList<PanelRecord>(panels.values())) {
            if (p.expired(now)) continue;
            Patch patch = null;
            for (Encoder.ButtonSpec b : p.buttons.values()) {
                long left = cooldownLeft(p.id, b, now);
                if (left <= 0) continue;
                if (patch == null) patch = Patch.of(p.id);
                patch.set(b.id, "cooldown_left", left);
            }
            if (patch != null) send(patch);
        }
    }

    synchronized void updatePrefs(Prefs prefs) {
        this.prefs = prefs;
    }

    synchronized void noteClosed(Closed closed) {
        if (closed.what() == Closed.What.MENU || (closed.what() == Closed.What.PANEL && closed.byPlayer())) outbox.playerClosed(now());
        if (closed.what() == Closed.What.TOAST && closed.id() != null) toasts.remove(closed.id());
    }

    synchronized String describeRef(String ref) {
        return outbox.describe(ref);
    }

    synchronized PanelRecord panelRecord(String panelId) {
        PanelRecord p = panels.get(panelId);
        return p == null || p.expired(now()) ? null : p;
    }

    void putPanel(PanelRecord record) {
        long now = now();
        PanelRecord old = panels.remove(record.id);
        if (old != null && !old.token.equals(record.token) && !old.expired(now)) record.keepOldToken(old.token, old, now + TOKEN_GRACE_MS);
        panels.put(record.id, record);
        for (Encoder.ButtonSpec b : record.buttons.values()) {
            if (b.cooldownLeft != null) setCooldownLeft(record.id, b.id, b.cooldownMs, b.cooldownLeft, now);
        }
    }

    long cooldownLeft(String panelId, Encoder.ButtonSpec b, long now) {
        if (!b.onePressOneClick || b.cooldownMs <= 0) return 0;
        Long last = lastPress.get(panelId + '\n' + b.id);
        return last == null ? 0 : Math.max(0, b.cooldownMs - (now - last));
    }

    void setCooldownLeft(String panelId, String buttonId, long cooldownMs, long left, long now) {
        String key = panelId + '\n' + buttonId;
        if (left > 0 && cooldownMs > 0) lastPress.put(key, now - cooldownMs + Math.min(left, cooldownMs));
        else lastPress.remove(key);
    }

    void putToast(String toastId, String token, Map<String, Encoder.ButtonSpec> buttons) {
        toasts.remove(toastId);
        if (token != null) toasts.put(toastId, new ToastRecord(token, buttons));
    }

    void putWaypoint(String waypointId, String token, Consumer<Click> handler, Set<String> data, long expiresAt) {
        waypoints.remove(waypointId);
        waypoints.put(waypointId, new WaypointRecord(token, handler, data, expiresAt));
    }

    Object worldKey() {
        return connect.worldOf(this);
    }

    void applyValues(List<String> set, List<String> removed) {
        values.removeAll(removed);
        values.addAll(set);
    }

    void checkValueLimit(List<String> set, List<String> removed) {
        int after = values.size();
        for (String k : set) if (!values.contains(k)) after++;
        for (String k : removed) if (values.contains(k) && !set.contains(k)) after--;
        if (after > Limits.VALUES) throw Check.fail("values.set", name + " would have " + after + " values, the client limit is " + Limits.VALUES + " (VALUES)");
    }

    void checkWaypointLimit(String waypointId) {
        expire(now());
        if (!waypoints.containsKey(waypointId) && waypoints.size() >= Limits.WAYPOINTS) {
            throw Check.fail("waypoint \"" + waypointId + "\"", name + " already has " + Limits.WAYPOINTS + " server waypoints (WAYPOINTS)");
        }
    }

    void checkPanelLimits(PanelRecord p) {
        checkPanelLimits(p.id, p.in, p.rightSide, p.tab);
    }

    void checkPanelLimits(String id, String in, boolean rightSide, String tab) {
        expire(now());
        int total = 0;
        int world = 0;
        int front = 0;
        int chat = 0;
        int hud = 0;
        int sameSide = 0;
        int containerSide = 0;
        int sameTab = 0;
        boolean isWorld = "block".equals(in) || "entity".equals(in);
        for (PanelRecord other : panels.values()) {
            if (other.id.equals(id)) continue;
            total++;
            if (other.world()) world++;
            if ("front".equals(other.in)) front++;
            if ("chat".equals(other.in)) chat++;
            if ("hud".equals(other.in)) hud++;
            if ("inventory".equals(other.in) && "inventory".equals(in) && other.rightSide == rightSide) sameSide++;
            if ("container".equals(other.in) && "container".equals(in) && other.rightSide == rightSide) containerSide++;
            if ("menu".equals(other.in) && "menu".equals(in) && eq(other.tab, tab)) sameTab++;
        }
        String at = "panel \"" + id + "\"";
        if (total + 1 > Limits.PANELS) throw limit(at, Limits.PANELS + " panels", "PANELS");
        if (isWorld && world + 1 > Limits.WORLD_PANELS) throw limit(at, Limits.WORLD_PANELS + " block and entity panels", "WORLD_PANELS");
        if ("front".equals(in) && front + 1 > Limits.FRONT_PANELS) throw limit(at, Limits.FRONT_PANELS + " front panels", "FRONT_PANELS");
        if ("chat".equals(in) && chat + 1 > Limits.CHAT_PANELS) throw limit(at, Limits.CHAT_PANELS + " chat panels", "CHAT_PANELS");
        if ("hud".equals(in) && hud + 1 > Limits.HUD_PANELS) throw limit(at, Limits.HUD_PANELS + " HUD panels", "HUD_PANELS");
        if ("inventory".equals(in) && sameSide + 1 > Limits.INVENTORY_PANELS_PER_SIDE) {
            throw limit(at, Limits.INVENTORY_PANELS_PER_SIDE + " inventory panels on one side", "INVENTORY_PANELS_PER_SIDE");
        }
        if ("container".equals(in) && containerSide + 1 > Limits.CONTAINER_PANELS_PER_SIDE) {
            throw limit(at, Limits.CONTAINER_PANELS_PER_SIDE + " container panels on one side", "CONTAINER_PANELS_PER_SIDE");
        }
        if ("menu".equals(in) && sameTab + 1 > Limits.PANELS_PER_TAB) {
            throw limit(at, Limits.PANELS_PER_TAB + " panels in menu tab " + (tab == null ? "(none)" : tab), "PANELS_PER_TAB");
        }
    }

    private InvalidUiException limit(String at, String what, String constant) {
        return Check.fail(at, name + " already has " + what + ", the client limit (" + constant + "); remove one first");
    }

    synchronized Resolved resolveClick(JsonObject m, long now) {
        String src = Json.getString(m, "src");
        String panel = Json.getString(m, "panel");
        String button = Json.getString(m, "button");
        String token = Json.getString(m, "token");
        String data = Json.getString(m, "data");
        boolean confirmed = Json.getBool(m, "confirmed", false);
        Long seqValue = Json.integer(m.get("seq"));
        if (src == null || panel == null || button == null || seqValue == null) {
            return reject(Rejection.Reason.MALFORMED, panel, button, -1, "missing src, panel, button or seq");
        }
        long seq = seqValue;
        if (seq <= lastSeq) return reject(Rejection.Reason.REPLAY, panel, button, seq, "seq ", seq, " is not above ", lastSeq);
        lastSeq = seq;
        if (!clickRate.take(1, now)) return reject(Rejection.Reason.RATE, panel, button, seq, "more than 32 clicks per second");
        if ("toast".equals(src)) {
            ToastRecord t = toasts.get(panel);
            if (t == null) return unknown(panel, button, seq);
            if (!t.token.equals(token)) return reject(Rejection.Reason.BAD_TOKEN, panel, button, seq, "token does not match the last toast ", panel);
            Encoder.ButtonSpec b = t.buttons.get(button);
            if (b == null || b.data.isEmpty()) return reject(Rejection.Reason.UNKNOWN_BUTTON, panel, button, seq, "toast ", panel, " has no button ", button, " that sends clicks");
            if (!b.data.contains(data)) return badData(panel, button, seq);
            return accept(new Click(this, src, panel, button, data, confirmed, seq, FormValues.EMPTY, null), b.handler);
        }
        if ("item".equals(src)) return unknown(panel, button, seq);
        if ("waypoint".equals(src)) {
            WaypointRecord w = waypoints.get(panel);
            if (w == null) return unknown(panel, button, seq);
            if (w.expiresAt > 0 && now > w.expiresAt + TTL_GRACE_MS) return reject(Rejection.Reason.EXPIRED, panel, button, seq, "waypoint ttl ended");
            if (w.token == null || !w.token.equals(token)) return reject(Rejection.Reason.BAD_TOKEN, panel, button, seq, "token does not match the last waypoint ", panel);
            if (!"label".equals(button)) return reject(Rejection.Reason.UNKNOWN_BUTTON, panel, button, seq, "a waypoint click is on its label");
            if (!w.data.contains(data)) return badData(panel, button, seq);
            return accept(new Click(this, src, panel, button, data, confirmed, seq, FormValues.EMPTY, null), w.handler);
        }
        PanelRecord p = panels.get(panel);
        if (p == null) return unknown(panel, button, seq);
        if (p.expiresAt > 0 && now > p.expiresAt + TTL_GRACE_MS) return reject(Rejection.Reason.EXPIRED, panel, button, seq, "panel ttl ended");
        if (!p.token.equals(token)) {
            PanelRecord previous = p.oldTokenRecord(token, now);
            if (previous == null) return reject(Rejection.Reason.BAD_TOKEN, panel, button, seq, "token does not match the last panel.set or patch of ", panel);
            p = previous;
        }
        if (!p.in.equals(src)) return reject(Rejection.Reason.WRONG_SOURCE, panel, button, seq, "click came from ", src, " but the panel is in ", p.in);
        Encoder.ButtonSpec b = p.buttons.get(button);
        if (b == null) return reject(Rejection.Reason.UNKNOWN_BUTTON, panel, button, seq, "panel ", panel, " has no button ", button);
        if (b.data.isEmpty()) return reject(Rejection.Reason.UNKNOWN_BUTTON, panel, button, seq, "button ", button, " sends no clicks");
        if (!b.enabled) return reject(Rejection.Reason.DISABLED, panel, button, seq, "button ", button, " was sent disabled");
        if (!b.data.contains(data)) return badData(panel, button, seq);
        if (p.anchor != null && p.anchor.kind() == WorldAnchor.Kind.BLOCK && !inWorldOf(p)) {
            return reject(Rejection.Reason.OUT_OF_REACH, panel, button, seq, "player is not in the world of block panel ", panel);
        }
        String pressKey = panel + '\n' + button;
        Long last = b.onePressOneClick && b.cooldownMs > 0 ? lastPress.get(pressKey) : null;
        if (last != null && now - last < b.cooldownMs - Math.max(COOLDOWN_TOLERANCE_MS, b.cooldownMs / 10)) {
            return reject(Rejection.Reason.COOLDOWN, panel, button, seq, "pressed ", now - last, " ms after the last press, cooldown ", b.cooldownMs, " ms");
        }
        FormValues inputs = FormValues.EMPTY;
        if (!p.inputs.isEmpty()) {
            String problem = null;
            Map<String, Object> parsed = new LinkedHashMap<String, Object>();
            JsonElement raw = m.get("inputs");
            JsonObject given = raw != null && raw.isJsonObject() ? raw.getAsJsonObject() : new JsonObject();
            for (Map.Entry<String, JsonElement> e : given.entrySet()) {
                if (!p.inputs.containsKey(e.getKey())) problem = "unknown input " + e.getKey();
            }
            for (Encoder.InputSpec spec : p.inputs.values()) {
                if (problem != null) break;
                JsonElement v = given.get(spec.id);
                if (v == null || v.isJsonNull()) {
                    parsed.put(spec.id, spec.initial);
                    continue;
                }
                Object value = inputValue(spec, v);
                if (value == null) problem = "input " + spec.id + " has a value the form cannot produce";
                else parsed.put(spec.id, value);
            }
            if (problem != null) return reject(Rejection.Reason.BAD_INPUTS, panel, button, seq, problem);
            inputs = new FormValues(parsed);
        }
        lastPress.put(pressKey, now);
        return accept(new Click(this, src, panel, button, data, confirmed, seq, inputs, p.anchor), b.handler);
    }

    private static Object inputValue(Encoder.InputSpec spec, JsonElement v) {
        if (!v.isJsonPrimitive()) return null;
        JsonPrimitive p = v.getAsJsonPrimitive();
        switch (spec.kind) {
            case TEXT:
                if (!p.isString() || p.getAsString().length() > spec.maxChars || !TextInput.clientCanSend(p.getAsString())) return null;
                return TextInput.clean(p.getAsString());
            case TOGGLE:
                return p.isBoolean() ? p.getAsBoolean() : null;
            case CHOICE:
                return p.isString() && spec.options.contains(p.getAsString()) ? p.getAsString() : null;
            case SLIDER: {
                if (!p.isNumber()) return null;
                double d = p.getAsDouble();
                if (Double.isNaN(d) || Double.isInfinite(d) || d < spec.min - 1e-6 || d > spec.max + 1e-6) return null;
                if (spec.step > 0 && Math.abs(Slider.snap(spec.min, spec.max, spec.step, d) - d) > 1e-9) return null;
                return d;
            }
            default:
                return null;
        }
    }

    private boolean inWorldOf(PanelRecord p) {
        if (p.placeWorld != null && !p.placeWorld.equals(worldId)) return false;
        if (p.placeDim != null) {
            String dim = connect.dimensionOf(this);
            if (dim != null && !p.placeDim.equals(dim)) return false;
        }
        if (p.placeWorld != null || p.placeDim != null) return true;
        return p.world == null || p.world.equals(connect.worldOf(this));
    }

    private Resolved accept(Click click, Consumer<Click> handler) {
        return new Resolved(click, handler, null, false);
    }

    private Resolved reject(Rejection.Reason reason, String panel, String button, long seq, Object... detail) {
        return new Resolved(null, null, new Rejection(this, reason, panel, button, seq, detail), false);
    }

    private Resolved badData(String panel, String button, long seq) {
        return reject(Rejection.Reason.BAD_DATA, panel, button, seq, "button ", button, " does not send that data");
    }

    private Resolved unknown(String panel, String button, long seq) {
        return new Resolved(null, null, new Rejection(this, Rejection.Reason.UNKNOWN_PANEL, panel, button, seq, "this plugin sent no ", panel), true);
    }

    private void expire(long now) {
        Iterator<PanelRecord> it = panels.values().iterator();
        while (it.hasNext()) {
            PanelRecord p = it.next();
            if (p.expiresAt > 0 && now >= p.expiresAt + TTL_GRACE_MS) it.remove();
        }
        Iterator<WaypointRecord> wit = waypoints.values().iterator();
        while (wit.hasNext()) {
            WaypointRecord w = wit.next();
            if (w.expiresAt > 0 && now >= w.expiresAt + TTL_GRACE_MS) wit.remove();
        }
    }

    private void cancelOpenIfGone() {
        String label = outbox.pendingOpenLabel();
        if (label != null && label.startsWith("panel.open ") && !panels.containsKey(label.substring("panel.open ".length()))) outbox.cancelOpen();
    }

    private Session key(String opens) {
        JsonObject o = Json.envelope("key.set");
        o.addProperty("opens", opens);
        return control(o, "key.set " + opens, null);
    }

    private Session control(JsonObject o, String label, Runnable commit) {
        enqueue(new Outgoing(o, label, commit));
        return this;
    }

    private JsonArray modIds(String at, String[] ids) {
        if (ids.length == 0) throw Check.fail(at, "name at least one mod id");
        Check.count(ids.length, Limits.MOD_IDS, at, "ids");
        JsonArray a = new JsonArray();
        for (String i : ids) a.add(new JsonPrimitive(Check.id(i, at)));
        return a;
    }

    private String check(JsonObject o) {
        String json = Json.write(o);
        int depth = Json.depth(o);
        if (depth + 2 > Limits.JSON_DEPTH) throw Check.fail(label(o), "nested " + depth + " deep, the client reads at most " + (Limits.JSON_DEPTH - 2) + " inside a batch (JSON_DEPTH)");
        return json;
    }

    private static String label(JsonObject o) {
        return o.has("t") ? o.get("t").getAsString() : "message";
    }

    private boolean enqueue(Outgoing out) {
        return enqueue(out, false);
    }

    private boolean enqueue(Outgoing out, boolean alone) {
        String json = check(out.json);
        int bytes = Json.utf8Length(json);
        if (out.isLock() && bytes > cap) throw Check.fail(out.label, "is " + bytes + " bytes, over the " + cap + " byte message limit");
        if (Outbox.fitsAlone(bytes, cap) == 0) {
            int raw = bytes + Outbox.batchOverhead("b2147483647");
            if (raw > Limits.CHUNKED_MESSAGE_BYTES) {
                throw Check.fail(out.label, "is " + raw + " bytes, the client accepts at most " + Limits.CHUNKED_MESSAGE_BYTES + " even when chunked (CHUNKED_MESSAGE_BYTES)");
            }
            List<Outbox.Item> one = new ArrayList<Outbox.Item>();
            one.add(new Outbox.Item(json, bytes, out.label, out.type, false, null, null));
            int parts = Outbox.chunkParts(Outbox.batchText("b2147483647", one), cap);
            if (parts > Limits.CHUNK_PARTS) {
                throw Check.fail(out.label, "is " + bytes + " bytes and needs " + parts + " chunks of " + cap + " bytes on this server, the client accepts "
                        + Limits.CHUNK_PARTS + " (CHUNK_PARTS)");
            }
        }
        if (outbox.waiting() >= Outbox.MAX_WAITING_MESSAGES) {
            String line = warnings.admit(Outbox.MAX_WAITING_MESSAGES + " messages already wait to be sent to " + name + ", dropped " + out.label, now());
            if (line != null) connect.log().warn(line);
            return false;
        }
        out.commit.run();
        outbox.add(new Outbox.Item(json, bytes, out.label, out.type, alone, panelId(out), out.json));
        return true;
    }

    private static String panelId(Outgoing out) {
        if ("panel.patch".equals(out.type)) return Json.getString(out.json, "id");
        if (!"panel.set".equals(out.type)) return null;
        JsonElement p = out.json.get("panel");
        return p != null && p.isJsonObject() ? Json.getString(p.getAsJsonObject(), "id") : null;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * Returns the player name, UUID and client info, for logs.
     *
     * @return the description
     */
    @Override
    public String toString() {
        return "Session[" + name + " " + id + ", " + client + "]";
    }
}
