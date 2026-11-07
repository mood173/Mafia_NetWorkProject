package com.mafia.common.protocol;

import java.util.EnumSet;
import java.util.Set;

/**
 * 제안서 10.2 메시지 목록.
 * 괄호 안 숫자 = 방향별 필드 개수. -1 은 그 방향으로는 보내지 않는다는 뜻.
 * 마지막 필드는 '|'가 들어가도 나누지 않는다 (채팅 내용 보호).
 * 필드를 바꿀 때는 두 사람이 같이 바꾸고 노션 설계 문서에 남긴다.
 */
public enum MessageType {
    //                C→S  S→C
    LOGIN           ( 1,  -1),   // 닉네임
    LOGIN_OK        (-1,   1),   // 닉네임
    ROOM_LIST       ( 0,   1),   // (요청) / "번호,이름,인원,진행여부;..."
    CREATE_ROOM     ( 2,  -1),   // 방 이름, PUBLIC 또는 PRIVATE
    ROOM_CODE       (-1,   1),   // 입장 코드
    JOIN_ROOM       ( 1,  -1),   // 방 번호
    JOIN_PRIVATE    ( 1,  -1),   // 입장 코드
    PLAYER_JOIN     (-1,   1),   // 닉네임
    PLAYER_LEAVE    (-1,   1),   // 닉네임
    READY           ( 1,  -1),   // true 또는 false
    GAME_START      (-1,   1),   // 참가자 "a,b,c,..."
    ROLE            (-1,   1),   // 직업 (개인 메시지)
    PHASE_CHANGE    (-1,   2),   // 단계, 제한 시간(초)
    CHAT            ( 1,   2),   // 내용 / 보낸 사람, 내용
    SYSTEM_CHAT     (-1,   1),   // 내용
    MAFIA_ATTACK    ( 1,  -1),   // 대상
    POLICE_CHECK    ( 1,  -1),   // 대상
    POLICE_RESULT   (-1,   2),   // 대상, true 또는 false (개인 메시지)
    DOCTOR_HEAL     ( 1,  -1),   // 대상
    NIGHT_RESULT    (-1,   1),   // 사망자 (없으면 빈 값)
    VOTE            ( 1,  -1),   // 대상 (기권이면 빈 값)
    VOTE_RESULT     (-1,   2),   // 처형자 (없으면 빈 값), 득표 "a:2,b:1"
    PLAYER_DIED     (-1,   2),   // 닉네임, 원인 ATTACK / VOTE / DISCONNECT
    GAME_STATE      (-1,   3),   // 단계, 생존자 "a,b,...", 남은 시간 (직업 정보 없음)
    GAME_END        (-1,   2),   // 승리 팀, 직업 공개 "a:MAFIA,b:CITIZEN,..."
    ERROR           (-1,   1);   // 실패 사유

    private final int toServerFields;
    private final int toClientFields;

    MessageType(int toServerFields, int toClientFields) {
        this.toServerFields = toServerFields;
        this.toClientFields = toClientFields;
    }

    /** 이 방향의 필드 개수. 보내지 않는 방향이면 -1. */
    public int fields(Direction direction) {
        return direction == Direction.CLIENT_TO_SERVER ? toServerFields : toClientFields;
    }

    public boolean usedIn(Direction direction) {
        return fields(direction) >= 0;
    }

    /** 이 방향으로 오가는 메시지 종류 전부. 시작 검사(WiringCheck)에서 쓴다. */
    public static Set<MessageType> allUsedIn(Direction direction) {
        Set<MessageType> result = EnumSet.noneOf(MessageType.class);
        for (MessageType type : values()) {
            if (type.usedIn(direction)) result.add(type);
        }
        return result;
    }

    /** 판 진행으로 넘기는 게임 커맨드. */
    public static Set<MessageType> gameCommands() {
        return EnumSet.of(MAFIA_ATTACK, POLICE_CHECK, DOCTOR_HEAL, VOTE);
    }
}
