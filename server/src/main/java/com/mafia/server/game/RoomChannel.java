package com.mafia.server.game;
import com.mafia.common.protocol.Message;
import java.util.List;
/** 판이 방에 메시지를 내보내는 통로. 로비의 Room이 구현한다. 판은 소켓을 모르고 이것만 쓴다. */
public interface RoomChannel {
    List<String> players();
    void broadcast(Message message);
    void send(String playerName, Message message);
}
