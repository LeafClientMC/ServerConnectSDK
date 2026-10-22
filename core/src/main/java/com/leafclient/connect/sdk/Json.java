package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

final class Json {

    static final Charset UTF_8 = Charset.forName("UTF-8");

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
        int n = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
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
        StringBuilder sb = new StringBuilder(len + 3);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            sb.append(Character.isISOControl(c) || c == ' ' || c == ' ' ? ' ' : c);
        }
        if (s.length() > max) sb.append("...");
        return sb.toString();
    }

    static boolean tooDeep(byte[] data, int max) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (inString) {
                if (b == '\\') i++;
                else if (b == '"') inString = false;
            } else if (b == '"') {
                inString = true;
            } else if (b == '{' || b == '[') {
                if (++depth > max) return true;
            } else if (b == '}' || b == ']') {
                depth--;
            }
        }
        return false;
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
