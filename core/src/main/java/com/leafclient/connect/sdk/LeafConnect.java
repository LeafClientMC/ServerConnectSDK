package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The entry point of the SDK: keeps one {@link Session} per Leaf Client player, reads the messages they send and fires
 * the listeners. The platform adapters ({@code PaperConnect}, {@code VelocityConnect}, {@code BungeeConnect}) extend
 * it, register the plugin channel and call {@link #flush()} on a timer. A custom platform calls
 * {@link #receive}, {@link #disconnect(UUID)} and {@link #flush()} itself. A listener or click handler that throws is
 * logged and does not stop the others.
 */
public class LeafConnect {

    /** The plugin channel Leaf Client listens on. */
    public static final String CHANNEL = "leafclient:connect";
    /** The Server Connect protocol version this SDK speaks. Clients that do not speak it get no session. */
    public static final int PROTOCOL = 1;
    /** Largest message the SDK reads from a client, in bytes. Larger ones are ignored. */
    public static final int CLIENT_MESSAGE_BYTES = 16 * 1024;

    private final ConnectLog log;
    private final LongSupplier clock;
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<UUID, Session>();
    private final List<Consumer<Session>> readyListeners = new CopyOnWriteArrayList<Consumer<Session>>();
    private final List<Consumer<Session>> quitListeners = new CopyOnWriteArrayList<Consumer<Session>>();
    private final List<Consumer<Session>> prefsListeners = new CopyOnWriteArrayList<Consumer<Session>>();
    private final List<Consumer<Click>> clickListeners = new CopyOnWriteArrayList<Consumer<Click>>();
    private final List<Consumer<Closed>> closedListeners = new CopyOnWriteArrayList<Consumer<Closed>>();
    private final List<Consumer<ClientError>> errorListeners = new CopyOnWriteArrayList<Consumer<ClientError>>();
    private final List<Consumer<Rejection>> rejectionListeners = new CopyOnWriteArrayList<Consumer<Rejection>>();
    private final ConcurrentHashMap<UUID, Bucket> hellos = new ConcurrentHashMap<UUID, Bucket>();
    private final Throttle warnings = new Throttle(0.2, 5);
    private volatile boolean logClientErrors = true;

    /**
     * Creates the SDK with the system clock.
     *
     * @param log where the SDK writes its log lines
     */
    public LeafConnect(ConnectLog log) {
        this(log, new LongSupplier() {
            @Override
            public long getAsLong() {
                return System.currentTimeMillis();
            }
        });
    }

    /**
     * Creates the SDK with a custom clock, used for rate limits, open pacing, cooldowns and time to live.
     *
     * @param log where the SDK writes its log lines
     * @param clock the current time in milliseconds
     */
    public LeafConnect(ConnectLog log, LongSupplier clock) {
        this.log = log;
        this.clock = clock;
    }

    /**
     * Adds a listener for a client hello: a new session, or a session whose client said hello again. Send the UI
     * here. A hello again drops the server scoped panels, toasts, waypoints and values the session recorded.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onReady(Consumer<Session> listener) {
        readyListeners.add(listener);
        return this;
    }

    /**
     * Adds a listener for a session that ended with {@link #disconnect(UUID)}.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onQuit(Consumer<Session> listener) {
        quitListeners.add(listener);
        return this;
    }

    /**
     * Adds a listener for a change of the player's settings. Read them with {@link Session#prefs()}.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onPrefs(Consumer<Session> listener) {
        prefsListeners.add(listener);
        return this;
    }

    /**
     * Adds a listener for every accepted click. It runs after the handler of the clicked button.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onClick(Consumer<Click> listener) {
        clickListeners.add(listener);
        return this;
    }

    /**
     * Adds a listener for a menu, panel or toast the client closed.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onClosed(Consumer<Closed> listener) {
        closedListeners.add(listener);
        return this;
    }

    /**
     * Adds a listener for errors the client reports about messages it received.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onError(Consumer<ClientError> listener) {
        errorListeners.add(listener);
        return this;
    }

    /**
     * Adds a listener for clicks the SDK refused.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onRejected(Consumer<Rejection> listener) {
        rejectionListeners.add(listener);
        return this;
    }

    /**
     * Turns the warning log line for each client error on or off. The default is on.
     *
     * @param log false to stop logging client errors
     * @return this instance
     */
    public LeafConnect logClientErrors(boolean log) {
        this.logClientErrors = log;
        return this;
    }

    /**
     * Returns the session of a player.
     *
     * @param playerId the player UUID, or null
     * @return the session, or null if the player has no Leaf Client session
     */
    public Session session(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    /**
     * Tells whether a player has a Leaf Client session.
     *
     * @param playerId the player UUID, or null
     * @return true if the player's client said hello with a supported protocol
     */
    public boolean isLeaf(UUID playerId) {
        return session(playerId) != null;
    }

    /**
     * Returns the current sessions.
     *
     * @return a copy of the sessions, unmodifiable
     */
    public Collection<Session> sessions() {
        return Collections.unmodifiableCollection(new ArrayList<Session>(sessions.values()));
    }

    /**
     * Returns the log the SDK writes to.
     *
     * @return the log
     */
    public ConnectLog log() {
        return log;
    }

    /**
     * Tells whether a player is close enough to a block or entity panel to click it. Clicks out of reach are rejected
     * with {@link Rejection.Reason#OUT_OF_REACH}. This base version accepts every click; platform adapters override it.
     *
     * @param session the session of the player who clicked
     * @param anchor where the clicked panel is
     * @return true to accept the click
     */
    protected boolean inReach(Session session, WorldAnchor anchor) {
        return true;
    }

    long now() {
        return clock.getAsLong();
    }

    /**
     * Handles one plugin message from a player on {@link #CHANNEL}. A {@code hello} creates or refreshes the session;
     * other messages from players without a session are ignored. Empty messages, messages over
     * {@link #CLIENT_MESSAGE_BYTES}, messages nested deeper than 64 levels and messages that are not a JSON object are
     * ignored. A client may say hello 4 times in a row, then once every 2 seconds; more hellos are ignored. Warnings
     * caused by a client, such as its error reports, are cut to one line of at most 512 characters and logged at most
     * once every 5 seconds per player. Listeners and click handlers run on the calling thread.
     *
     * @param playerId the player UUID
     * @param playerName the player name, used in log lines
     * @param player the platform player object, returned by {@link Session#player()}
     * @param data the message bytes
     * @param transport sends frames back to this player
     */
    public void receive(UUID playerId, String playerName, Object player, byte[] data, Transport transport) {
        if (data == null || data.length == 0 || data.length > CLIENT_MESSAGE_BYTES) {
            if (log.debugEnabled()) log.debug("ignored a " + (data == null ? 0 : data.length) + " byte message from " + playerName);
            return;
        }
        try {
            handle(playerId, playerName, player, data, transport);
        } catch (RuntimeException e) {
            Session s = sessions.get(playerId);
            String line = (s != null ? s.warnings : warnings).admit("could not handle a Server Connect message from " + playerName + ": "
                    + Json.clean(e.toString(), 512), now());
            if (line != null) log.warn(line);
        }
    }

    @SuppressWarnings("deprecation")
    private void handle(UUID playerId, String playerName, Object player, byte[] data, Transport transport) {
        JsonObject msg;
        try {
            if (Json.tooDeep(data, 64)) throw new IllegalArgumentException("too deep");
            JsonElement e = new JsonParser().parse(new String(data, Json.UTF_8));
            if (!e.isJsonObject()) throw new IllegalArgumentException("not an object");
            msg = e.getAsJsonObject();
        } catch (RuntimeException e) {
            if (log.debugEnabled()) log.debug("ignored a message from " + playerName + " that is not a JSON object");
            return;
        }
        String type = Json.getString(msg, "t");
        if (type == null) return;
        if ("hello".equals(type)) {
            hello(playerId, playerName, player, msg, transport);
            return;
        }
        Session s = sessions.get(playerId);
        if (s == null) {
            if (log.debugEnabled()) log.debug("ignored " + Json.clean(type, 64) + " from " + playerName + " before hello");
            return;
        }
        if ("click".equals(type)) {
            click(s, msg);
        } else if ("closed".equals(type)) {
            Closed closed = new Closed(s, Json.getString(msg, "what"), Json.getString(msg, "id"), Json.getString(msg, "why"));
            s.noteClosed(closed);
            fire(closedListeners, closed, "closed");
        } else if ("prefs".equals(type)) {
            s.updatePrefs(Prefs.parse(msg));
            fire(prefsListeners, s, "prefs");
        } else if ("error".equals(type)) {
            String ref = Json.clean(Json.getString(msg, "ref"), 64);
            ClientError error = new ClientError(s, String.valueOf(Json.clean(Json.getString(msg, "code"), 64)), ref,
                    Json.clean(Json.getString(msg, "detail"), 512), s.describeRef(ref));
            if (logClientErrors) {
                String line = s.warnings.admit(error.toString(), now());
                if (line != null) log.warn(line);
            }
            fire(errorListeners, error, "error");
        } else if (log.debugEnabled()) {
            log.debug("ignored " + Json.clean(type, 64) + " from " + playerName);
        }
    }

    /**
     * Ends the session of a player who left, and fires the {@link #onQuit} listeners if there was one.
     *
     * @param playerId the player UUID
     */
    public void disconnect(UUID playerId) {
        hellos.remove(playerId);
        endSession(playerId);
    }

    private void endSession(UUID playerId) {
        Session s;
        synchronized (sessions) {
            s = sessions.remove(playerId);
        }
        if (s != null) fire(quitListeners, s, "quit");
    }

    /**
     * Sends what every session has queued, as far as the client rate limits allow. The rest waits for the next call.
     * The platform adapters call it every tick or every 50 ms.
     */
    public void flush() {
        for (Session s : sessions.values()) flushSession(s);
    }

    void flushSession(Session s) {
        synchronized (s.sendLock) {
            List<byte[]> frames = s.drain(now());
            for (byte[] frame : frames) {
                try {
                    s.transport().send(frame);
                } catch (RuntimeException e) {
                    log.error("could not send a Server Connect message to " + s.name(), e);
                    return;
                }
            }
        }
    }

    private void hello(UUID playerId, String playerName, Object player, JsonObject msg, Transport transport) {
        ClientInfo info = ClientInfo.parse(msg);
        JsonElement prefsJson = msg.get("prefs");
        Prefs prefs = Prefs.parse(prefsJson != null && prefsJson.isJsonObject() ? prefsJson.getAsJsonObject() : null);
        Bucket rate = hellos.get(playerId);
        if (rate == null) {
            Bucket fresh = new Bucket(0.5, 4);
            rate = hellos.putIfAbsent(playerId, fresh);
            if (rate == null) rate = fresh;
        }
        if (!rate.take(1, now())) {
            if (log.debugEnabled()) log.debug("ignored a hello from " + playerName + ": too many hellos");
            return;
        }
        if (!info.speaks(PROTOCOL)) {
            String line = warnings.admit(playerName + " uses Leaf Server Connect protocol " + info.protocolMin() + " to " + info.protocolMax()
                    + ", this plugin speaks " + PROTOCOL + "; no UI is sent to them", now());
            if (line != null) log.warn(line);
            endSession(playerId);
            return;
        }
        Session s;
        synchronized (sessions) {
            s = sessions.get(playerId);
            if (s != null && s.player() == player) {
                s.rehello(info, prefs);
            } else {
                s = new Session(this, playerId, playerName, player, transport, info, prefs);
                sessions.put(playerId, s);
            }
        }
        if (log.debugEnabled()) log.debug("hello from " + playerName + ": " + Json.clean(info.toString(), 512));
        fire(readyListeners, s, "ready");
    }

    private void click(Session s, JsonObject msg) {
        Session.Resolved r = s.resolveClick(msg, now());
        if (r.rejection != null) {
            if (!r.unknown && log.debugEnabled()) log.debug(Json.clean(r.rejection.toString(), 512));
            fire(rejectionListeners, r.rejection, "rejected");
            return;
        }
        if (r.click.anchor() != null && !inReach(s, r.click.anchor())) {
            Rejection far = new Rejection(s, Rejection.Reason.OUT_OF_REACH, "player is too far from world panel " + r.click.panel(),
                    r.click.panel(), r.click.button(), r.click.seq());
            if (log.debugEnabled()) log.debug(Json.clean(far.toString(), 512));
            fire(rejectionListeners, far, "rejected");
            return;
        }
        if (r.handler != null) {
            try {
                r.handler.accept(r.click);
            } catch (RuntimeException e) {
                log.error("click handler for " + r.click.panel() + "/" + r.click.button() + " failed", e);
            }
        }
        fire(clickListeners, r.click, "click");
    }

    private <T> void fire(List<Consumer<T>> listeners, T value, String what) {
        for (Consumer<T> l : listeners) {
            try {
                l.accept(value);
            } catch (RuntimeException e) {
                log.error("Server Connect " + what + " listener failed", e);
            }
        }
    }
}
