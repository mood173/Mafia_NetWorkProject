package com.mafia.server.character.role;

import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import com.mafia.server.character.ActionType;
import com.mafia.server.character.RoleAbility;

import java.util.Map;
import java.util.Optional;

/** 의사: 밤마다 한 명을 보호한다. 제안서 5.2에 따라 자기 자신도 보호할 수 있다. */
public final class DoctorRole implements RoleAbility {
    public RoleName name() { return RoleName.DOCTOR; }
    public Team team() { return Team.CITIZEN; }
    public Optional<ActionType> nightAction() { return Optional.of(ActionType.HEAL); }
    public boolean canTarget(String actor, String target, Map<String, RoleAbility> roles) {
        return roles.containsKey(target);
    }
}
