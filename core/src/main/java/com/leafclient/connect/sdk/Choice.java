package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A pick-one input. Create it with {@link Ui#choice(String, String...)} and read the selected option id in a click
 * handler with {@link FormValues#choice(String)}. Inputs are only allowed in menu panels.
 * A choice has 1 to {@link Limits#CHOICE_OPTIONS} options.
 */
public final class Choice extends InputElement<Choice> {

    private final List<String> ids = new ArrayList<String>();
    private final List<Text> labels = new ArrayList<Text>();
    private String value;

    Choice(String id) {
        super(id);
    }

    @Override
    String type() {
        return "choice";
    }

    /**
     * Adds an option whose label is its id.
     *
     * @param optionId the option id, 1 to {@link Limits#ID_CHARS} characters, unique in this choice
     * @return this input
     * @throws InvalidUiException if the id is missing, too long or a duplicate, or the choice already has
     *         {@link Limits#CHOICE_OPTIONS} options
     */
    public Choice option(String optionId) {
        return add(optionId, null);
    }

    /**
     * Adds an option with a label.
     *
     * @param optionId the option id, 1 to {@link Limits#ID_CHARS} characters, unique in this choice
     * @param label legacy text with {@code &} or {@code §} codes, at most 64 visible characters
     * @return this input
     * @throws InvalidUiException if the id is missing, too long or a duplicate, the label is null, or the choice
     *         already has {@link Limits#CHOICE_OPTIONS} options
     */
    public Choice option(String optionId, String label) {
        return add(optionId, Text.of(label));
    }

    /**
     * Adds an option with a label.
     *
     * @param optionId the option id, 1 to {@link Limits#ID_CHARS} characters, unique in this choice
     * @param label the label, at most 64 visible characters
     * @return this input
     * @throws InvalidUiException if the id is missing, too long or a duplicate, the label is null, or the choice
     *         already has {@link Limits#CHOICE_OPTIONS} options
     */
    public Choice option(String optionId, Text label) {
        return add(optionId, Check.notNull(label, "choice.options.label"));
    }

    /**
     * Sets the option selected at the start. The default is the first option. It must be one of the options when the
     * panel is sent.
     *
     * @param optionId the option id
     * @return this input
     */
    public Choice value(String optionId) {
        this.value = optionId;
        return this;
    }

    private Choice add(String optionId, Text label) {
        Check.notNull(optionId, "choice.options");
        if (optionId.isEmpty() || optionId.length() > Limits.ID_CHARS) throw Check.fail("choice.options", "an option id is 1 to " + Limits.ID_CHARS + " characters");
        if (ids.contains(optionId)) throw Check.fail("choice.options", "duplicate option " + optionId);
        if (ids.size() >= Limits.CHOICE_OPTIONS) throw Check.fail("choice.options", "more than " + Limits.CHOICE_OPTIONS + " options");
        ids.add(optionId);
        labels.add(label);
        return this;
    }

    @Override
    Encoder.InputSpec encodeInput(JsonObject o, Encoder enc, String path) {
        if (ids.isEmpty()) throw enc.fail(path + ".options", "a choice needs at least one option");
        JsonArray options = new JsonArray();
        for (int i = 0; i < ids.size(); i++) {
            Text label = labels.get(i);
            if (label == null) {
                options.add(new JsonPrimitive(ids.get(i)));
            } else {
                JsonObject option = new JsonObject();
                option.addProperty("id", ids.get(i));
                option.add("label", Check.text(label, 64, enc.at(path + ".options[" + i + "].label")).toJson());
                options.add(option);
            }
        }
        o.add("options", options);
        if (value != null) {
            if (!ids.contains(value)) throw enc.fail(path + ".value", value + " is not one of the options");
            o.addProperty("value", value);
        }
        return new Encoder.InputSpec(id, Encoder.InputKind.CHOICE, 0, Collections.unmodifiableList(new ArrayList<String>(ids)), 0, 0, 0,
                value != null ? value : ids.get(0));
    }
}
