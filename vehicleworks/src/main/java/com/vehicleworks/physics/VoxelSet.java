package com.vehicleworks.physics;

import java.util.Arrays;

/**
 * Open-addressing hash set/map of packed voxel coordinates to int values.
 * x/z within ±2^25, y within ±2^11 (any Minecraft world position). No boxing, no per-entry allocation.
 */
public final class VoxelSet {
    private static final long EMPTY = Long.MIN_VALUE;
    private long[] keys;
    private int[] values;
    private int size;
    private int mask;

    public VoxelSet() {
        this(64);
    }

    public VoxelSet(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, expected * 2) - 1) << 1;
        keys = new long[cap];
        values = new int[cap];
        Arrays.fill(keys, EMPTY);
        mask = cap - 1;
    }

    /** Packs coordinates like Minecraft's BlockPos: x and z 26 bits (±33M), y 12 bits (±2048). */
    public static long pack(int x, int y, int z) {
        return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
    }

    public static int unpackX(long k) { return (int) (k >> 38); }
    public static int unpackY(long k) { return (int) (k << 52 >> 52); }
    public static int unpackZ(long k) { return (int) (k << 26 >> 38); }

    private static int hash(long k) {
        k ^= (k >>> 33);
        k *= 0xff51afd7ed558ccdL;
        k ^= (k >>> 33);
        return (int) k;
    }

    public int size() {
        return size;
    }

    /** Remove all entries, keeping capacity. */
    public void clearAll() {
        if (size == 0) return;
        Arrays.fill(keys, EMPTY);
        size = 0;
    }

    public void put(int x, int y, int z, int value) {
        putKey(pack(x, y, z), value);
    }

    public void putKey(long key, int value) {
        if ((size + 1) * 2 > keys.length) grow();
        int i = hash(key) & mask;
        while (true) {
            long k = keys[i];
            if (k == EMPTY) {
                keys[i] = key;
                values[i] = value;
                size++;
                return;
            }
            if (k == key) {
                values[i] = value;
                return;
            }
            i = (i + 1) & mask;
        }
    }

    /** Returns the stored value or {@code def}. */
    public int get(int x, int y, int z, int def) {
        return getKey(pack(x, y, z), def);
    }

    public int getKey(long key, int def) {
        int i = hash(key) & mask;
        while (true) {
            long k = keys[i];
            if (k == EMPTY) return def;
            if (k == key) return values[i];
            i = (i + 1) & mask;
        }
    }

    public boolean contains(int x, int y, int z) {
        return getKey(pack(x, y, z), Integer.MIN_VALUE) != Integer.MIN_VALUE;
    }

    private void grow() {
        long[] ok = keys;
        int[] ov = values;
        keys = new long[ok.length * 2];
        values = new int[ok.length * 2];
        Arrays.fill(keys, EMPTY);
        mask = keys.length - 1;
        size = 0;
        for (int i = 0; i < ok.length; i++) {
            if (ok[i] != EMPTY) putKey(ok[i], ov[i]);
        }
    }

    /** Iterate all entries. */
    public void forEach(Visitor v) {
        for (int i = 0; i < keys.length; i++) {
            long k = keys[i];
            if (k != EMPTY) v.visit(unpackX(k), unpackY(k), unpackZ(k), values[i]);
        }
    }

    @FunctionalInterface
    public interface Visitor {
        void visit(int x, int y, int z, int value);
    }
}
