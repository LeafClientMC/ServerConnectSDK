package com.leafclient.connect.sdk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Outbox {

    static final double RATE_PER_SECOND = Limits.MESSAGES_PER_SECOND * 0.8;
    static final int RATE_BURST = (int) (Limits.MESSAGES_BURST * 0.8);
    static final long OPEN_MARGIN_MS = 150;
    static final int[] TYPE_BURST = {16, 16, 2};
    private static final int PANELS = 0;
    private static final int VALUES = 1;
    private static final int TOASTS = 2;

    static final class Item {
        final String json;
        final int bytes;
        final String label;
        final String type;
        final boolean alone;

        Item(String json, String label, String type) {
            this(json, label, type, false);
        }

        Item(String json, String label, String type, boolean alone) {
            this.json = json;
            this.bytes = Json.utf8Length(json);
            this.label = label;
            this.type = type;
            this.alone = alone;
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

        Frame(String json, int cost, boolean lock, long seq, int[] need) {
            this(json, cost, lock, null, 0, seq, need);
        }

        Frame(String json, int cost, boolean lock, String cid, int part, long seq, int[] need) {
            this.data = json.getBytes(Json.UTF_8);
            this.cost = cost;
            this.lock = lock;
            this.cid = cid;
            this.part = part;
            this.seq = seq;
            this.need = need;
        }
    }

    static int[] need(List<Item> items) {
        int[] need = new int[3];
        for (Item item : items) {
            if ("panel.set".equals(item.type) || "panel.patch".equals(item.type)) need[PANELS]++;
            else if ("values.set".equals(item.type)) need[VALUES]++;
            else if ("toast".equals(item.type)) need[TOASTS]++;
        }
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

    void add(Item item) {
        Item last = queue.isEmpty() ? null : queue.get(queue.size() - 1);
        if (last != null && "values.set".equals(item.type) && "values.set".equals(last.type) && !item.alone && !last.alone) {
            queue.set(queue.size() - 1, new Item(mergeValues(last.json, item.json), item.label, item.type));
            return;
        }
        queue.add(item);
    }

    @SuppressWarnings("deprecation")
    static String mergeValues(String first, String second) {
        JsonObject base = new JsonParser().parse(first).getAsJsonObject();
        JsonObject next = new JsonParser().parse(second).getAsJsonObject();
        JsonObject set = base.has("set") ? base.getAsJsonObject("set") : new JsonObject();
        JsonArray del = base.has("del") ? base.getAsJsonArray("del") : new JsonArray();
        if (next.has("set")) {
            for (Map.Entry<String, JsonElement> e : next.getAsJsonObject("set").entrySet()) {
                set.add(e.getKey(), e.getValue());
                del = without(del, e.getKey());
            }
        }
        if (next.has("del")) {
            for (JsonElement k : next.getAsJsonArray("del")) {
                set.remove(k.getAsString());
                del = without(del, k.getAsString());
                del.add(k);
            }
        }
        base.remove("set");
        base.remove("del");
        if (set.entrySet().size() > 0) base.add("set", set);
        if (del.size() > 0) base.add("del", del);
        return Json.write(base);
    }

    private static JsonArray without(JsonArray list, String key) {
        JsonArray out = new JsonArray();
        for (JsonElement k : list) if (!key.equals(k.getAsString())) out.add(k);
        return out;
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

    void dropPartialChunkSet() {
        Frame head = frames.peek();
        if (head == null || head.cid == null || head.part == 0) return;
        java.util.Iterator<Frame> it = frames.iterator();
        while (it.hasNext()) if (head.cid.equals(it.next().cid)) it.remove();
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
        return Json.utf8Length(batchPrefix(rid)) + 2;
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
        List<byte[]> out = new ArrayList<byte[]>();
        while (true) {
            Frame m = frames.peek();
            Frame l = lockFrames.peek();
            if (l != null && (m == null || l.seq < m.seq) && all.has(1, now) && locks.has(1, now)) {
                all.take(1, now);
                locks.take(1, now);
                out.add(l.data);
                lockFrames.poll();
            } else if (m != null && (l == null || m.seq < l.seq || !locks.has(1, now)) && all.has(m.cost, now) && typesHave(m.need, now)) {
                all.take(m.cost, now);
                for (int k = 0; k < types.length; k++) types[k].take(m.need[k], now);
                toastsPerMinute.take(m.need[TOASTS], now);
                out.add(m.data);
                frames.poll();
            } else {
                break;
            }
        }
        if (frames.isEmpty() && pendingOpen != null) {
            long allowed = Math.max(lastOpen + Limits.OPEN_INTERVAL_MS, lastPlayerClose + Limits.OPEN_AFTER_CLOSE_MS) + OPEN_MARGIN_MS;
            if (now >= allowed && all.take(1, now)) {
                out.add(pendingOpen.json.getBytes(Json.UTF_8));
                refs.put(pendingOpen.type, pendingOpen.label);
                lastOpen = now;
                pendingOpen = null;
            }
        }
        return out;
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
        int groupBytes = 0;
        for (Item item : queue) {
            if (item.type.equals("mods.lock") || item.type.equals("mods.unlock")) {
                groupBytes = flushGroup(group, groupBytes);
                String rid = "l" + (++lockSeq);
                refs.put(rid, item.label);
                refs.put(item.type, item.label);
                lockFrames.add(new Frame(withRid(item.json, rid), 1, true, ++frameSeq, new int[3]));
                continue;
            }
            if (item.alone) groupBytes = flushGroup(group, groupBytes);
            String nextRid = "b" + (batchSeq + 1);
            if (!group.isEmpty() && (group.size() >= Limits.BATCH_MESSAGES || over(need(group), need(Collections.singletonList(item)))
                    || batchOverhead(nextRid) + groupBytes + group.size() + item.bytes > cap)) {
                groupBytes = flushGroup(group, groupBytes);
                nextRid = "b" + (batchSeq + 1);
            }
            if (batchOverhead(nextRid) + item.bytes > cap) {
                groupBytes = flushGroup(group, groupBytes);
                chunk(item, cap);
                continue;
            }
            group.add(item);
            groupBytes += item.bytes;
            if (item.alone) groupBytes = flushGroup(group, groupBytes);
        }
        flushGroup(group, groupBytes);
        queue.clear();
    }

    private int flushGroup(List<Item> group, int groupBytes) {
        if (group.isEmpty()) return 0;
        String rid = "b" + (++batchSeq);
        frames.add(new Frame(batchText(rid, group), 1, false, ++frameSeq, need(group)));
        refs.put(rid, describe(group));
        group.clear();
        return 0;
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
        refs.put(rid, describe(one));
        refs.put(cid, describe(one) + " (chunked in " + parts.size() + " parts)");
        for (int i = 0; i < parts.size(); i++) {
            String json = Json.write(Chunker.message(cid, i, parts.size(), parts.get(i)));
            if (Json.utf8Length(json) > cap) throw new IllegalStateException("chunk " + cid + " part " + i + " is over " + cap + " bytes");
            boolean last = i == parts.size() - 1;
            frames.add(new Frame(json, last ? 2 : 1, false, cid, i, ++frameSeq, last ? need(one) : new int[3]));
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
