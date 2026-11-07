package com.mafia.server.net;

import com.mafia.common.protocol.Message;

/**
 * 접속한 사용자 하나. 구현은 ClientHandler (고경탁).
 * 핸들러는 이 인터페이스만 보고, 소켓은 모른다.
 */
public interface Session {

    /** 닉네임. LOGIN 전이면 null. */
    String name();

    /** 이 사용자에게만 보낸다. 여러 스레드에서 불려도 안전해야 한다. */
    void send(Message message);
}
