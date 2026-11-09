package com.mafia.server.game;
import com.mafia.common.protocol.Message;
import java.util.List;
/** 경탁의 Room 어댑터가 구현할 계약. 송신은 비차단 큐 방식 권장. */
public interface RoomChannel {
    List<String> players();
    void broadcast(Message message);
    void send(String playerName, Message message);
}
