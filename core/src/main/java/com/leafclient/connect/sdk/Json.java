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
        return s.getBytes(UTF_8).length;
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
