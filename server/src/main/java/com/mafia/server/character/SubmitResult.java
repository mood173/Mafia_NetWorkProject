package com.mafia.server.character;
import com.mafia.common.protocol.RejectCode;

/** 밤 능력 제출의 결과. 거절이면 사유 글자와 분류(code)를 담는다. */
public record SubmitResult(boolean accepted, String reason, RejectCode code) {
    /** 분류를 정하지 않은 결과. 거절이면 UNKNOWN으로 나간다. */
    public SubmitResult(boolean accepted, String reason) {
        this(accepted, reason, RejectCode.UNKNOWN);
    }
}
