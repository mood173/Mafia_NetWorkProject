package com.mafia.client.net;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import javax.swing.SwingUtilities;

/**
 * 서버 없이 화면 흐름을 시험하는 가짜 연결. LOGIN 에는 LOGIN_OK 로 답한다.
 * 진짜 SocketConnection 이 생기면 ClientMain 에서 한 줄만 바꾼다.
 */
public final class OfflineConnection implements Connection {

    private MessageListener listener;

    @Override
    public void connect(String host, int port) {
        System.out.println("[Offline] connect " + host + ":" + port + " (실제 연결 없음)");
    }

    @Override
    public void send(Message message) {
        System.out.println("[Offline] send " + message);
        Message reply = switch (message.type()) {
            case LOGIN -> Message.of(MessageType.LOGIN_OK, message.field(0));
            case ROOM_LIST -> Message.of(MessageType.ROOM_LIST, "");
            default -> Message.of(MessageType.SYSTEM_CHAT, "오프라인: " + message.type() + " 처리 안 함");
        };
        SwingUtilities.invokeLater(() -> listener.onMessage(reply));
    }

    @Override
    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    @Override
    public void close() {
    }
}
