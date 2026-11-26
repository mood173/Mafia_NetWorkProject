package com.mafia.server.character.role;

import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import com.mafia.server.character.ActionType;
import com.mafia.server.character.RoleAbility;

import java.util.Map;
import java.util.Optional;

/** 시민: 밤 능력 없음. 낮 토론과 투표에만 참여한다. */
public final class CitizenRole implements RoleAbility {
    public RoleName name() { return RoleName.CITIZEN; }
    public Team team() { return Team.CITIZEN; }
    public Optional<ActionType> nightAction() { return Optional.empty(); }
    public boolean canTarget(String actor, String target, Map<String, RoleAbility> roles) { return false; }
}
