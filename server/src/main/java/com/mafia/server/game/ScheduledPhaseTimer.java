package com.mafia.server.game;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
public final class ScheduledPhaseTimer implements PhaseTimer {
    private final ScheduledExecutorService executor;
    public ScheduledPhaseTimer(ScheduledExecutorService executor) { this.executor=executor; }
    public Ticket schedule(int seconds, Runnable callback) {
        var future=executor.schedule(callback, seconds, TimeUnit.SECONDS);
        return () -> future.cancel(false);
    }
    public long nowNanos() { return System.nanoTime(); }
}
