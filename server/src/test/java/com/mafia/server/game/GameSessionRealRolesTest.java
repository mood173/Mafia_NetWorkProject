package com.mafia.server.game;

import com.mafia.common.model.Phase;
import com.mafia.common.model.RoleName;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.character.RoleAbility;
import com.mafia.server.character.night.DefaultCharacterService;
import com.mafia.server.character.role.RoleCatalog;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.GameSessionConfirmTest.FakeChannel;
import com.mafia.server.game.GameSessionConfirmTest.ManualTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 기본 서버가 쓰는 정식 직업·밤 처리(RoleCatalog + DefaultCharacterService)를 판과 엮어 본다. 12주차 "밤 한 번" 기준. */
final class GameSessionRealRolesTest {
    private final List<String> names = List.of("maf", "pol", "doc", "c1", "c2", "c3");
    private final FakeChannel channel = new FakeChannel(names);
    private final ManualTimer timer = new ManualTimer();
    private GameSession game;

    @BeforeEach
    void start() {
        Map<RoleName, RoleAbility> catalog = RoleCatalog.standard();
        Map<String, RoleAbility> roles = new LinkedHashMap<>();
        roles.put("maf", catalog.get(RoleName.MAFIA));
        roles.put("pol", catalog.get(RoleName.POLICE));
        roles.put("doc", catalog.get(RoleName.DOCTOR));
        for (String c : List.of("c1", "c2", "c3")) roles.put(c, catalog.get(RoleName.CITIZEN));
        game = new GameSession(channel, roles, new DefaultCharacterService(), timer,
                new PhaseTable(GameConfig.defaults()), new VoteCounter(), new WinChecker());
        game.start();
    }

    private long count(String name, MessageType type) {
        return channel.inbox.get(name).stream().filter(m -> m.type() == type).count();
    }

    @Test
    void secondNightActionIsRejectedWithErrorAndConfirmedOnlyOnce() {
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "c1"));
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "maf"));
        assertEquals(List.of("[SYSTEM]: c1에게 능력을 사용했습니다."), channel.system("pol"));
        Message error = channel.inbox.get("pol").stream().filter(m -> m.type() == MessageType.ERROR).findFirst().orElseThrow();
        assertTrue(error.field(0).contains("이미"), error.field(0));
    }

    @Test
    void sameAttackAndHealTargetMeansNoDeathAndOnlyPoliceGetsTheFinding() {
        game.onPlayerMessage("maf", Message.of(MessageType.MAFIA_ATTACK, "c1"));
        game.onPlayerMessage("doc", Message.of(MessageType.DOCTOR_HEAL, "c1"));
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "maf")); // 마지막 제출 → 밤 종료

        assertEquals(Message.of(MessageType.NIGHT_RESULT, ""),
                channel.inbox.get("c2").stream().filter(m -> m.type() == MessageType.NIGHT_RESULT).findFirst().orElseThrow());
        assertEquals(0, count("c1", MessageType.PLAYER_DIED));
        assertEquals(List.of(Message.of(MessageType.POLICE_RESULT, "maf", "true")),
                channel.inbox.get("pol").stream().filter(m -> m.type() == MessageType.POLICE_RESULT).toList());
        for (String other : List.of("maf", "doc", "c1", "c2", "c3"))
            assertEquals(0, count(other, MessageType.POLICE_RESULT), other + "는 조사 결과를 받지 않는다");
        assertEquals(Phase.DAY_RESULT, game.phase(), "전원 제출로 시간 전에 밤이 끝난다");
    }

    @Test
    void unprotectedTargetDiesWhenTheNightTimesOut() {
        game.onPlayerMessage("maf", Message.of(MessageType.MAFIA_ATTACK, "c1"));
        game.onPlayerMessage("doc", Message.of(MessageType.DOCTOR_HEAL, "c2"));
        timer.fire(); // 경찰이 안 내고 시간 만료
        assertEquals(List.of("c1", "ATTACK"),
                channel.inbox.get("c3").stream().filter(m -> m.type() == MessageType.PLAYER_DIED).findFirst().orElseThrow().fields());
        assertFalse(game.alive().contains("c1"));
    }
}
