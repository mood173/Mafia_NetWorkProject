package com.mafia.server.lobby;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 단계가 바뀔 때 방 전체에 보내는 시스템 안내. */
final class RoomAnnouncementTest {

    private static List<Message> afterBroadcast(String phase) {
        Room room = new Room(1, "테스트방", false, "");
        RoomServiceTest.FakeSession a = new RoomServiceTest.FakeSession("a");
        room.add(a);
        room.broadcast(Message.of(MessageType.PHASE_CHANGE, phase, "10"));
        return a.inbox;
    }

    @Test
    void nightStartIsAnnouncedAfterPhaseChange() {
        assertEquals(List.of(Message.of(MessageType.PHASE_CHANGE, "NIGHT", "10"),
                Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 밤이 찾아왔습니다.")), afterBroadcast("NIGHT"));
    }

    @Test
    void morningIsAnnouncedWhenNightTurnsToDay() {
        assertEquals(Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 아침이 밝았습니다."), afterBroadcast("DAY_RESULT").get(1));
    }

    @Test
    void otherPhasesAndMessagesAreNotAnnounced() {
        for (String phase : List.of("DAY_TALK", "VOTE", "GAME_END"))
            assertEquals(1, afterBroadcast(phase).size(), phase);
    }
}
