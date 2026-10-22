package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

final class Json {

    static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final ThreadLocal<char[]> SCRATCH = new ThreadLocal<char[]>() {
        @Override
        protected char[] initialValue() {
            return new char[1024];
        }
    };

    private Json() {}

    static JsonPrimitive number(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return new JsonPrimitive((long) v);
        return new JsonPrimitive(v);
    }

    static void num(JsonObject o, String key, double v) {
        o.add(key, number(v));
    }

    static void str(JsonObject o, String key, String v) {
        if (v != null) o.addProperty(key, v);
    }

    static JsonArray numbers(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) a.add(number(v));
        return a;
    }

    static JsonArray strings(List<String> values) {
        JsonArray a = new JsonArray();
        for (String v : values) a.add(new JsonPrimitive(v));
        return a;
    }

    static JsonObject envelope(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("t", type);
        return o;
    }

    static String write(JsonElement e) {
        return e.toString();
    }

    static int utf8Length(String s) {
        int len = s.length();
        if (len < 128) return utf8LengthFrom(s, 0);
        char[] buf = SCRATCH.get();
        for (int start = 0; start < len; start += buf.length) {
            int end = Math.min(len, start + buf.length);
            s.getChars(start, end, buf, 0);
            int high = 0;
            for (int i = 0; i < end - start; i++) high |= buf[i];
            if (high >= 0x80) return start + utf8LengthFrom(s, start);
        }
        return len;
    }

    private static int utf8LengthFrom(String s, int from) {
        int len = s.length();
        int n = 0;
        for (int i = from; i < len; i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                n++;
            } else if (c < 0x800) {
                n += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < len && Character.isLowSurrogate(s.charAt(i + 1))) {
                n += 4;
                i++;
            } else if (Character.isSurrogate(c)) {
                n++;
            } else {
                n += 3;
            }
        }
        return n;
    }

    static Long integer(JsonElement e) {
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
        try {
            return new java.math.BigDecimal(e.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException ex) {
            return null;
        }
    }

    static String clean(String s, int max) {
        if (s == null) return null;
        int len = Math.min(s.length(), max);
        int first = 0;
        while (first < len && !unsafe(s.charAt(first))) first++;
        if (first == s.length()) return s;
        StringBuilder sb = new StringBuilder(len + 3);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            sb.append(unsafe(c) ? ' ' : c);
        }
        if (s.length() > max) sb.append("...");
        return sb.toString();
    }

    static boolean unsafe(char c) {
        return Character.isISOControl(c) || c == ' ' || c == ' ' || c == '‎' || c == '‏' || c == '؜'
                || (c >= '‪' && c <= '‮') || (c >= '⁦' && c <= '⁩');
    }

    static boolean strictWithin(String text, int maxDepth) {
        JsonReader r = new JsonReader(new StringReader(text));
        try {
            int depth = 0;
            do {
                switch (r.peek()) {
                    case BEGIN_OBJECT:
                        if (++depth > maxDepth) return false;
                        r.beginObject();
                        break;
                    case BEGIN_ARRAY:
                        if (++depth > maxDepth) return false;
                        r.beginArray();
                        break;
                    case END_OBJECT:
                        depth--;
                        r.endObject();
                        break;
                    case END_ARRAY:
                        depth--;
                        r.endArray();
                        break;
                    case NAME:
                        r.nextName();
                        break;
                    case STRING:
                    case NUMBER:
                        r.nextString();
                        break;
                    case BOOLEAN:
                        r.nextBoolean();
                        break;
                    case NULL:
                        r.nextNull();
                        break;
                    default:
                        return false;
                }
            } while (depth > 0);
            return r.peek() == JsonToken.END_DOCUMENT;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static int depth(JsonElement e) {
        if (e == null || e.isJsonNull() || e.isJsonPrimitive()) return 0;
        int max = 0;
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) max = Math.max(max, depth(a.get(i)));
        } else {
            for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) max = Math.max(max, depth(entry.getValue()));
        }
        return max + 1;
    }

    static JsonElement copy(JsonElement e) {
        if (e == null || e.isJsonNull() || e.isJsonPrimitive()) return e;
        if (e.isJsonArray()) {
            JsonArray out = new JsonArray();
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) out.add(copy(a.get(i)));
            return out;
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) out.add(entry.getKey(), copy(entry.getValue()));
        return out;
    }

    static String getString(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static boolean getBool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o == null ? null : o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) return fallback;
        return e.getAsBoolean();
    }
}
