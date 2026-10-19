package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A test the client checks again and again, used to show panels and elements ({@code when}) or enable buttons
 * ({@link Button#enabledWhen}) without a round trip to the server. Value tests read the values set with {@link Values}.
 * A condition holds at most {@link Limits#CONDITION_TESTS} tests nested at most {@link Limits#CONDITION_DEPTH} deep;
 * this is checked when it is encoded.
 */
public final class Condition {

    private final String op;
    private final JsonObject leaf;
    private final List<Condition> children;

    private Condition(String op, JsonObject leaf, List<Condition> children) {
        this.op = op;
        this.leaf = leaf;
        this.children = children;
    }

    /**
     * True when a value equals the operand. Two numbers compare as numbers, anything else as text.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param value a number, boolean, String or Character; text at most {@link Limits#VALUE_CHARS} characters
     * @return the condition
     * @throws InvalidUiException if the key or value is not valid
     */
    public static Condition eq(String key, Object value) {
        return compare(key, "eq", scalar(value, "eq"));
    }

    /**
     * True when a value is not set or differs from the operand.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param value a number, boolean, String or Character; text at most {@link Limits#VALUE_CHARS} characters
     * @return the condition
     * @throws InvalidUiException if the key or value is not valid
     */
    public static Condition ne(String key, Object value) {
        return compare(key, "ne", scalar(value, "ne"));
    }

    /**
     * True when a value is a number greater than the operand. False when the value is not a number.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param value the operand, -1e300 to 1e300
     * @return the condition
     * @throws InvalidUiException if the key or operand is not valid
     */
    public static Condition gt(String key, double value) {
        return compare(key, "gt", number(value, "gt"));
    }

    /**
     * True when a value is a number greater than or equal to the operand. False when the value is not a number.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param value the operand, -1e300 to 1e300
     * @return the condition
     * @throws InvalidUiException if the key or operand is not valid
     */
    public static Condition ge(String key, double value) {
        return compare(key, "ge", number(value, "ge"));
    }

    /**
     * True when a value is a number less than the operand. False when the value is not a number.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param value the operand, -1e300 to 1e300
     * @return the condition
     * @throws InvalidUiException if the key or operand is not valid
     */
    public static Condition lt(String key, double value) {
        return compare(key, "lt", number(value, "lt"));
    }

    /**
     * True when a value is a number less than or equal to the operand. False when the value is not a number.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param value the operand, -1e300 to 1e300
     * @return the condition
     * @throws InvalidUiException if the key or operand is not valid
     */
    public static Condition le(String key, double value) {
        return compare(key, "le", number(value, "le"));
    }

    /**
     * True when a value equals one of the operands, compared as in {@link #eq}.
     *
     * @param key the value key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @param values at most 32 operands, each a number, boolean, String or Character
     * @return the condition
     * @throws InvalidUiException if the key or an operand is not valid, or there are more than 32 operands
     */
    public static Condition in(String key, Object... values) {
        Check.count(values.length, 32, "condition.in", "values");
        JsonArray a = new JsonArray();
        for (Object v : values) a.add(scalar(v, "in"));
        return compare(key, "in", a);
    }

    /**
     * True when every condition is true.
     *
     * @param conditions 1 to {@link Limits#CONDITION_TESTS} conditions
     * @return the condition
     * @throws InvalidUiException if the list is empty, too long or holds null
     */
    public static Condition all(Condition... conditions) {
        return group("all", conditions);
    }

    /**
     * True when at least one condition is true.
     *
     * @param conditions 1 to {@link Limits#CONDITION_TESTS} conditions
     * @return the condition
     * @throws InvalidUiException if the list is empty, too long or holds null
     */
    public static Condition any(Condition... conditions) {
        return group("any", conditions);
    }

    /**
     * True when the condition is false.
     *
     * @param condition the condition
     * @return the condition
     * @throws InvalidUiException if the condition is null
     */
    public static Condition not(Condition condition) {
        return new Condition("not", null, Collections.singletonList(Check.notNull(condition, "condition.not")));
    }

    /**
     * True when the player holds an item.
     *
     * @param itemId the item id, at most 128 characters
     * @return the condition
     * @throws InvalidUiException if the id is null or too long
     */
    public static Condition holding(String itemId) {
        return leaf("holding", new JsonPrimitive(Check.chars(itemId, 128, "condition.holding")));
    }

    /**
     * True while the player sneaks.
     *
     * @return the condition
     */
    public static Condition sneaking() {
        return leaf("sneaking", new JsonPrimitive(true));
    }

    /**
     * True while the player does not sneak.
     *
     * @return the condition
     */
    public static Condition notSneaking() {
        return leaf("sneaking", new JsonPrimitive(false));
    }

    /**
     * True when the player is in a dimension.
     *
     * @param dimension the dimension id, at most 64 characters
     * @return the condition
     * @throws InvalidUiException if the dimension is null or too long
     */
    public static Condition dim(String dimension) {
        return leaf("dim", new JsonPrimitive(Check.chars(dimension, 64, "condition.dim")));
    }

    /**
     * True when the current world id, set with {@link Session#world(String)}, is the given one.
     *
     * @param worldId 1 to {@link Limits#ID_CHARS} characters of {@code a-z 0-9 _ . : / -}
     * @return the condition
     * @throws InvalidUiException if the id is missing or not valid
     */
    public static Condition world(String worldId) {
        return leaf("world", new JsonPrimitive(Check.id(worldId, "condition.world")));
    }

    /**
     * True when the player is within a distance of the block or entity panel the condition belongs to. False for
     * panels that are not in the world.
     *
     * @param blocks the distance, 0 to 128
     * @return the condition
     * @throws InvalidUiException if the distance is out of range
     */
    public static Condition near(double blocks) {
        return leaf("near", Json.number(Check.range(blocks, 0, 128, "condition.near")));
    }

    /**
     * True when a countdown set with {@link Values#countdown(String, long)} has reached zero.
     *
     * @param key the timer key, at most {@link Limits#VALUE_KEY_CHARS} characters
     * @return the condition
     * @throws InvalidUiException if the key is null or too long
     */
    public static Condition timerDone(String key) {
        return leaf("timer_done", new JsonPrimitive(Check.chars(key, Limits.VALUE_KEY_CHARS, "condition.timer_done")));
    }

    JsonObject toJson(String at) {
        int depth = depth();
        if (depth > Limits.CONDITION_DEPTH) throw Check.fail(at, "condition nested " + depth + " deep, the client limit is " + Limits.CONDITION_DEPTH);
        int tests = tests();
        if (tests > Limits.CONDITION_TESTS) throw Check.fail(at, "condition has " + tests + " tests, the client limit is " + Limits.CONDITION_TESTS);
        return build();
    }

    boolean usesNear() {
        if (leaf != null) return leaf.has("near");
        for (Condition c : children) if (c.usesNear()) return true;
        return false;
    }

    private JsonObject build() {
        if (leaf != null) return (JsonObject) Json.copy(leaf);
        JsonObject o = new JsonObject();
        if ("not".equals(op)) {
            o.add("not", children.get(0).build());
        } else {
            JsonArray a = new JsonArray();
            for (Condition c : children) a.add(c.build());
            o.add(op, a);
        }
        return o;
    }

    private int depth() {
        int max = 0;
        for (Condition c : children) max = Math.max(max, c.depth());
        return max + 1;
    }

    private int tests() {
        if (leaf != null) return 1;
        int n = 0;
        for (Condition c : children) n += c.tests();
        return n;
    }

    private static Condition group(String op, Condition[] conditions) {
        if (conditions.length == 0) throw new InvalidUiException("condition." + op + ": empty list");
        Check.count(conditions.length, Limits.CONDITION_TESTS, "condition." + op, "conditions");
        for (Condition c : conditions) Check.notNull(c, "condition." + op);
        return new Condition(op, null, Collections.unmodifiableList(Arrays.asList(conditions.clone())));
    }

    private static Condition compare(String key, String cmp, JsonElement operand) {
        JsonObject o = new JsonObject();
        o.addProperty("v", Check.chars(key, Limits.VALUE_KEY_CHARS, "condition.v"));
        o.add(cmp, operand);
        return new Condition("v", o, Collections.<Condition>emptyList());
    }

    private static Condition leaf(String key, JsonElement value) {
        JsonObject o = new JsonObject();
        o.add(key, value);
        return new Condition(key, o, Collections.<Condition>emptyList());
    }

    private static JsonPrimitive number(double v, String cmp) {
        return Json.number(Check.range(v, -1e300, 1e300, "condition." + cmp));
    }

    private static JsonPrimitive scalar(Object value, String cmp) {
        String at = "condition." + cmp;
        if (value instanceof Number) return number(((Number) value).doubleValue(), cmp);
        if (value instanceof Boolean) return new JsonPrimitive(value.toString());
        if (value instanceof String || value instanceof Character) return new JsonPrimitive(Check.chars(value.toString(), Limits.VALUE_CHARS, at));
        throw Check.fail(at, "expected a string, number or boolean");
    }
}
