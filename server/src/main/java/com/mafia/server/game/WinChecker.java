package com.mafia.server.game;
import com.mafia.common.model.Team;
import java.util.Collection;
import java.util.Optional;
public final class WinChecker {
    public Optional<Team> winner(Collection<Team> alive) {
        long mafia=alive.stream().filter(t -> t==Team.MAFIA).count();
        if(mafia==0) return Optional.of(Team.CITIZEN);
        return mafia>=alive.size()-mafia ? Optional.of(Team.MAFIA) : Optional.empty();
    }
}
