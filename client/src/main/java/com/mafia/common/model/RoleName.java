package com.mafia.common.model;

/** 직업 이름과 소속 팀. 능력 규칙은 서버 캐릭터 패키지가 맡는다. */
public enum RoleName {
    CITIZEN(Team.CITIZEN),
    POLICE(Team.CITIZEN),
    DOCTOR(Team.CITIZEN),
    MAFIA(Team.MAFIA);

    private final Team team;

    RoleName(Team team) {
        this.team = team;
    }

    public Team team() {
        return team;
    }
}
