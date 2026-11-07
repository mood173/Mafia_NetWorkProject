package com.mafia.common.model;

/** 판의 단계. 순서: WAITING → NIGHT → DAY_RESULT → DAY_TALK → VOTE → (NIGHT 반복) → GAME_END */
public enum Phase {
    WAITING,
    NIGHT,
    DAY_RESULT,
    DAY_TALK,
    VOTE,
    GAME_END
}
