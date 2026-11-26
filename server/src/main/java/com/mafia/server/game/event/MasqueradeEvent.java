package com.mafia.server.game.event;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

/** 가면무도회: 그날 채팅은 보낸 사람 이름이 가려지고 말끝에 정해진 말이 붙어서 전달된다. */
public final class MasqueradeEvent implements DayEvent {
    public static final String MASKED_NAME = "???";
    public static final String TAIL = " ...라고 가면이 말했다";

    @Override public String code() { return "MASQUERADE"; }

    @Override public String description() {
        return "가면무도회: 오늘은 누가 말했는지 알 수 없습니다.";
    }

    @Override public Message chat(String sender, String text) {
        return Message.of(MessageType.CHAT, MASKED_NAME, text + TAIL);
    }
}
