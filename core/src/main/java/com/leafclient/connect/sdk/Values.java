package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code values.set} message: sets or deletes named values and timers on the client. Text shown by the client
 * reads them through placeholders, so the server can change shown numbers without sending the UI again:
 * <ul>
 *   <li>{@code {v:key}} the value, or {@code {v:key|fallback}} with a fallback when it is not set</li>
 *   <li>{@code {n:key}} a number value with thousands separators</li>
 *   <li>{@code {t:key}} a timer as {@code mm:ss}; {@code {t:key:hh:mm:ss}} and {@code {t:key:s}} pick other formats</li>
 *   <li><code>&#123;&#123;</code> and <code>&#125;&#125;</code> write literal braces</li>
 * </ul>
 * Conditions test values with {@link Condition#eq} and the other comparisons. The client holds at most
 * {@link Limits#VALUES} values per server, and the session checks that before sending. A message with only plain
 * values needs the {@code values} cap; one with a timer needs the {@code timers} cap.
 */
public final class Values extends Message {

    private final Map<String, JsonElement> set = new LinkedHashMap<String, JsonElement>();
    private final List<String> delete = new ArrayList<String>();
    private boolean timers;

    private Values() {}

    /**
     * Creates an empty values message.
     *
     * @return the new message
     */
    public static Values create() {
        return new Values();
    }

    /**
     * Creates a values message that sets one value.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param value a number, boolean or text, as in {@link #set(String, Object)}
     * @return the new message
     * @throws InvalidUiException if the key or value is not valid
     */
    public static Values of(String key, Object value) {
        return new Values().set(key, value);
    }

    /**
     * Sets a value. A number is sent as a number (-1e300 to 1e300), a boolean as the text {@code true} or
     * {@code false}, anything else as its {@code toString()} text of at most {@link Limits#VALUE_CHARS} characters.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param value the value, not null
     * @return this message
     * @throws InvalidUiException if the key is not valid, or the value is null, too long or not finite
     */
    public Values set(String key, Object value) {
        key(key);
        String at = "values.set." + key;
        if (value instanceof Number) {
            set.put(key, Json.number(Check.range(((Number) value).doubleValue(), -1e300, 1e300, at)));
        } else if (value instanceof Boolean) {
            set.put(key, new JsonPrimitive(value.toString()));
        } else if (value != null) {
            set.put(key, new JsonPrimitive(Check.chars(value.toString(), Limits.VALUE_CHARS, at)));
        } else {
            throw Check.fail(at, "missing, use delete to remove a value");
        }
        delete.remove(key);
        return this;
    }

    /**
     * Starts a timer that counts down to zero on the client. {@link Condition#timerDone(String)} becomes true at zero.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param ms the time left in milliseconds, 0 to 864000000 (10 days)
     * @return this message
     * @throws InvalidUiException if the key is not valid or the time is out of range
     */
    public Values countdown(String key, long ms) {
        return timer(key, "countdown", ms, null);
    }

    /**
     * Starts a countdown that shows a text when it reaches zero.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param ms the time left in milliseconds, 0 to 864000000 (10 days)
     * @param then the text shown at zero, at most {@link Limits#VALUE_CHARS} characters
     * @return this message
     * @throws InvalidUiException if the key is not valid, the time is out of range, or the text is null or too long
     */
    public Values countdown(String key, long ms, String then) {
        return timer(key, "countdown", ms, Check.chars(then, Limits.VALUE_CHARS, "values.set." + key + ".then"));
    }

    /**
     * Starts a timer that counts up on the client.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param startMs the starting time in milliseconds, 0 to 864000000 (10 days)
     * @return this message
     * @throws InvalidUiException if the key is not valid or the time is out of range
     */
    public Values stopwatch(String key, long startMs) {
        return timer(key, "stopwatch", startMs, null);
    }

    /**
     * Sets a timer that shows a fixed time and does not run.
     *
     * @param key the key, 1 to {@link Limits#VALUE_KEY_CHARS} characters of {@code A-Z a-z 0-9 _ . : -}
     * @param ms the time shown in milliseconds, 0 to 864000000 (10 days)
     * @return this message
     * @throws InvalidUiException if the key is not valid or the time is out of range
     */
    public Values paused(String key, long ms) {
        return timer(key, "paused", ms, null);
    }

    /**
     * Deletes values and timers on the client.
     *
     * @param keys the keys, at most {@link Limits#VALUES} in one message
     * @return this message
     * @throws InvalidUiException if a key is not valid or there are too many keys
     */
    public Values delete(String... keys) {
        for (String k : keys) {
            if (delete.size() >= Limits.VALUES) throw Check.fail("values.del", "more than " + Limits.VALUES + " keys");
            set.remove(key(k));
            if (!delete.contains(k)) delete.add(k);
        }
        return this;
    }

    private Values timer(String key, String kind, long ms, String then) {
        key(key);
        JsonObject t = new JsonObject();
        t.addProperty(kind, Check.range(ms, 0, 864000000L, "values.set." + key + "." + kind));
        Json.str(t, "then", then);
        set.put(key, t);
        delete.remove(key);
        timers = true;
        return this;
    }

    private static String key(String key) {
        return Check.pattern(key, Check.VALUE_KEY, "values.set", "a value key (1 to 48 of A-Z a-z 0-9 _ . : -)");
    }

    @Override
    String requiredCap() {
        return timers ? "timers" : "values";
    }

    @Override
    Outgoing encode(final Session session) {
        if (set.isEmpty() && delete.isEmpty()) throw Check.fail("values.set", "nothing to set or delete");
        JsonObject o = Json.envelope("values.set");
        if (!set.isEmpty()) {
            JsonObject s = new JsonObject();
            for (Map.Entry<String, JsonElement> e : set.entrySet()) s.add(e.getKey(), Json.copy(e.getValue()));
            o.add("set", s);
        }
        if (!delete.isEmpty()) {
            JsonArray d = new JsonArray();
            for (String k : delete) d.add(new JsonPrimitive(k));
            o.add("del", d);
        }
        if (session == null) return Outgoing.simple(o, "values.set");
        final List<String> keys = new ArrayList<String>(set.keySet());
        final List<String> removed = new ArrayList<String>(delete);
        session.checkValueLimit(keys, removed);
        return new Outgoing(o, "values.set", new Runnable() {
            @Override
            public void run() {
                session.applyValues(keys, removed);
            }
        });
    }
}
