package com.mafia.server.character.night;

import com.mafia.common.model.RoleName;
import com.mafia.server.character.*;
import com.mafia.server.character.role.RoleCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 밤 능력 접수와 결과 계산. 판(GameSession)은 시점·생존을, 이 서비스는 능력 유효성·결과를 맡는다. */
final class DefaultCharacterServiceTest {
    private final Map<String, RoleAbility> roles = new LinkedHashMap<>();
    private final Set<String> alive = new LinkedHashSet<>(List.of("maf", "pol", "doc", "c1", "c2", "c3"));
    private final AtomicInteger completed = new AtomicInteger();
    private final DefaultCharacterService service = new DefaultCharacterService();

    @BeforeEach
    void beginNight() {
        Map<RoleName, RoleAbility> catalog = RoleCatalog.standard();
        roles.put("maf", catalog.get(RoleName.MAFIA));
        roles.put("pol", catalog.get(RoleName.POLICE));
        roles.put("doc", catalog.get(RoleName.DOCTOR));
        for (String c : List.of("c1", "c2", "c3")) roles.put(c, catalog.get(RoleName.CITIZEN));
        service.beginNight(alive, roles, completed::incrementAndGet);
    }

    private SubmitResult submit(String actor, ActionType type, String target) {
        return service.submit(actor, new NightAction(type, target));
    }

    @Test
    void healOnSameTargetCancelsAttack() {
        submit("maf", ActionType.ATTACK, "c1");
        submit("doc", ActionType.HEAL, "c1");
        assertEquals(List.of(), service.endNight().deaths());
    }

    @Test
    void attackOnUnprotectedTargetKillsOnlyThatTarget() {
        submit("maf", ActionType.ATTACK, "c1");
        submit("doc", ActionType.HEAL, "c2");
        assertEquals(List.of("c1"), service.endNight().deaths());
    }

    @Test
    void noAttackMeansNoDeath() {
        submit("doc", ActionType.HEAL, "doc");
        assertEquals(List.of(), service.endNight().deaths());
    }

    @Test
    void policeFindingGoesOnlyToPoliceWithTeamResult() {
        submit("pol", ActionType.CHECK, "maf");
        assertEquals(Map.of("pol", new NightResult.PoliceFinding("maf", true)), service.endNight().findings());

        service.beginNight(alive, roles, () -> { });
        submit("pol", ActionType.CHECK, "c1");
        assertEquals(Map.of("pol", new NightResult.PoliceFinding("c1", false)), service.endNight().findings());
    }

    @Test
    void secondSubmissionIsRejectedAndFirstChoiceStays() {
        assertTrue(submit("maf", ActionType.ATTACK, "c1").accepted());
        SubmitResult again = submit("maf", ActionType.ATTACK, "c2");
        assertFalse(again.accepted(), "한 번 확정: 같은 밤에 다시 제출 불가");
        assertTrue(again.reason().contains("이미"), again.reason());
        assertEquals(List.of("c1"), service.endNight().deaths());
    }

    @Test
    void invalidSubmissionsAreRejectedWithReason() {
        assertFalse(submit("c1", ActionType.ATTACK, "c2").accepted(), "시민은 밤 능력 없음");
        assertFalse(submit("doc", ActionType.ATTACK, "c2").accepted(), "직업에 없는 행동");
        assertFalse(submit("maf", ActionType.ATTACK, "maf").accepted(), "마피아는 마피아 팀 공격 불가");
        assertFalse(submit("pol", ActionType.CHECK, "pol").accepted(), "경찰은 자기 조사 불가");
        assertFalse(submit("maf", ActionType.ATTACK, "nobody").accepted(), "판에 없는 대상");
        assertTrue(submit("maf", ActionType.ATTACK, "c1").accepted(), "거절된 시도는 1회에 포함되지 않음");
    }

    @Test
    void deadActorsAndDeadTargetsAreRejected() {
        alive.remove("c1");
        alive.remove("doc");
        service.beginNight(alive, roles, () -> { });
        assertFalse(submit("maf", ActionType.ATTACK, "c1").accepted(), "죽은 대상");
        assertFalse(submit("doc", ActionType.HEAL, "c2").accepted(), "죽은 의사");
    }

    @Test
    void submissionsAfterNightClosedAreRejected() {
        service.endNight();
        assertFalse(submit("maf", ActionType.ATTACK, "c1").accepted());
    }

    @Test
    void completionFiresOnceWhenEveryAliveAbilityHolderSubmitted() {
        submit("maf", ActionType.ATTACK, "c1");
        submit("pol", ActionType.CHECK, "c2");
        assertEquals(0, completed.get(), "의사가 아직 안 냄 (시민은 세지 않음)");
        submit("doc", ActionType.HEAL, "c3");
        assertEquals(1, completed.get());
        submit("doc", ActionType.HEAL, "c1"); // 거절되는 재제출
        assertEquals(1, completed.get(), "완료 알림은 한 번만");
    }

    @Test
    void nightWithoutLivingAbilityHoldersCompletesImmediately() {
        AtomicInteger done = new AtomicInteger();
        service.beginNight(Set.of("c1", "c2"), roles, done::incrementAndGet);
        assertEquals(1, done.get());
    }

    @Test
    void leftPlayersActionsAndActionsAgainstThemAreDropped() {
        submit("maf", ActionType.ATTACK, "c1");
        submit("pol", ActionType.CHECK, "maf");
        service.onPlayerLeft("c1");   // 공격 대상이 나감
        service.onPlayerLeft("pol");  // 조사한 경찰이 나감
        NightResult result = service.endNight();
        assertEquals(List.of(), result.deaths());
        assertEquals(Map.of(), result.findings());
    }
}
