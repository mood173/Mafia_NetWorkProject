package com.mafia.server.character;
import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import java.util.Map;
import java.util.Optional;
/** v2 계약. 실제 직업 클래스는 경탁 구현과 교체한다. */
public interface RoleAbility {
    RoleName name();
    Team team();
    Optional<ActionType> nightAction();
    boolean canTarget(String actor, String target, Map<String,RoleAbility> roles);
}
