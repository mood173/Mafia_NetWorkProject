package com.mafia.common.protocol;

/** 메시지가 가는 방향. 같은 종류라도 방향마다 필드 개수가 다를 수 있다 (예: CHAT). */
public enum Direction {
    CLIENT_TO_SERVER,
    SERVER_TO_CLIENT
}
