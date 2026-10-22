package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class TestWire implements Transport {
    final List<byte[]> frames = new ArrayList<byte[]>();
    final int max;
    RuntimeException failNext;

    TestWire(int max) {
        this.max = max;
    }

    @Override
    public void send(byte[] data) {
        if (failNext != null) {
            RuntimeException e = failNext;
            failNext = null;
            throw e;
        }
        frames.add(data);
    }

    @Override
    public int maxMessageBytes() {
        return max;
    }

    String text(int i) {
        return new String(frames.get(i), StandardCharsets.UTF_8);
    }

    @SuppressWarnings("deprecation")
    static JsonObject parse(String json) {
        return new JsonParser().parse(json).getAsJsonObject();
    }
}
