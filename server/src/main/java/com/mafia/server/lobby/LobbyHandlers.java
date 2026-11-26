package com.mafia.server.lobby;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.common.protocol.Rejection;
import com.mafia.server.engine.MessageHandler;
import com.mafia.server.engine.MessageRouter;

import java.util.Objects;

/** 로비·대기방 메시지 6종과 방 채팅을 RoomService에 연결한다. 규칙 위반은 요청한 사람에게 ERROR로 돌려준다. */
public final class LobbyHandlers {
    private LobbyHandlers() {
    }

    public static void register(MessageRouter router, RoomService rooms) {
        router.register(MessageType.ROOM_LIST, guarded((from, m) -> rooms.roomList(from)));
        router.register(MessageType.CREATE_ROOM, guarded((from, m) -> rooms.create(from, m.field(0), m.field(1))));
        router.register(MessageType.JOIN_ROOM, guarded((from, m) -> rooms.join(from, m.field(0))));
        router.register(MessageType.JOIN_PRIVATE, guarded((from, m) -> rooms.joinPrivate(from, m.field(0))));
        router.register(MessageType.LEAVE_ROOM, guarded((from, m) -> rooms.leave(from)));
        router.register(MessageType.READY, guarded((from, m) -> rooms.ready(from, m.field(0))));
        router.register(MessageType.CHAT, guarded((from, m) -> rooms.chat(from, m.field(0))));
    }

    /** 처리되지 않은 예외는 수신 스레드를 끝내 연결을 닫으므로 여기서 ERROR 응답으로 바꾼다. */
    private static MessageHandler guarded(MessageHandler handler) {
        return (from, message) -> {
            try {
                handler.handle(from, message);
            } catch (IllegalArgumentException e) {
                from.send(Rejection.of(Objects.requireNonNullElse(e.getMessage(), "요청 오류"), message.type()).toMessage());
            }
        };
    }
}
