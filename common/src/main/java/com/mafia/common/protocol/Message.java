package com.mafia.common.protocol;

import java.util.List;

/** 메시지 하나. 종류와 필드 목록. 만든 뒤에는 바뀌지 않는다. */
public record Message(MessageType type, List<String> fields) {

    public Message {
        fields = List.copyOf(fields);
    }

    public static Message of(MessageType type, String... fields) {
        return new Message(type, List.of(fields));
    }

    /** i번째 필드. 없으면 빈 문자열. */
    public String field(int i) {
        return i < fields.size() ? fields.get(i) : "";
    }
}
