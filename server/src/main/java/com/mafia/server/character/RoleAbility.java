package com.mafia.server.character;
import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import java.util.Map;
import java.util.Optional;
/** 직업 하나가 알려 주는 것: 편, 밤 능력, 그 능력을 쓸 수 있는 대상. 실제 직업 네 가지는 character.role에 있다. */
public interface RoleAbility {
    RoleName name();
    Team team();
    Optional<ActionType> nightAction();
    boolean canTarget(String actor, String target, Map<String,RoleAbility> roles);
}
