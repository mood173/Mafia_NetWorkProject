package com.mafia.common.protocol;

/**
 * 거절 응답 한 건. ERROR 메시지의 칸 순서(사유 글자, 거절된 요청 종류, 사유 코드)는 이 클래스만 안다.
 * 서버는 toMessage()로 만들고 화면은 from()으로 읽는다. ERROR를 직접 만들거나 칸 번호로 읽지 않는다.
 */
public record Rejection(String reason, String request, RejectCode code) {

    public Rejection {
        reason = reason == null ? "" : reason;
        request = request == null ? "" : request;
        code = code == null ? RejectCode.UNKNOWN : code;
    }

    /** 특정 요청과 무관한 거절 (연결 오류, 형식 오류 등). */
    public static Rejection of(String reason) {
        return new Rejection(reason, "", RejectCode.UNKNOWN);
    }

    public static Rejection of(String reason, MessageType request) {
        return new Rejection(reason, request.name(), RejectCode.UNKNOWN);
    }

    public static Rejection of(String reason, MessageType request, RejectCode code) {
        return new Rejection(reason, request.name(), code);
    }

    public Message toMessage() {
        return Message.of(MessageType.ERROR, reason, request, code.name());
    }

    /** 뒤의 두 칸이 없거나 모르는 코드면 요청 종류는 빈 값, 코드는 UNKNOWN으로 읽는다. */
    public static Rejection from(Message message) {
        if (message.type() != MessageType.ERROR) {
            throw new IllegalArgumentException("ERROR 메시지가 아님: " + message.type());
        }
        RejectCode code = RejectCode.UNKNOWN;
        for (RejectCode candidate : RejectCode.values()) {
            if (candidate.name().equals(message.field(2))) code = candidate;
        }
        return new Rejection(message.field(0), message.field(1), code);
    }

    /** 거절된 요청이 이 종류들 가운데 하나인가. */
    public boolean isAbout(MessageType... types) {
        for (MessageType type : types) {
            if (type.name().equals(request)) return true;
        }
        return false;
    }
}
