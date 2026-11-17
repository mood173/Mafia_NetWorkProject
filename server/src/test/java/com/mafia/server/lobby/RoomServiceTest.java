package com.mafia.server.lobby;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.demo.DemoCharacterService;
import com.mafia.server.demo.DemoRole;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.engine.MessageRouter;
import com.mafia.server.game.GameSessionFactory;
import com.mafia.server.game.PhaseTimer;
import com.mafia.server.net.Session;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

final class RoomServiceTest {

    /** 받은 메시지를 기록하는 테스트용 접속. */
    static final class FakeSession implements Session {
        final String name;
        final List<Message> inbox = new ArrayList<>();
        FakeSession(String name) { this.name = name; }
        public String name() { return name; }
        public synchronized void send(Message message) { inbox.add(message); }
        synchronized List<Message> all(MessageType type) {
            return inbox.stream().filter(m -> m.type() == type).toList();
        }
        synchronized Message last(MessageType type) {
            List<Message> found = all(type);
            assertFalse(found.isEmpty(), name + "에게 " + type + " 없음");
            return found.get(found.size() - 1);
        }
        synchronized void clear() { inbox.clear(); }
    }

    /** 단계가 자동으로 넘어가지 않게 하는 타이머. 게임 시작 직후 상태만 검사한다. */
    static final PhaseTimer FROZEN = new PhaseTimer() {
        public Ticket schedule(int seconds, Runnable callback) { return () -> { }; }
        public long nowNanos() { return 0; }
    };

    private final GameConfig config = GameConfig.defaults();
    private final RoomService rooms = new RoomService(config,
            new GameSessionFactory(config, DemoRole.catalog(), DemoCharacterService::new, FROZEN, new Random(1)),
            new Random(1));

    private List<FakeSession> fullPublicRoom() {
        List<FakeSession> players = new ArrayList<>();
        for (int i = 0; i < 6; i++) players.add(new FakeSession("p" + i));
        rooms.create(players.get(0), "연습방", "PUBLIC");
        for (int i = 1; i < 6; i++) rooms.join(players.get(i), "1");
        return players;
    }

    @Test
    void creatorEntersPublicRoomAsHostAndRoomIsListed() {
        FakeSession a = new FakeSession("a");
        rooms.create(a, "연습방", "PUBLIC");

        assertEquals(List.of("1", "연습방", "PUBLIC", "a", "a:false"), a.last(MessageType.ROOM_STATE).fields());
        assertTrue(a.all(MessageType.ROOM_CODE).isEmpty(), "공개방은 코드를 보내지 않는다");

        FakeSession viewer = new FakeSession("v");
        rooms.roomList(viewer);
        assertEquals("1,연습방,1,false", viewer.last(MessageType.ROOM_LIST).field(0));
    }

    @Test
    void invalidTitleOrKindIsRejected() {
        FakeSession a = new FakeSession("a");
        assertThrows(IllegalArgumentException.class, () -> rooms.create(a, " ", "PUBLIC"));
        assertThrows(IllegalArgumentException.class, () -> rooms.create(a, "a,b", "PUBLIC"));
        assertThrows(IllegalArgumentException.class, () -> rooms.create(a, "a;b", "PUBLIC"));
        assertThrows(IllegalArgumentException.class, () -> rooms.create(a, "연습방", "SECRET"));
        rooms.create(a, "연습방", "PUBLIC");
        assertThrows(IllegalArgumentException.class, () -> rooms.create(a, "두번째", "PUBLIC"), "이미 방에 있음");
    }

    @Test
    void privateRoomIsHiddenAndJoinedOnlyByCode() {
        FakeSession a = new FakeSession("a"), b = new FakeSession("b");
        rooms.create(a, "비밀방", "PRIVATE");
        String code = a.last(MessageType.ROOM_CODE).field(0);
        assertTrue(code.matches("[A-Z0-9]{6}"), code);

        rooms.roomList(b);
        assertEquals("", b.last(MessageType.ROOM_LIST).field(0));
        assertThrows(IllegalArgumentException.class, () -> rooms.join(b, "1"));
        assertThrows(IllegalArgumentException.class, () -> rooms.joinPrivate(b, "WRONG1"));

        rooms.joinPrivate(b, code.toLowerCase());
        assertEquals("a:false,b:false", b.last(MessageType.ROOM_STATE).field(4));
        assertTrue(b.all(MessageType.ROOM_CODE).isEmpty(), "코드는 방장에게만 보낸다");
    }

    @Test
    void seventhPlayerIsRejected() {
        fullPublicRoom();
        assertThrows(IllegalArgumentException.class, () -> rooms.join(new FakeSession("p6"), "1"));
    }

    @Test
    void readyChangeIsBroadcastToEveryMember() {
        FakeSession a = new FakeSession("a"), b = new FakeSession("b");
        rooms.create(a, "연습방", "PUBLIC");
        rooms.join(b, "1");

        rooms.ready(a, "true");
        assertEquals("a:true,b:false", a.last(MessageType.ROOM_STATE).field(4));
        assertEquals("a:true,b:false", b.last(MessageType.ROOM_STATE).field(4));

        rooms.ready(a, "false");
        assertEquals("a:false,b:false", b.last(MessageType.ROOM_STATE).field(4));
        assertThrows(IllegalArgumentException.class, () -> rooms.ready(a, "yes"));
        assertThrows(IllegalArgumentException.class, () -> rooms.ready(new FakeSession("x"), "true"), "방 밖");
    }

    @Test
    void allSixReadyStartsGameAndSendsEachRoleOnlyToItsOwner() {
        List<FakeSession> players = fullPublicRoom();
        for (int i = 0; i < 5; i++) rooms.ready(players.get(i), "true");
        assertTrue(players.get(0).all(MessageType.GAME_START).isEmpty(), "5명 준비로는 시작하지 않는다");

        rooms.ready(players.get(5), "true");
        for (FakeSession p : players) {
            assertEquals("p0,p1,p2,p3,p4,p5", p.last(MessageType.GAME_START).field(0));
            assertEquals(1, p.all(MessageType.ROLE).size(), p.name + "는 자기 직업 하나만 받는다");
        }
        assertThrows(IllegalArgumentException.class, () -> rooms.join(new FakeSession("late"), "1"), "진행 중 입장 불가");
        assertThrows(IllegalArgumentException.class, () -> rooms.ready(players.get(0), "false"), "시작 후 준비 변경 불가");

        FakeSession viewer = new FakeSession("v");
        rooms.roomList(viewer);
        assertEquals("1,연습방,6,true", viewer.last(MessageType.ROOM_LIST).field(0));
        assertNotNull(rooms.gameOf("p0"));
    }

    @Test
    void hostLeavingHandsOverHostAndEmptyRoomIsRemoved() {
        FakeSession a = new FakeSession("a"), b = new FakeSession("b");
        rooms.create(a, "연습방", "PUBLIC");
        rooms.join(b, "1");

        rooms.leave(a);
        assertEquals("1", a.last(MessageType.ROOM_LEFT).field(0));
        assertEquals("a", b.last(MessageType.PLAYER_LEAVE).field(0));
        assertEquals("b", b.last(MessageType.ROOM_STATE).field(3), "다음 입장자가 방장");
        assertThrows(IllegalArgumentException.class, () -> rooms.leave(a), "이미 나감");

        rooms.leave(b);
        FakeSession viewer = new FakeSession("v");
        rooms.roomList(viewer);
        assertEquals("", viewer.last(MessageType.ROOM_LIST).field(0));
    }

    @Test
    void disconnectDuringGameMarksPlayerDead() {
        List<FakeSession> players = fullPublicRoom();
        players.forEach(p -> rooms.ready(p, "true"));
        players.get(1).clear();

        rooms.disconnected(players.get(0));
        assertEquals(List.of("p0", "DISCONNECT"), players.get(1).last(MessageType.PLAYER_DIED).fields());
        assertNull(rooms.gameOf("p0"));
    }

    @Test
    void routerHandlersTurnRuleViolationsIntoErrorReplies() {
        MessageRouter router = new MessageRouter();
        LobbyHandlers.register(router, rooms);
        FakeSession a = new FakeSession("a");

        router.dispatch(a, Message.of(MessageType.JOIN_ROOM, "abc"));
        router.dispatch(a, Message.of(MessageType.READY, "true"));
        assertEquals(2, a.all(MessageType.ERROR).size());

        router.dispatch(a, Message.of(MessageType.CREATE_ROOM, "연습방", "PUBLIC"));
        assertEquals("a", a.last(MessageType.ROOM_STATE).field(3));
    }
}
