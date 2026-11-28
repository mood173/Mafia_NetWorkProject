package StreamSystem;

import com.mafia.common.model.Phase;

/** 화면에 보일 판의 단계. 공통 Phase를 그대로 따라가며, 단계를 정하는 것은 서버다. */
public enum gameStream {
    WAITING(Phase.WAITING), NIGHT(Phase.NIGHT), DAY_RESULT(Phase.DAY_RESULT),
    DAY_TALK(Phase.DAY_TALK), VOTE(Phase.VOTE), GAME_END(Phase.GAME_END);

    private final Phase phase;
    gameStream(Phase phase) { this.phase = phase; }
    public Phase phase() { return phase; }
    public static gameStream from(Phase phase) { return valueOf(phase.name()); }
}
