package com.leafclient.connect.sdk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

final class Encoder {

    static final class ButtonSpec {
        final String id;
        final Consumer<Click> handler;
        boolean enabled;
        long cooldownMs;
        final boolean onePressOneClick;
        final Set<String> data;

        ButtonSpec(String id, Consumer<Click> handler, boolean enabled, long cooldownMs, boolean onePressOneClick, Set<String> data) {
            this.id = id;
            this.handler = handler;
            this.enabled = enabled;
            this.cooldownMs = cooldownMs;
            this.onePressOneClick = onePressOneClick;
            this.data = data;
        }
    }

    enum InputKind { TEXT, TOGGLE, CHOICE, SLIDER }

    static final class InputSpec {
        final String id;
        final InputKind kind;
        final int maxChars;
        final List<String> options;
        final double min;
        final double max;
        final double step;
        final Object initial;

        InputSpec(String id, InputKind kind, int maxChars, List<String> options, double min, double max, double step, Object initial) {
            this.id = id;
            this.kind = kind;
            this.maxChars = maxChars;
            this.options = options;
            this.min = min;
            this.max = max;
            this.step = step;
            this.initial = initial;
        }
    }

    final String prefix;
    final boolean inputsAllowed;
    int elements;
    int buttons;
    int inputs;
    int images;
    int players;
    boolean inScroll;
    final Map<String, String> ids = new LinkedHashMap<String, String>();
    final Map<String, ButtonSpec> buttonSpecs = new LinkedHashMap<String, ButtonSpec>();
    final Map<String, InputSpec> inputSpecs = new LinkedHashMap<String, InputSpec>();

    Encoder(String prefix, boolean inputsAllowed) {
        this.prefix = prefix;
        this.inputsAllowed = inputsAllowed;
    }

    String at(String path) {
        return prefix + " " + path;
    }

    InvalidUiException fail(String path, String problem) {
        return Check.fail(at(path), problem);
    }

    void depth(int depth, String path) {
        if (depth > Limits.ELEMENT_DEPTH) throw fail(path, "elements nested deeper than " + Limits.ELEMENT_DEPTH + " (ELEMENT_DEPTH)");
    }

    void element(String path) {
        if (++elements > Limits.ELEMENTS_PER_PANEL) throw fail(path, "more than " + Limits.ELEMENTS_PER_PANEL + " elements in one panel (ELEMENTS_PER_PANEL)");
    }

    void button(String path) {
        if (++buttons > Limits.BUTTONS_PER_PANEL) throw fail(path, "more than " + Limits.BUTTONS_PER_PANEL + " buttons in one panel (BUTTONS_PER_PANEL)");
    }

    void input(String path) {
        if (!inputsAllowed) throw fail(path, "inputs are only allowed in menu panels");
        if (++inputs > Limits.INPUTS_PER_PANEL) throw fail(path, "more than " + Limits.INPUTS_PER_PANEL + " inputs in one panel (INPUTS_PER_PANEL)");
    }

    void image(String path) {
        if (++images > Limits.IMAGES_PER_PANEL) throw fail(path, "more than " + Limits.IMAGES_PER_PANEL + " images in one panel (IMAGES_PER_PANEL)");
    }

    void player(String path) {
        if (++players > Limits.PLAYERS_PER_PANEL) throw fail(path, "more than " + Limits.PLAYERS_PER_PANEL + " player models in one panel (PLAYERS_PER_PANEL)");
    }

    void claimId(String id, String type, String path) {
        if (ids.containsKey(id)) throw fail(path + ".id", "duplicate element id " + id);
        ids.put(id, type);
    }
}
