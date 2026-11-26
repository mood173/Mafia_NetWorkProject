package com.mafia.common.protocol;

/** 서버가 요청을 거절한 사유의 분류. 화면은 사유 글자가 아니라 이 값으로 반응을 정한다. */
public enum RejectCode {
    /** 이미 받아들여진 행동을 또 보냈다. 화면은 잠금을 풀지 않는다. */
    ALREADY_DONE,
    /** 대상이 규칙에 맞지 않는다. 화면은 다시 고르게 한다. */
    INVALID_TARGET,
    /** 지금 단계나 상태에서는 할 수 없다. */
    NOT_ALLOWED_NOW,
    /** 분류하지 않은 거절. */
    UNKNOWN
}
