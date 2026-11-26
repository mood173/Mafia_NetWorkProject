package com.mafia.server.character.role;

import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import com.mafia.server.character.ActionType;
import com.mafia.server.character.RoleAbility;

import java.util.Map;
import java.util.Optional;

/** 경찰: 밤마다 자신을 뺀 한 명이 마피아인지 조사한다. 결과는 경찰에게만 간다. */
public final class PoliceRole implements RoleAbility {
    public RoleName name() { return RoleName.POLICE; }
    public Team team() { return Team.CITIZEN; }
    public Optional<ActionType> nightAction() { return Optional.of(ActionType.CHECK); }
    public boolean canTarget(String actor, String target, Map<String, RoleAbility> roles) {
        return roles.containsKey(target) && !actor.equals(target);
    }
}
