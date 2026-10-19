package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a Leaf Client told the server in its {@code hello}: client name and version, Minecraft version, mod loader,
 * protocol range, caps and lockable mods. Get it with {@link Session#client()}. Missing text fields are empty strings.
 */
public final class ClientInfo {

    private final String name;
    private final String version;
    private final String minecraft;
    private final String loader;
    private final int protocolMin;
    private final int protocolMax;
    private final Set<String> caps;
    private final List<String> lockable;

    private ClientInfo(String name, String version, String minecraft, String loader, int protocolMin, int protocolMax,
                       Set<String> caps, List<String> lockable) {
        this.name = name;
        this.version = version;
        this.minecraft = minecraft;
        this.loader = loader;
        this.protocolMin = protocolMin;
        this.protocolMax = protocolMax;
        this.caps = caps;
        this.lockable = lockable;
    }

    static ClientInfo parse(JsonObject hello) {
        JsonObject client = object(hello, "client");
        JsonObject proto = object(hello, "proto");
        return new ClientInfo(
                orEmpty(Json.getString(client, "name")),
                orEmpty(Json.getString(client, "version")),
                orEmpty(Json.getString(client, "mc")),
                orEmpty(Json.getString(client, "loader")),
                integer(proto, "min", 1),
                integer(proto, "max", 1),
                Collections.unmodifiableSet(new LinkedHashSet<String>(strings(hello, "caps"))),
                Collections.unmodifiableList(strings(hello, "lockable")));
    }

    /**
     * Returns the client name.
     *
     * @return the name, or an empty string
     */
    public String name() {
        return name;
    }

    /**
     * Returns the client version.
     *
     * @return the version, or an empty string
     */
    public String version() {
        return version;
    }

    /**
     * Returns the Minecraft version the client runs.
     *
     * @return the version, or an empty string
     */
    public String minecraftVersion() {
        return minecraft;
    }

    /**
     * Returns the mod loader the client runs on.
     *
     * @return the loader, or an empty string
     */
    public String loader() {
        return loader;
    }

    /**
     * Returns the lowest Server Connect protocol version the client speaks.
     *
     * @return the version, 1 if the client sent none
     */
    public int protocolMin() {
        return protocolMin;
    }

    /**
     * Returns the highest Server Connect protocol version the client speaks.
     *
     * @return the version, 1 if the client sent none
     */
    public int protocolMax() {
        return protocolMax;
    }

    /**
     * Returns the caps: the features this client supports, such as {@code toast}, {@code waypoint}, {@code timers},
     * {@code mods.lock} or a panel place such as {@code hud}. The SDK reads at most 256.
     *
     * @return the caps, unmodifiable
     */
    public Set<String> caps() {
        return caps;
    }

    /**
     * Tells whether the client supports a feature. {@link Session#send(Message)} skips messages whose cap is missing.
     *
     * @param cap the cap name
     * @return true if the client listed it
     */
    public boolean has(String cap) {
        return caps.contains(cap);
    }

    /**
     * Returns the ids of the mods the server can lock with {@link Session#lockMods(String, String...)}.
     * The SDK reads at most 256.
     *
     * @return the mod ids, unmodifiable
     */
    public List<String> lockableMods() {
        return lockable;
    }

    boolean speaks(int version) {
        return protocolMin <= version && version <= protocolMax;
    }

    @Override
    public String toString() {
        return name + " " + version + " (" + minecraft + " " + loader + ", protocol " + protocolMin + "-" + protocolMax + ")";
    }

    private static JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return fallback;
        return e.getAsInt();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<String>();
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) return out;
        JsonArray a = e.getAsJsonArray();
        for (int i = 0; i < a.size() && i < 256; i++) {
            JsonElement v = a.get(i);
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) out.add(v.getAsString());
        }
        return out;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
