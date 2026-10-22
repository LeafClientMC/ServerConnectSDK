package com.leafclient.connect.sdk;

final class Throttle {

    private final Bucket bucket;
    private int suppressed;

    Throttle(double perSecond, int burst) {
        this.bucket = new Bucket(perSecond, burst);
    }

    synchronized String admit(String line, long now) {
        if (!bucket.take(1, now)) {
            suppressed++;
            return null;
        }
        if (suppressed == 0) return line;
        String out = line + " (" + suppressed + " similar lines suppressed)";
        suppressed = 0;
        return out;
    }
}
