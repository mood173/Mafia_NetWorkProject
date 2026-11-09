package com.mafia.common.protocol;

import java.util.List;

/**
 * Message ↔ 한 줄 문자열. 형식은 "종류|필드1|필드2|...".
 * 서버는 decode(line, CLIENT_TO_SERVER), 클라이언트는 decode(line, SERVER_TO_CLIENT)로 읽는다.
 */
public final class MessageCodec {

    public String encode(Message message) {
        StringBuilder line = new StringBuilder(message.type().name());
        List<String> fields = message.fields();
        for (int i = 0; i < fields.size(); i++) {
            String field = fields.get(i).replace('\r', ' ').replace('\n', ' ');
            if (i < fields.size() - 1 && field.contains("|")) {
                throw new IllegalArgumentException("마지막이 아닌 필드에 '|'가 있음: " + field);
            }
            line.append('|').append(field);
        }
        return line.toString();
    }

    /** 형식이 틀리면 IllegalArgumentException. 받는 쪽은 잡아서 ERROR로 답한다. */
    public Message decode(String line, Direction direction) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("빈 메시지");
        }
        String[] head = line.split("\\|", 2);
        MessageType type = parseType(head[0].trim());
        int count = type.fields(direction);
        if (count < 0) {
            throw new IllegalArgumentException(type + "은(는) " + direction + " 방향으로 보낼 수 없음");
        }
        if (count == 0 || head.length == 1) {
            return new Message(type, List.of());
        }
        return new Message(type, List.of(head[1].split("\\|", count)));
    }

    private static MessageType parseType(String name) {
        try {
            return MessageType.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("모르는 메시지 종류: " + name);
        }
    }
}
