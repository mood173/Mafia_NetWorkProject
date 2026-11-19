package com.mafia.server.game;

import com.mafia.common.model.RoleName;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.character.RoleAbility;
import com.mafia.server.demo.DemoCharacterService;
import com.mafia.server.demo.DemoRole;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.GameSessionConfirmTest.FakeChannel;
import com.mafia.server.game.GameSessionConfirmTest.ManualTimer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 밤 시작과 아침 안내는 단계를 바꾸는 GameSession.transitionTo가 PHASE_CHANGE·GAME_STATE 뒤에 방송한다. */
final class GameSessionAnnouncementTest {
    private static final Message NIGHT = Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 밤이 찾아왔습니다.");
    private static final Message MORNING = Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 아침이 밝았습니다.");

    private final List<String> names = List.of("maf", "pol", "doc", "c1", "c2", "c3");
    private final FakeChannel channel = new FakeChannel(names);
    private final ManualTimer timer = new ManualTimer();
    private GameSession game;

    @BeforeEach
    void start() {
        Map<RoleName, RoleAbility> catalog = DemoRole.catalog();
        Map<String, RoleAbility> roles = new LinkedHashMap<>();
        roles.put("maf", catalog.get(RoleName.MAFIA));
        roles.put("pol", catalog.get(RoleName.POLICE));
        roles.put("doc", catalog.get(RoleName.DOCTOR));
        for (String c : List.of("c1", "c2", "c3")) roles.put(c, catalog.get(RoleName.CITIZEN));
        game = new GameSession(channel, roles, new DemoCharacterService(), timer,
                new PhaseTable(GameConfig.defaults()), new VoteCounter(), new WinChecker());
        game.start();
    }

    /** c1이 받은 메시지 중 마지막 n개의 종류와, 안내가 있다면 그 문구. */
    private List<Message> tail(int n) {
        List<Message> all = channel.inbox.get("c1");
        return all.subList(all.size() - n, all.size());
    }

    private void finishNight() {
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "c1"));
        game.onPlayerMessage("doc", Message.of(MessageType.DOCTOR_HEAL, "c1"));
        game.onPlayerMessage("maf", Message.of(MessageType.MAFIA_ATTACK, "c1")); // 보호되어 사망 없음
    }

    @Test
    void firstNightIsAnnouncedAfterPhaseChangeAndGameState() {
        List<Message> last = tail(3);
        assertEquals(List.of("NIGHT", "NIGHT"), List.of(last.get(0).field(0), last.get(1).field(0)));
        assertEquals(MessageType.PHASE_CHANGE, last.get(0).type());
        assertEquals(MessageType.GAME_STATE, last.get(1).type());
        assertEquals(NIGHT, last.get(2));
    }

    @Test
    void morningIsAnnouncedWhenNightTurnsToDay() {
        finishNight();
        List<Message> last = tail(3);
        assertEquals(MessageType.PHASE_CHANGE, last.get(0).type());
        assertEquals("DAY_RESULT", last.get(0).field(0));
        assertEquals(MessageType.GAME_STATE, last.get(1).type());
        assertEquals(MORNING, last.get(2));
    }

    @Test
    void nightIsAnnouncedAgainAfterVoteAndNotForTalkOrVote() {
        finishNight();
        timer.fire(); // DAY_RESULT → DAY_TALK
        timer.fire(); // DAY_TALK → VOTE
        for (String voter : names) game.onPlayerMessage(voter, Message.of(MessageType.VOTE, "")); // 전원 기권 → 다음 밤

        List<Message> received = channel.inbox.get("c1");
        assertEquals(2, received.stream().filter(NIGHT::equals).count(), "첫 밤 + 투표 뒤 밤");
        assertEquals(1, received.stream().filter(MORNING::equals).count(), "아침 한 번");
        assertEquals(NIGHT, received.get(received.size() - 1));
    }
}
