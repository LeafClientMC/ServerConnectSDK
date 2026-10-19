package com.leafclient.connect.sdk;

final class Bucket {

    private final double capacity;
    private final double perMs;
    private double tokens;
    private long last;
    private boolean started;

    Bucket(double perSecond, int burst) {
        this.capacity = burst;
        this.perMs = perSecond / 1000.0;
        this.tokens = burst;
    }

    private void refill(long now) {
        if (!started) {
            started = true;
            last = now;
        } else if (now > last) {
            tokens = Math.min(capacity, tokens + (now - last) * perMs);
            last = now;
        }
    }

    boolean has(int n, long now) {
        refill(now);
        return tokens >= n;
    }

    boolean take(int n, long now) {
        refill(now);
        if (tokens < n) return false;
        tokens -= n;
        return true;
    }
}
