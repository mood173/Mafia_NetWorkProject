package com.mafia.server.game;
public interface PhaseTimer {
    interface Ticket { void cancel(); }
    Ticket schedule(int seconds, Runnable callback);
    long nowNanos();
}
