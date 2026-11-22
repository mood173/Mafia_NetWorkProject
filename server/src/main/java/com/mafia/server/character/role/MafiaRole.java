package com.mafia.server.character.role;

import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import com.mafia.server.character.ActionType;
import com.mafia.server.character.RoleAbility;

import java.util.Map;
import java.util.Optional;

/** 마피아: 밤마다 마피아 팀이 아닌 한 명을 공격한다 (자기 자신 포함 마피아 팀은 대상이 아니다). */
public final class MafiaRole implements RoleAbility {
    public RoleName name() { return RoleName.MAFIA; }
    public Team team() { return Team.MAFIA; }
    public Optional<ActionType> nightAction() { return Optional.of(ActionType.ATTACK); }
    public boolean canTarget(String actor, String target, Map<String, RoleAbility> roles) {
        RoleAbility victim = roles.get(target);
        return victim != null && victim.team() != Team.MAFIA;
    }
}
