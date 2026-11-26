package com.mafia.server.game.event;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import java.util.List;

/**
 * 하루 동안 규칙 하나를 바꾸는 사건. 아침에 작동하고 밤에 들어갈 때 풀린다.
 * 사건은 자기가 끼어드는 자리의 메서드만 고쳐 쓴다. 나머지는 평소 값이 그대로 나간다.
 * 메시지를 직접 보내지 않고 돌려주기만 한다. 보내는 것은 GameSession이 한다.
 */
public interface DayEvent {
    /** 프로토콜에 실리는 코드. 봇은 이 값을 보고 행동을 정한다. */
    String code();

    /** 화면에 보일 설명. "이름: 내용" 꼴로 쓴다. */
    String description();

    /** 사건이 작동하는 순간. 특정 사람에게만 보낼 안내가 있으면 돌려준다. */
    default List<PrivateNote> onStart(DayContext day) {
        return List.of();
    }

    /** 이 단계의 제한 시간(초). */
    default int seconds(Phase phase, int base) {
        return base;
    }

    /** 게임 중 채팅 한 줄을 방에 어떤 모양으로 전달할지. */
    default Message chat(String sender, String text) {
        return Message.of(MessageType.CHAT, sender, text);
    }

    /** 표가 받아들여진 직후. 방 전체에 알릴 것이 있으면 돌려준다. */
    default List<Message> onVote(String voter, String target) {
        return List.of();
    }
}
