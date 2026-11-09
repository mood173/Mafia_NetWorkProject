package com.mafia.server.character;
import java.util.Map;
import java.util.Set;
/** 공동 계약. 판은 시점/생존, 캐릭터는 능력 유효성/결과 계산을 책임진다. */
public interface CharacterService {
    void beginNight(Set<String> alive, Map<String,RoleAbility> roles, Runnable onAllSubmitted);
    SubmitResult submit(String actor, NightAction action);
    NightResult endNight();
    void onPlayerLeft(String actor);
}
