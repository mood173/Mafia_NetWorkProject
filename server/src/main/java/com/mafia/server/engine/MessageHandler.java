package com.mafia.server.engine;

import com.mafia.common.protocol.Message;
import com.mafia.server.net.Session;

/** 메시지 종류 하나를 처리한다. MessageRouter에 등록해서 쓴다. */
@FunctionalInterface
public interface MessageHandler {
    void handle(Session from, Message message);
}
