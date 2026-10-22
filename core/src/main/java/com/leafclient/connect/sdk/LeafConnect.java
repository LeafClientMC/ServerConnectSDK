package com.leafclient.connect.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    private final Throttle failures = new Throttle(0.2, 5);
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
        readyListeners.add(notNull(listener));
        return this;
    }

    /**
     * Adds a listener for a session that ended with {@link #disconnect(UUID)}.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onQuit(Consumer<Session> listener) {
        quitListeners.add(notNull(listener));
        return this;
    }

    /**
     * Adds a listener for a change of the player's settings. Read them with {@link Session#prefs()}.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onPrefs(Consumer<Session> listener) {
        prefsListeners.add(notNull(listener));
        return this;
    }

    /**
     * Adds a listener for every accepted click. It runs after the handler of the clicked button.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onClick(Consumer<Click> listener) {
        clickListeners.add(notNull(listener));
        return this;
    }

    /**
     * Adds a listener for a menu, panel or toast the client closed.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onClosed(Consumer<Closed> listener) {
        closedListeners.add(notNull(listener));
        return this;
    }

    /**
     * Adds a listener for errors the client reports about messages it received.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onError(Consumer<ClientError> listener) {
        errorListeners.add(notNull(listener));
        return this;
    }

    /**
     * Adds a listener for clicks the SDK refused.
     *
     * @param listener the listener
     * @return this instance
     */
    public LeafConnect onRejected(Consumer<Rejection> listener) {
        rejectionListeners.add(notNull(listener));
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

    private void handle(UUID playerId, String playerName, Object player, byte[] data, Transport transport) {
        JsonObject msg = Json.parseObject(new String(data, Json.UTF_8), 64);
        if (msg == null) {
            if (log.debugEnabled()) log.debug("ignored a message from " + playerName + " that is not a strict JSON object within 64 levels");
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
        if (!s.messageRate.take(1, now())) {
            if (log.debugEnabled()) log.debug("ignored " + Json.clean(type, 64) + " from " + playerName + ": more than "
                    + Session.MESSAGES_PER_SECOND + " messages per second");
            return;
        }
        if ("click".equals(type)) {
            click(s, msg);
        } else if ("closed".equals(type)) {
            Closed closed = new Closed(s, Json.getString(msg, "what"), Json.getString(msg, "id"), Json.getString(msg, "why"));
            s.noteClosed(closed);
            fire(closedListeners, closed, "closed", s);
        } else if ("prefs".equals(type)) {
            s.updatePrefs(Prefs.parse(msg));
            fire(prefsListeners, s, "prefs", s);
        } else if ("error".equals(type)) {
            String ref = Json.clean(Json.getString(msg, "ref"), 64);
            ClientError error = new ClientError(s, String.valueOf(Json.clean(Json.getString(msg, "code"), 64)), ref,
                    Json.clean(Json.getString(msg, "detail"), 512), s.describeRef(ref));
            if (logClientErrors) {
                String line = s.warnings.admit(error.toString(), now());
                if (line != null) log.warn(line);
            }
            fire(errorListeners, error, "error", s);
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
        if (s != null) fire(quitListeners, s, "quit", s);
    }

    /**
     * Sends what every session has queued, as far as the client rate limits allow. The rest waits for the next call.
     * The platform adapters call it every tick or every 50 ms.
     */
    public void flush() {
        for (Session s : sessions.values()) {
            try {
                flushSession(s);
            } catch (RuntimeException e) {
                failed(s, "could not flush the Server Connect session of " + s.name(), e);
            }
        }
    }

    void flushSession(Session s) {
        synchronized (s.sendLock) {
            List<byte[]> frames = s.drain(now());
            for (int i = 0; i < frames.size(); i++) {
                try {
                    s.transport().send(frames.get(i));
                } catch (RuntimeException e) {
                    s.keepUnsent(frames.subList(i + 1, frames.size()));
                    failed(s, "could not send a Server Connect message to " + s.name(), e);
                    return;
                }
            }
        }
    }

    /**
     * Called once for each new session, after the client said hello with a supported protocol and before the
     * {@link #onReady} listeners run. A hello again on the same connection does not call it.
     *
     * @param session the new session
     * @param transport the transport the session sends with
     */
    protected void started(Session session, Transport transport) {
    }

    /**
     * Returns the world the player is in, compared by {@code equals}. A click on a block panel sent while the player
     * was in another world is rejected with {@link Rejection.Reason#OUT_OF_REACH}. This base version returns null,
     * which skips the check; platform adapters override it.
     *
     * @param session the session
     * @return the world, or null if unknown
     */
    protected Object worldOf(Session session) {
        return null;
    }

    void failed(Session s, String message, Throwable error) {
        String line = (s != null ? s.failures : failures).admit(message, now());
        if (line != null) log.error(line, error);
    }

    private static <T> T notNull(T listener) {
        if (listener == null) throw new NullPointerException("listener");
        return listener;
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
        boolean fresh;
        synchronized (sessions) {
            s = sessions.get(playerId);
            fresh = s == null || s.player() != player;
            if (fresh) {
                s = new Session(this, playerId, playerName, player, transport, info, prefs);
                sessions.put(playerId, s);
            } else {
                s.rehello(info, prefs);
            }
        }
        if (fresh) {
            try {
                started(s, transport);
            } catch (RuntimeException e) {
                failed(s, "could not start the Server Connect session of " + playerName, e);
            }
        }
        if (log.debugEnabled()) log.debug("hello from " + playerName + ": " + info);
        fire(readyListeners, s, "ready", s);
    }

    private void click(Session s, JsonObject msg) {
        Session.Resolved r = s.resolveClick(msg, now());
        if (r.rejection != null) {
            if (!r.unknown && log.debugEnabled()) log.debug(r.rejection.toString());
            fire(rejectionListeners, r.rejection, "rejected", s);
            return;
        }
        if (r.click.anchor() != null && !inReach(s, r.click.anchor())) {
            Rejection far = new Rejection(s, Rejection.Reason.OUT_OF_REACH, r.click.panel(), r.click.button(), r.click.seq(),
                    "player is too far from world panel ", r.click.panel());
            if (log.debugEnabled()) log.debug(far.toString());
            fire(rejectionListeners, far, "rejected", s);
            return;
        }
        if (r.handler != null) {
            try {
                r.handler.accept(r.click);
            } catch (RuntimeException e) {
                failed(s, "click handler for " + r.click.panel() + "/" + r.click.button() + " failed", e);
            }
        }
        fire(clickListeners, r.click, "click", s);
    }

    private <T> void fire(List<Consumer<T>> listeners, T value, String what, Session s) {
        for (Consumer<T> l : listeners) {
            try {
                l.accept(value);
            } catch (RuntimeException e) {
                failed(s, "Server Connect " + what + " listener failed", e);
            }
        }
    }
}
