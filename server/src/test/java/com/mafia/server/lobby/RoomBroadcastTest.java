package com.mafia.server.lobby;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Room은 RoomChannel(참가자 목록과 송신)만 맡는다. 단계 내용을 보고 메시지를 덧붙이지 않는다. */
final class RoomBroadcastTest {

    @Test
    void broadcastDeliversExactlyWhatGameSessionSent() {
        Room room = new RoomRegistry(new Random(1)).create("테스트방", false);
        RoomServiceTest.FakeSession a = new RoomServiceTest.FakeSession("a");
        RoomServiceTest.FakeSession b = new RoomServiceTest.FakeSession("b");
        room.add(a);
        room.add(b);

        for (String phase : List.of("NIGHT", "DAY_RESULT", "DAY_TALK", "VOTE", "GAME_END")) {
            room.broadcast(Message.of(MessageType.PHASE_CHANGE, phase, "10"));
        }
        assertEquals(5, a.inbox.size(), "단계 안내는 GameSession 몫이라 방은 덧붙이지 않는다");
        assertEquals(a.inbox, b.inbox);
    }
}
