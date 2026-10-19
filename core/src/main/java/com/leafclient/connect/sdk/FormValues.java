package com.leafclient.connect.sdk;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The input values that came with a click, keyed by input id. Get it with {@link Click#inputs()}.
 * The SDK has already checked every value against the panel it sent: text fits the maximum length, a choice is one of
 * the options, and a slider value is inside its range. An input the client did not send holds its starting value.
 */
public final class FormValues {

    static final FormValues EMPTY = new FormValues(Collections.<String, Object>emptyMap());

    private final Map<String, Object> values;

    FormValues(Map<String, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(values));
    }

    /**
     * Tells whether there are no values. It is true for clicks on panels without inputs, toasts and waypoints.
     *
     * @return true if there are no values
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Tells whether there is a value for an input.
     *
     * @param id the input id
     * @return true if there is a value
     */
    public boolean has(String id) {
        return values.containsKey(id);
    }

    /**
     * Returns the input ids, in panel order.
     *
     * @return the ids, unmodifiable
     */
    public Set<String> ids() {
        return values.keySet();
    }

    /**
     * Returns the value of a text input or a choice.
     *
     * @param id the input id
     * @return the text, or the option id of a choice
     * @throws IllegalArgumentException if there is no such input or it is not a text input or choice
     */
    public String text(String id) {
        return get(id, String.class, "a text input or choice");
    }

    /**
     * Returns the selected option id of a choice.
     *
     * @param id the input id
     * @return the option id
     * @throws IllegalArgumentException if there is no such input or it does not hold a string
     */
    public String choice(String id) {
        return get(id, String.class, "a choice");
    }

    /**
     * Returns the state of a toggle or checkbox.
     *
     * @param id the input id
     * @return true if it is on
     * @throws IllegalArgumentException if there is no such input or it is not a toggle or checkbox
     */
    public boolean isOn(String id) {
        return get(id, Boolean.class, "a toggle or checkbox");
    }

    /**
     * Returns the value of a slider.
     *
     * @param id the input id
     * @return the value
     * @throws IllegalArgumentException if there is no such input or it is not a slider
     */
    public double number(String id) {
        return get(id, Double.class, "a slider");
    }

    /**
     * Returns the value of a slider rounded to the nearest integer.
     *
     * @param id the input id
     * @return the rounded value
     * @throws IllegalArgumentException if there is no such input or it is not a slider
     */
    public int integer(String id) {
        return (int) Math.round(number(id));
    }

    /**
     * Returns all values: {@code String} for text inputs and choices, {@code Boolean} for toggles and checkboxes,
     * {@code Double} for sliders.
     *
     * @return the values by input id, unmodifiable
     */
    public Map<String, Object> asMap() {
        return values;
    }

    private <T> T get(String id, Class<T> type, String what) {
        Object v = values.get(id);
        if (v == null) throw new IllegalArgumentException("the form has no field " + id + ", it has " + values.keySet());
        if (!type.isInstance(v)) throw new IllegalArgumentException("field " + id + " is not " + what);
        return type.cast(v);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
