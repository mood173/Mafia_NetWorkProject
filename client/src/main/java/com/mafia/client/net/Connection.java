package com.mafia.client.net;

import com.mafia.common.protocol.Message;

import java.io.IOException;

/**
 * 서버 연결 계약. 소켓 구현 SocketConnection 은 고경탁 담당.
 * 씬은 이 인터페이스만 보고, 소켓과 스레드는 모른다.
 */
public interface Connection {

    void connect(String host, int port) throws IOException;

    void send(Message message);

    /** 수신 스레드에서 불러도 된다. GameStream이 화면 스레드(EDT)로 옮긴다. */
    void setListener(MessageListener listener);

    void close();
}
