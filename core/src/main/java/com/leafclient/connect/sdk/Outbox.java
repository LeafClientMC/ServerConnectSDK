package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Outbox {

    static final double RATE_PER_SECOND = Limits.MESSAGES_PER_SECOND * 0.8;
    static final int RATE_BURST = (int) (Limits.MESSAGES_BURST * 0.8);
    static final long OPEN_MARGIN_MS = 150;
    static final int[] TYPE_BURST = {16, 16, 2};
    static final int MAX_WAITING_MESSAGES = 512;
    private static final int PANELS = 0;
    private static final int VALUES = 1;
    private static final int TOASTS = 2;
    private static final int BATCH_OVERHEAD = batchPrefix("").length() + 2;

    static final class Item {
        String json;
        int bytes;
        int mergedBytes;
        final String label;
        final String type;
        final boolean alone;
        final String panel;
        final int[] need;
        final Map<String, JsonElement> set;
        final Set<String> del;

        Item(String json, String label, String type) {
            this(json, Json.utf8Length(json), label, type, false, null, null);
        }

        Item(String json, int bytes, String label, String type, boolean alone, String panel, JsonObject values) {
            this.json = json;
            this.bytes = bytes;
            this.mergedBytes = bytes;
            this.label = label;
            this.type = type;
            this.alone = alone;
            this.panel = panel;
            this.need = needOf(type);
            if (values != null && !alone && "values.set".equals(type)) {
                set = new LinkedHashMap<String, JsonElement>();
                del = new LinkedHashSet<String>();
                JsonElement s = values.get("set");
                if (s != null && s.isJsonObject()) for (Map.Entry<String, JsonElement> e : s.getAsJsonObject().entrySet()) set.put(e.getKey(), e.getValue());
                JsonElement d = values.get("del");
                if (d != null && d.isJsonArray()) for (JsonElement k : d.getAsJsonArray()) del.add(k.getAsString());
            } else {
                set = null;
                del = null;
            }
        }

        boolean canMerge(Item next, int maxBytes) {
            if (set.size() + del.size() + next.set.size() + next.del.size() > Limits.VALUES) return false;
            if (mergedBytes + next.bytes <= maxBytes) return true;
            ensureJson();
            mergedBytes = bytes;
            return mergedBytes + next.bytes <= maxBytes;
        }

        void merge(Item next) {
            mergedBytes += next.bytes;
            for (Map.Entry<String, JsonElement> e : next.set.entrySet()) {
                set.put(e.getKey(), e.getValue());
                del.remove(e.getKey());
            }
            for (String k : next.del) {
                set.remove(k);
                del.remove(k);
                del.add(k);
            }
            json = null;
        }

        void ensureJson() {
            if (json != null) return;
            JsonObject o = Json.envelope("values.set");
            if (!set.isEmpty()) {
                JsonObject s = new JsonObject();
                for (Map.Entry<String, JsonElement> e : set.entrySet()) s.add(e.getKey(), e.getValue());
                o.add("set", s);
            }
            if (!del.isEmpty()) {
                JsonArray d = new JsonArray();
                for (String k : del) d.add(new JsonPrimitive(k));
                o.add("del", d);
            }
            json = Json.write(o);
            bytes = Json.utf8Length(json);
        }
    }

    static final class Frame {
        final byte[] data;
        final int cost;
        final boolean lock;
        final String cid;
        final int part;
        final long seq;
        final int[] need;
        final int msgs;
        final List<String> panels;

        Frame(String json, int cost, boolean lock, String cid, int part, long seq, int[] need, int msgs, List<String> panels) {
            this.data = json.getBytes(Json.UTF_8);
            this.cost = cost;
            this.lock = lock;
            this.cid = cid;
            this.part = part;
            this.seq = seq;
            this.need = need;
            this.msgs = msgs;
            this.panels = panels;
        }
    }

    static int[] needOf(String type) {
        int[] need = new int[3];
        if ("panel.set".equals(type) || "panel.patch".equals(type)) need[PANELS]++;
        else if ("values.set".equals(type)) need[VALUES]++;
        else if ("toast".equals(type)) need[TOASTS]++;
        return need;
    }

    private static boolean over(int[] group, int[] item) {
        for (int k = 0; k < group.length; k++) {
            if (group[k] + item[k] > TYPE_BURST[k]) return true;
        }
        return false;
    }

    private final List<Item> queue = new ArrayList<Item>();
    private final ArrayDeque<Frame> frames = new ArrayDeque<Frame>();
    private final ArrayDeque<Frame> lockFrames = new ArrayDeque<Frame>();
    private long frameSeq;
    private int waiting;
    private final Bucket all = new Bucket(RATE_PER_SECOND, RATE_BURST);
    private final Bucket locks = new Bucket(Limits.LOCKS_PER_SECOND * 0.8, 1);
    private final Bucket[] types = {new Bucket(16, TYPE_BURST[PANELS]), new Bucket(16, TYPE_BURST[VALUES]), new Bucket(0.8, TYPE_BURST[TOASTS])};
    private final Bucket toastsPerMinute = new Bucket(16 / 60.0, 16);
    private final Map<String, String> refs = new LinkedHashMap<String, String>(64, 0.75f, false) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 256;
        }
    };
    private Item pendingOpen;
    private long lastOpen = Long.MIN_VALUE / 2;
    private long lastPlayerClose = Long.MIN_VALUE / 2;
    private int batchSeq;
    private int chunkSeq;
    private int lockSeq;
    private final int mergeBytes;
    private int dropped;
    private String lastProblem;

    Outbox(int cap) {
        this.mergeBytes = cap - batchOverhead("b2147483647");
    }

    void add(Item item) {
        Item last = queue.isEmpty() ? null : queue.get(queue.size() - 1);
        if (last != null && last.set != null && item.set != null && last.canMerge(item, mergeBytes)) {
            last.merge(item);
            return;
        }
        queue.add(item);
        waiting++;
    }

    String takeProblem() {
        if (dropped == 0) return null;
        String out = dropped + " message" + (dropped == 1 ? "" : "s") + ", the last: " + lastProblem;
        dropped = 0;
        lastProblem = null;
        return out;
    }

    @SuppressWarnings("deprecation")
    static String mergeValues(String first, String second) {
        Item a = new Item(first, 0, "values.set", "values.set", false, null, new JsonParser().parse(first).getAsJsonObject());
        Item b = new Item(second, 0, "values.set", "values.set", false, null, new JsonParser().parse(second).getAsJsonObject());
        a.merge(b);
        a.ensureJson();
        return a.json;
    }

    int waiting() {
        return waiting;
    }

    Set<String> clearQueued() {
        Set<String> dropped = new HashSet<String>();
        Iterator<Item> it = queue.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (item.type.equals("mods.lock") || item.type.equals("mods.unlock")) continue;
            if (item.panel != null) dropped.add(item.panel);
            it.remove();
        }
        for (Frame f : frames) dropped.addAll(f.panels);
        frames.clear();
        pendingOpen = null;
        waiting = queue.size() + lockFrames.size();
        return dropped;
    }

    void open(Item item) {
        pendingOpen = item;
    }

    String pendingOpenType() {
        return pendingOpen == null ? null : pendingOpen.type;
    }

    String pendingOpenLabel() {
        return pendingOpen == null ? null : pendingOpen.label;
    }

    void cancelOpen() {
        pendingOpen = null;
    }

    void playerClosed(long now) {
        lastPlayerClose = now;
    }

    boolean idle() {
        return queue.isEmpty() && frames.isEmpty() && lockFrames.isEmpty() && pendingOpen == null;
    }

    int waitingFrames() {
        return frames.size() + lockFrames.size();
    }

    String describe(String ref) {
        return ref == null ? null : refs.get(ref);
    }

    static int batchOverhead(String rid) {
        return BATCH_OVERHEAD + rid.length();
    }

    static String batchPrefix(String rid) {
        return "{\"v\":1,\"t\":\"batch\",\"rid\":\"" + rid + "\",\"msgs\":[";
    }

    static int fitsAlone(int itemBytes, int cap) {
        return itemBytes + batchOverhead("b2147483647") <= cap ? 1 : 0;
    }

    static int chunkParts(String batchText, int cap) {
        int budget = cap - Chunker.overhead("c2147483647");
        return Chunker.split(batchText, budget).size();
    }

    List<byte[]> drain(long now, int cap) {
        pack(cap);
        List<byte[]> out = null;
        while (true) {
            Frame m = frames.peek();
            Frame l = lockFrames.peek();
            if (l != null && (m == null || l.seq < m.seq) && all.has(1, now) && locks.has(1, now)) {
                all.take(1, now);
                locks.take(1, now);
                if (out == null) out = new ArrayList<byte[]>();
                out.add(l.data);
                lockFrames.poll();
                waiting -= l.msgs;
            } else if (m != null && (l == null || m.seq < l.seq || !locks.has(1, now)) && all.has(m.cost, now) && typesHave(m.need, now)) {
                all.take(m.cost, now);
                for (int k = 0; k < types.length; k++) types[k].take(m.need[k], now);
                toastsPerMinute.take(m.need[TOASTS], now);
                if (out == null) out = new ArrayList<byte[]>();
                out.add(m.data);
                frames.poll();
                waiting -= m.msgs;
            } else {
                break;
            }
        }
        if (frames.isEmpty() && pendingOpen != null) {
            long allowed = Math.max(lastOpen + Limits.OPEN_INTERVAL_MS, lastPlayerClose + Limits.OPEN_AFTER_CLOSE_MS) + OPEN_MARGIN_MS;
            if (now >= allowed && all.take(1, now)) {
                if (out == null) out = new ArrayList<byte[]>();
                out.add(pendingOpen.json.getBytes(Json.UTF_8));
                refs.put(pendingOpen.type, pendingOpen.label);
                lastOpen = now;
                pendingOpen = null;
            }
        }
        return out == null ? Collections.<byte[]>emptyList() : out;
    }

    private boolean typesHave(int[] need, long now) {
        for (int k = 0; k < types.length; k++) {
            if (!types[k].has(need[k], now)) return false;
        }
        return toastsPerMinute.has(need[TOASTS], now);
    }

    private void pack(int cap) {
        if (queue.isEmpty()) return;
        List<Item> group = new ArrayList<Item>();
        int[] groupNeed = new int[3];
        int groupBytes = 0;
        try {
            for (Item item : queue) {
                try {
                    if (item.set != null) item.ensureJson();
                    if (item.type.equals("mods.lock") || item.type.equals("mods.unlock")) {
                        groupBytes = flushGroup(group, groupNeed);
                        String rid = "l" + (++lockSeq);
                        refs.put(rid, item.label);
                        refs.put(item.type, item.label);
                        lockFrames.add(new Frame(withRid(item.json, rid), 1, true, null, 0, ++frameSeq, new int[3], 1, Collections.<String>emptyList()));
                        continue;
                    }
                    if (item.alone) groupBytes = flushGroup(group, groupNeed);
                    String nextRid = "b" + (batchSeq + 1);
                    if (!group.isEmpty() && (group.size() >= Limits.BATCH_MESSAGES || over(groupNeed, item.need)
                            || batchOverhead(nextRid) + groupBytes + group.size() + item.bytes > cap)) {
                        groupBytes = flushGroup(group, groupNeed);
                        nextRid = "b" + (batchSeq + 1);
                    }
                    if (batchOverhead(nextRid) + item.bytes > cap) {
                        groupBytes = flushGroup(group, groupNeed);
                        chunk(item, cap);
                        continue;
                    }
                    group.add(item);
                    groupBytes += item.bytes;
                    for (int k = 0; k < groupNeed.length; k++) groupNeed[k] += item.need[k];
                    if (item.alone) groupBytes = flushGroup(group, groupNeed);
                } catch (RuntimeException e) {
                    group.remove(item);
                    waiting--;
                    dropped++;
                    lastProblem = item.label + ": " + e.getMessage();
                }
            }
            flushGroup(group, groupNeed);
        } finally {
            queue.clear();
        }
    }

    private int flushGroup(List<Item> group, int[] groupNeed) {
        if (group.isEmpty()) return 0;
        String rid = "b" + (++batchSeq);
        frames.add(new Frame(batchText(rid, group), 1, false, null, 0, ++frameSeq, groupNeed.clone(), group.size(), panels(group)));
        refs.put(rid, describe(group));
        group.clear();
        Arrays.fill(groupNeed, 0);
        return 0;
    }

    private static List<String> panels(List<Item> group) {
        List<String> out = null;
        for (Item item : group) {
            if (item.panel == null) continue;
            if (out == null) out = new ArrayList<String>();
            out.add(item.panel);
        }
        return out == null ? Collections.<String>emptyList() : out;
    }

    private void chunk(Item item, int cap) {
        String rid = "b" + (++batchSeq);
        List<Item> one = new ArrayList<Item>();
        one.add(item);
        String text = batchText(rid, one);
        String cid = "c" + (++chunkSeq);
        List<String> parts = Chunker.split(text, cap - Chunker.overhead(cid));
        if (parts.size() > Limits.CHUNK_PARTS) {
            throw new IllegalStateException(item.label + " needs " + parts.size() + " chunks, the client accepts " + Limits.CHUNK_PARTS);
        }
        List<String> texts = new ArrayList<String>(parts.size());
        for (int i = 0; i < parts.size(); i++) {
            String json = Json.write(Chunker.message(cid, i, parts.size(), parts.get(i)));
            if (Json.utf8Length(json) > cap) throw new IllegalStateException("chunk " + cid + " part " + i + " is over " + cap + " bytes");
            texts.add(json);
        }
        refs.put(rid, describe(one));
        refs.put(cid, describe(one) + " (chunked in " + parts.size() + " parts)");
        List<String> panels = panels(one);
        for (int i = 0; i < texts.size(); i++) {
            boolean last = i == texts.size() - 1;
            frames.add(new Frame(texts.get(i), last ? 2 : 1, false, cid, i, ++frameSeq, last ? item.need.clone() : new int[3], last ? 1 : 0, panels));
        }
    }

    static String batchText(String rid, List<Item> group) {
        StringBuilder sb = new StringBuilder(batchPrefix(rid));
        for (int i = 0; i < group.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(group.get(i).json);
        }
        return sb.append("]}").toString();
    }

    private static String withRid(String json, String rid) {
        return "{\"rid\":\"" + rid + "\"," + json.substring(1);
    }

    private static String describe(List<Item> group) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < group.size(); i++) {
            if (i > 0) sb.append(", ");
            if (i == 12) {
                sb.append(group.size() - 12).append(" more");
                break;
            }
            sb.append(group.get(i).label);
        }
        return sb.toString();
    }
}
