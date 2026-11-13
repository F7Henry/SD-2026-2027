package raft04;

import java.util.function.Consumer;

public final class HeartbeatMonitor {
    public enum State { DESCONHECIDO, ATIVO, SUSPEITO }

    private final int partition;
    private final long timeoutNanos;
    private final Consumer<String> log;
    private State state = State.DESCONHECIDO;
    private long lastNanos;
    private long term = 0;
    private int leader = 0;
    private long hb = 0;

    public HeartbeatMonitor(int partition, long periodMs, int factor,
                            Consumer<String> log) {
        this.partition = partition;
        timeoutNanos = periodMs * factor * 1_000_000L;
        this.log = log;
        lastNanos = System.nanoTime();
    }

    public synchronized void onHeartbeat(long newTerm, int newLeader, long newHb) {
        if (newTerm < term) return;                       // líder obsoleto
        if (newTerm == term && newHb <= hb) return;       // duplicado ou atrasado
        if (newTerm > term) {
            log.accept("NOVO_LIDER;p" + partition + ";termo=" + newTerm
                    + ";lider=n" + newLeader);
        } else if (newHb != hb + 1) {
            log.accept("HB_PERDIDO;p" + partition + ";esperado=" + (hb + 1)
                    + ";recebido=" + newHb);
        }
        if (state == State.SUSPEITO) {
            log.accept("RETOMA;p" + partition + ";termo=" + newTerm
                    + ";hb=" + newHb);
        }
        term = newTerm;
        leader = newLeader;
        hb = newHb;
        lastNanos = System.nanoTime();
        state = State.ATIVO;
    }

    public synchronized void check() {
        long elapsed = System.nanoTime() - lastNanos;
        if (state != State.SUSPEITO && elapsed > timeoutNanos) {
            state = State.SUSPEITO;
            log.accept("SUSPEITA;p" + partition + ";silencio_ms="
                    + elapsed / 1_000_000);
        }
    }

    public synchronized State state() { return state; }

    public synchronized long term() { return term; }
}