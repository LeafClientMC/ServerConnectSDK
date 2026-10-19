package com.leafclient.connect.sdk;

import java.util.Locale;
import java.util.regex.Pattern;

final class Check {

    static final Pattern ID = Pattern.compile("[a-z0-9_.:/-]{1,64}");
    static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}");
    static final Pattern RESOURCE = Pattern.compile("([a-z0-9_.-]{1,32}:)?[a-z0-9_./-]{1,96}");
    static final Pattern LEAF_ICON = Pattern.compile("[a-z0-9_]{1,32}");
    static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,96}\\.png");
    static final Pattern SOUND = Pattern.compile("([a-z0-9_.-]{1,32}:)?[a-z0-9_./-]{1,64}");
    static final Pattern FONT = Pattern.compile("[a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64}");
    static final Pattern VALUE_KEY = Pattern.compile("[A-Za-z0-9_.:-]{1,48}");

    private Check() {}

    static InvalidUiException fail(String at, String problem) {
        return new InvalidUiException(at + ": " + problem);
    }

    static <T> T notNull(T value, String at) {
        if (value == null) throw fail(at, "missing");
        return value;
    }

    static String id(String value, String at) {
        if (value == null) throw fail(at, "missing");
        if (!ID.matcher(value).matches()) {
            throw fail(at, "\"" + clip(value) + "\" is not a valid id, use 1 to " + Limits.ID_CHARS + " characters of a-z 0-9 _ . : / -");
        }
        return value;
    }

    static String uuid(String value, String at) {
        if (value == null || !UUID.matcher(value).matches()) throw fail(at, "\"" + clip(value) + "\" is not a player UUID");
        return value;
    }

    static double range(double v, double min, double max, String at) {
        if (Double.isNaN(v) || Double.isInfinite(v)) throw fail(at, "not a finite number");
        if (v < min || v > max) throw fail(at, format(v) + " is outside the client range " + format(min) + " to " + format(max));
        return v;
    }

    static int range(int v, int min, int max, String at) {
        if (v < min || v > max) throw fail(at, v + " is outside the client range " + min + " to " + max);
        return v;
    }

    static long range(long v, long min, long max, String at) {
        if (v < min || v > max) throw fail(at, v + " is outside the client range " + min + " to " + max);
        return v;
    }

    static String color(String c, String at) {
        if (c == null) throw fail(at, "missing");
        if (!isHex(c)) throw fail(at, "\"" + clip(c) + "\" is not a color, use #RGB, #RRGGBB or #AARRGGBB");
        return c;
    }

    static boolean isHex(String c) {
        if (c.length() < 2 || c.charAt(0) != '#') return false;
        int n = c.length() - 1;
        if (n != 3 && n != 6 && n != 8) return false;
        for (int i = 1; i < c.length(); i++) if (Character.digit(c.charAt(i), 16) < 0) return false;
        return true;
    }

    static void count(int n, int limit, String at, String what) {
        if (n > limit) throw fail(at, "more than " + limit + " " + what + " (" + n + ")");
    }

    static String chars(String s, int max, String at) {
        if (s == null) throw fail(at, "missing");
        if (s.length() > max) throw fail(at, s.length() + " characters, the client limit is " + max);
        return s;
    }

    static String oneLine(String s, String at) {
        if (s.indexOf('\n') >= 0) throw fail(at, "must be one line");
        return s;
    }

    static Text text(Text t, int max, String at) {
        if (t == null) throw fail(at, "missing");
        int n = t.visibleLength(at);
        if (n > max) throw fail(at, n + " visible characters, the client shows at most " + max);
        return t;
    }

    static String pattern(String value, Pattern pattern, String at, String expected) {
        if (value == null) throw fail(at, "missing");
        if (!pattern.matcher(value).matches()) throw fail(at, "\"" + clip(value) + "\" is not " + expected);
        return value;
    }

    static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    private static String clip(String s) {
        if (s == null) return "null";
        return s.length() <= 40 ? s : s.substring(0, 40) + "...";
    }
}
