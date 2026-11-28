package com.mafia.server.character;
import java.util.Map;
import java.util.Set;
/**
 * 판과 밤 능력 처리 사이의 약속(두 사람이 같이 정함).
 * 판은 지금 밤인지와 살아 있는지를 보고, 이쪽은 그 직업이 그 대상에게 능력을 쓸 수 있는지와 밤 결과를 정한다.
 */
public interface CharacterService {
    void beginNight(Set<String> alive, Map<String,RoleAbility> roles, Runnable onAllSubmitted);
    SubmitResult submit(String actor, NightAction action);
    NightResult endNight();
    void onPlayerLeft(String actor);
}
