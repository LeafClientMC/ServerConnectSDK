package com.leafclient.connect.sdk;

import com.google.gson.JsonObject;

final class Outgoing {

    static final Runnable NOTHING = new Runnable() {
        @Override
        public void run() {
        }
    };

    final JsonObject json;
    final String type;
    final String label;
    final Runnable commit;

    Outgoing(JsonObject json, String label, Runnable commit) {
        this.json = json;
        this.type = json.get("t").getAsString();
        this.label = label;
        this.commit = commit == null ? NOTHING : commit;
    }

    static Outgoing simple(JsonObject json, String label) {
        return new Outgoing(json, label, null);
    }

    boolean isLock() {
        return "mods.lock".equals(type) || "mods.unlock".equals(type);
    }

    boolean isOpen() {
        return "menu.open".equals(type) || "panel.open".equals(type);
    }
}
