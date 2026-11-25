package com.mafia.server.character.night;

import com.mafia.common.model.Team;
import com.mafia.server.character.*;

import java.util.*;

/**
 * 한 판의 밤 능력 접수와 결과 계산. 판마다 새로 만든다 (GameSessionFactory의 supplier).
 *
 * 책임 경계: 판(GameSession)은 "지금 밤인가, 살아 있는가"를, 이 클래스는 "그 직업이 그 대상에게
 * 그 능력을 쓸 수 있는가, 밤 결과가 무엇인가"를 판단한다. 판의 잠금 안에서만 불리므로 스스로 잠그지 않는다.
 *
 * 규칙 (제안서 5장, 팀 합의):
 * - 능력은 밤마다 한 번 확정이다. 받아들여진 뒤 다시 내면 거절한다 (화면도 사용 후 잠근다).
 * - 거절된 시도는 한 번에 포함되지 않는다.
 * - 공격 대상과 보호 대상이 같으면 아무도 죽지 않고, 결과에 '의사가 막음'을 표시한다. 조사 결과는 경찰 본인에게만 간다.
 * - 살아 있는 능력자가 모두 내면 판에 한 번 알린다. 안 낸 사람이 나가서 그렇게 된 경우도 같다.
 */
public final class DefaultCharacterService implements CharacterService {
    private final Map<String, NightAction> actions = new LinkedHashMap<>();
    private Set<String> alive = new LinkedHashSet<>();
    private Map<String, RoleAbility> roles = Map.of();
    private Runnable onAllSubmitted = () -> { };
    private boolean open;

    @Override
    public void beginNight(Set<String> alive, Map<String, RoleAbility> roles, Runnable onAllSubmitted) {
        this.alive = new LinkedHashSet<>(alive);
        this.roles = Map.copyOf(roles);
        this.onAllSubmitted = onAllSubmitted;
        actions.clear();
        open = true;
        notifyIfAllSubmitted();
    }

    @Override
    public SubmitResult submit(String actor, NightAction action) {
        if (!open) return reject("지금은 밤 능력을 쓸 수 없습니다");
        RoleAbility role = roles.get(actor);
        if (role == null || !alive.contains(actor)) return reject("살아 있는 참가자만 능력을 쓸 수 있습니다");
        if (role.nightAction().isEmpty() || role.nightAction().get() != action.type())
            return reject("직업에 없는 능력입니다");
        if (!alive.contains(action.target())) return reject("살아 있는 대상만 고를 수 있습니다");
        if (!role.canTarget(actor, action.target(), roles)) return reject("그 대상에게는 능력을 쓸 수 없습니다");
        if (actions.containsKey(actor)) return reject("이미 능력을 사용했습니다");
        actions.put(actor, action);
        notifyIfAllSubmitted();
        return new SubmitResult(true, "");
    }

    @Override
    public NightResult endNight() {
        open = false;
        String attacked = null;
        Set<String> protectedTargets = new HashSet<>();
        Map<String, NightResult.PoliceFinding> findings = new LinkedHashMap<>();
        for (Map.Entry<String, NightAction> entry : actions.entrySet()) {
            String actor = entry.getKey();
            NightAction action = entry.getValue();
            if (!alive.contains(actor) || !alive.contains(action.target())) continue;
            switch (action.type()) {
                case ATTACK -> { if (attacked == null) attacked = action.target(); } // 마피아가 여럿이면 먼저 낸 대상
                case HEAL -> protectedTargets.add(action.target());
                case CHECK -> findings.put(actor, new NightResult.PoliceFinding(action.target(),
                        roles.get(action.target()).team() == Team.MAFIA));
            }
        }
        boolean saved = attacked != null && protectedTargets.contains(attacked);
        List<String> deaths = attacked != null && !saved ? List.of(attacked) : List.of();
        return new NightResult(deaths, findings, saved);
    }

    @Override
    public void onPlayerLeft(String actor) {
        alive.remove(actor);
        actions.remove(actor);
        actions.values().removeIf(action -> action.target().equals(actor));
        // 아직 안 낸 마지막 능력자가 나가면 남은 사람은 모두 낸 상태다. 시간 만료를 기다리지 않는다.
        notifyIfAllSubmitted();
    }

    private void notifyIfAllSubmitted() {
        if (!open) return;
        boolean allIn = alive.stream()
                .filter(name -> roles.get(name).nightAction().isPresent())
                .allMatch(actions::containsKey);
        if (!allIn) return;
        Runnable callback = onAllSubmitted;
        onAllSubmitted = () -> { };   // 같은 밤에 두 번 알리지 않는다
        callback.run();
    }

    private static SubmitResult reject(String reason) {
        return new SubmitResult(false, reason);
    }
}
