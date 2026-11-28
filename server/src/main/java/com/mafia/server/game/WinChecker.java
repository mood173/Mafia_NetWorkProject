package com.mafia.server.game;
import com.mafia.common.model.Team;
import java.util.Collection;
import java.util.Optional;
/**
 * 승패 판정. 살아 있는 사람들의 편만 보고 정한다.
 * 마피아가 0명이면 시민 승리, 마피아 수가 나머지 수 이상이면 마피아 승리, 그 외는 계속 진행.
 * GameSession은 밤 결과를 적용한 직후와 처형 직후에 이것을 부른다.
 */
public final class WinChecker {
    public Optional<Team> winner(Collection<Team> alive) {
        long mafia=alive.stream().filter(t -> t==Team.MAFIA).count();
        if(mafia==0) return Optional.of(Team.CITIZEN);
        return mafia>=alive.size()-mafia ? Optional.of(Team.MAFIA) : Optional.empty();
    }
}
