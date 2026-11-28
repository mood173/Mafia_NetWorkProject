package com.mafia.server.game;
import com.mafia.common.protocol.Message;
/**
 * 판 바깥(로비, 연결 처리)에서 판에 넣는 입력 세 가지: 시작, 플레이어의 요청, 접속 이탈.
 * 바깥은 이 인터페이스만 알고, 판 안의 단계나 규칙은 모른다.
 */
public interface GameEventHandler {
    void start();
    void onPlayerMessage(String playerName, Message message);
    void onPlayerLeft(String playerName);
}
