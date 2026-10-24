package rep03;

public final class Partitioner {
    private final int n;
    public Partitioner(int n) {
        if (n < 1) throw new IllegalArgumentException("NÃºmero invÃ¡lido");
        this.n = n;
    }
    public int of(String sensorId) {
        return Math.floorMod(sensorId.hashCode(), n);
    }
    public int count() { return n; }
}

