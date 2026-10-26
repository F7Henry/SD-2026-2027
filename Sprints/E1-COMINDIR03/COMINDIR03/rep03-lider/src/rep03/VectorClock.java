package rep03;

import java.util.Arrays;

public final class VectorClock {
    public enum Order { BEFORE, AFTER, EQUAL, CONCURRENT }

    private final long[] v;

    public VectorClock(int n) {
        if (n <= 0) throw new IllegalArgumentException("dimensao invalida");
        v = new long[n];
    }

    private VectorClock(long[] v) { this.v = v; }

    public void merge(VectorClock other) {
        if (v.length != other.v.length) throw new IllegalArgumentException("dimensoes diferentes");
        for (int i = 0; i < v.length; i++) v[i] = Math.max(v[i], other.v[i]);
    }

    public void tick(int k) { v[k]++; }

    public long get(int k) { return v[k]; }

    public int size() { return v.length; }

    public VectorClock copy() { return new VectorClock(v.clone()); }

    public Order compare(VectorClock other) {
        if (v.length != other.v.length) throw new IllegalArgumentException("dimensoes diferentes");
        boolean less = false, greater = false;
        for (int i = 0; i < v.length; i++) {
            less |= v[i] < other.v[i];
            greater |= v[i] > other.v[i];
        }
        if (less && greater) return Order.CONCURRENT;
        if (less) return Order.BEFORE;
        if (greater) return Order.AFTER;
        return Order.EQUAL;
    }

    @Override public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) result.append(',');
            result.append(v[i]);
        }
        return result.toString();
    }

    public static VectorClock parse(String value, int n) {
        String[] fields = value.trim().split(",");
        if (fields.length != n) throw new IllegalArgumentException("dimensao do vetor invalida");
        long[] values = new long[n];
        for (int i = 0; i < n; i++) values[i] = Long.parseLong(fields[i].trim());
        return new VectorClock(values);
    }

    @Override public boolean equals(Object other) {
        return other instanceof VectorClock && Arrays.equals(v, ((VectorClock) other).v);
    }

    @Override public int hashCode() { return Arrays.hashCode(v); }
}
