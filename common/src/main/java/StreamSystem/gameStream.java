package StreamSystem;

import com.mafia.common.model.Phase;

/** 판 안의 단계. 기존 공통 Phase를 감싼다. 단계 판정의 정본은 서버다. */
public enum gameStream {
    WAITING(Phase.WAITING), NIGHT(Phase.NIGHT), DAY_RESULT(Phase.DAY_RESULT),
    DAY_TALK(Phase.DAY_TALK), VOTE(Phase.VOTE), GAME_END(Phase.GAME_END);

    private final Phase phase;
    gameStream(Phase phase) { this.phase = phase; }
    public Phase phase() { return phase; }
    public static gameStream from(Phase phase) { return valueOf(phase.name()); }
}
