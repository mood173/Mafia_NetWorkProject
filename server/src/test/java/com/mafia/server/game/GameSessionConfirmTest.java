package com.mafia.server.game;

import com.mafia.common.model.RoleName;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.character.RoleAbility;
import com.mafia.server.demo.DemoCharacterService;
import com.mafia.server.demo.DemoRole;
import com.mafia.server.engine.GameConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 밤 능력·투표가 받아들여지면 그 사람에게만 [SYSTEM] 안내를 보낸다. */
final class GameSessionConfirmTest {

    /** 참가자별 받은 메시지를 기록하는 방 채널. */
    static final class FakeChannel implements RoomChannel {
        final Map<String, List<Message>> inbox = new LinkedHashMap<>();
        FakeChannel(List<String> names) { names.forEach(n -> inbox.put(n, new ArrayList<>())); }
        public List<String> players() { return List.copyOf(inbox.keySet()); }
        public void broadcast(Message m) { inbox.values().forEach(list -> list.add(m)); }
        public void send(String name, Message m) { inbox.get(name).add(m); }
        /** 개인 [SYSTEM] 안내만. 방 전체 단계 안내(밤·아침)는 GameSessionAnnouncementTest가 따로 본다. */
        List<String> system(String name) {
            Set<String> phaseNotices = Set.of("[SYSTEM]: 밤이 찾아왔습니다.", "[SYSTEM]: 아침이 밝았습니다.");
            return inbox.get(name).stream().filter(m -> m.type() == MessageType.SYSTEM_CHAT)
                    .map(m -> m.field(0)).filter(text -> !phaseNotices.contains(text)).toList();
        }
        int indexOf(String name, MessageType type) {
            List<Message> list = inbox.get(name);
            for (int i = 0; i < list.size(); i++) if (list.get(i).type() == type) return i;
            return -1;
        }
        int indexOfSystem(String name, String text) {
            return inbox.get(name).indexOf(Message.of(MessageType.SYSTEM_CHAT, text));
        }
    }

    /** 예약된 단계 마감을 손으로 실행하는 타이머. */
    static final class ManualTimer implements PhaseTimer {
        private final List<Runnable> pending = new ArrayList<>();
        public Ticket schedule(int seconds, Runnable callback) {
            pending.add(callback);
            return () -> pending.remove(callback);
        }
        public long nowNanos() { return 0; }
        void fire() { new ArrayList<>(pending).forEach(Runnable::run); }
    }

    // mafia 1명, police 1명, doctor 1명, 시민 3명으로 고정 배정
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

    @Test
    void acceptedNightActionIsConfirmedOnlyToTheActor() {
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "c1"));
        assertEquals(List.of("[SYSTEM]: c1에게 능력을 사용했습니다."), channel.system("pol"));
        assertTrue(channel.system("c1").isEmpty(), "다른 사람에게는 보내지 않는다");
    }

    @Test
    void rejectedNightActionGetsNoConfirmation() {
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "pol")); // 경찰은 자신을 조사할 수 없다
        assertTrue(channel.system("pol").isEmpty());
        assertEquals(1, channel.inbox.get("pol").stream().filter(m -> m.type() == MessageType.ERROR).count());
    }

    @Test
    void lastNightActionIsConfirmedBeforeTheNightResult() {
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "c1"));
        game.onPlayerMessage("doc", Message.of(MessageType.DOCTOR_HEAL, "c1"));
        game.onPlayerMessage("maf", Message.of(MessageType.MAFIA_ATTACK, "c2")); // 마지막 제출 → 즉시 밤 마감

        int confirm = channel.indexOfSystem("maf", "[SYSTEM]: c2에게 능력을 사용했습니다.");
        int result = channel.indexOf("maf", MessageType.NIGHT_RESULT);
        assertTrue(confirm >= 0 && result >= 0 && confirm < result, "안내가 밤 결과보다 먼저");
    }

    @Test
    void votesAndAbstentionsAreConfirmedBeforeTheResult() {
        game.onPlayerMessage("pol", Message.of(MessageType.POLICE_CHECK, "c1"));
        game.onPlayerMessage("doc", Message.of(MessageType.DOCTOR_HEAL, "c1"));
        game.onPlayerMessage("maf", Message.of(MessageType.MAFIA_ATTACK, "c1")); // 보호되어 사망 없음
        timer.fire(); // DAY_RESULT → DAY_TALK
        timer.fire(); // DAY_TALK → VOTE

        game.onPlayerMessage("c1", Message.of(MessageType.VOTE, "c2"));
        assertEquals(List.of("[SYSTEM]: c2님을 투표하셨습니다."), channel.system("c1").stream()
                .filter(s -> s.contains("투표")).toList());
        game.onPlayerMessage("c2", Message.of(MessageType.VOTE, ""));
        assertTrue(channel.system("c2").contains("[SYSTEM]: 기권하셨습니다."));

        for (String voter : List.of("c3", "maf", "pol"))
            game.onPlayerMessage(voter, Message.of(MessageType.VOTE, "c3".equals(voter) ? "maf" : "c3"));
        game.onPlayerMessage("doc", Message.of(MessageType.VOTE, "c3")); // 마지막 투표 → 즉시 집계
        int confirm = channel.indexOfSystem("doc", "[SYSTEM]: c3님을 투표하셨습니다.");
        int result = channel.indexOf("doc", MessageType.VOTE_RESULT);
        assertTrue(confirm >= 0 && result >= 0 && confirm < result, "안내가 투표 결과보다 먼저");
    }
}
