package com.mafia.server.engine;

import com.mafia.common.protocol.Direction;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.net.Session;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 등록부: 메시지 종류 → 핸들러. switch 문을 여러 곳에 두지 않기 위해 한 곳에 모은다.
 * 등록은 ServerMain에서 시작할 때만 하고, 이후에는 읽기만 한다.
 */
public final class MessageRouter {

    private final Map<MessageType, MessageHandler> handlers = new EnumMap<>(MessageType.class);

    public void register(MessageType type, MessageHandler handler) {
        if (!type.usedIn(Direction.CLIENT_TO_SERVER)) {
            throw new IllegalArgumentException(type + "은(는) 클라이언트가 보내는 메시지가 아님");
        }
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException(type + " 핸들러가 이미 등록됨");
        }
    }

    public void dispatch(Session from, Message message) {
        MessageHandler handler = handlers.get(message.type());
        if (handler == null) {
            from.send(Message.of(MessageType.ERROR, "아직 처리할 수 없는 메시지: " + message.type()));
            return;
        }
        handler.handle(from, message);
    }

    public Set<MessageType> registered() {
        return Collections.unmodifiableSet(handlers.keySet());
    }
}
