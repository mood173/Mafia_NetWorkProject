package com.mafia.server.game;
/**
 * 단계 제한 시간을 재는 시계. 실제 서버는 ScheduledPhaseTimer를 쓰고,
 * 검사에서는 원하는 순간에 시간 만료를 일으키는 가짜 시계를 끼워 기다리지 않고 확인한다.
 */
public interface PhaseTimer {
    interface Ticket { void cancel(); }
    Ticket schedule(int seconds, Runnable callback);
    long nowNanos();
}
