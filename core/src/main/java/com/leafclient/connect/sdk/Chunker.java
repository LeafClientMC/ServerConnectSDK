package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class Chunker {

    private Chunker() {}

    static int overhead(String cid) {
        JsonObject o = message(cid, 99, 99, "");
        return Json.utf8Length(Json.write(o)) + 8;
    }

    static JsonObject message(String cid, int i, int n, String part) {
        JsonObject o = Json.envelope("chunk");
        o.addProperty("rid", cid);
        o.addProperty("cid", cid);
        o.addProperty("i", i);
        o.addProperty("n", n);
        o.addProperty("d", part);
        return o;
    }

    static List<String> split(String text, int partBudget) {
        if (partBudget < 16) throw new IllegalArgumentException("chunk budget too small: " + partBudget);
        List<String> parts = new ArrayList<String>();
        int start = 0;
        int used = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int width = 1;
            int bytes;
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                width = 2;
                bytes = 4;
            } else {
                bytes = escapedBytes(c);
            }
            if (used + bytes > partBudget) {
                parts.add(text.substring(start, i));
                start = i;
                used = 0;
            }
            used += bytes;
            i += width;
        }
        if (start < text.length() || parts.isEmpty()) parts.add(text.substring(start));
        return parts;
    }

    static int escapedBytes(char c) {
        if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f') return 2;
        if (c < 0x20 || c == ' ' || c == ' ') return 6;
        if (c < 0x80) return 1;
        if (c < 0x800) return 2;
        if (Character.isSurrogate(c)) return 1;
        return 3;
    }
}
