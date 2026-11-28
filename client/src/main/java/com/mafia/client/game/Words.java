package com.mafia.client.game;

/**
 * 서버가 보내는 값(단계, 직업, 편, 사망 원인)을 화면에 보일 한글로 바꾼다.
 * 프로토콜의 값은 그대로 두고 표시만 바꾼다. 모르는 값은 받은 그대로 보인다.
 */
public final class Words {
    private Words() {
    }

    public static String phase(String value) {
        return switch (value) {
            case "WAITING" -> "대기";
            case "NIGHT" -> "밤";
            case "DAY_RESULT" -> "아침";
            case "DAY_TALK" -> "토론";
            case "VOTE" -> "투표";
            case "GAME_END" -> "게임 종료";
            default -> value;
        };
    }

    public static String role(String value) {
        return switch (value) {
            case "MAFIA" -> "마피아";
            case "POLICE" -> "경찰";
            case "DOCTOR" -> "의사";
            case "CITIZEN" -> "시민";
            case "" -> "배정 전";
            default -> value;
        };
    }

    /** 승리 편. 편 이름도 직업 이름과 같은 값(MAFIA, CITIZEN)을 쓴다. */
    public static String team(String value) {
        return role(value);
    }

    public static String cause(String value) {
        return switch (value) {
            case "ATTACK" -> "마피아의 공격";
            case "VOTE" -> "투표로 처형";
            case "DISCONNECT" -> "접속 끊김";
            default -> value;
        };
    }

    /**
     * 대기실에 보여 줄 사건 종류와 효과. 서버의 사건 목록(DayEvents)과 같은 내용이다.
     * 어떤 사건이 뜰지는 서버가 정하므로 화면은 안내만 한다.
     */
    public static final java.util.List<String> EVENT_GUIDE = java.util.List.of(
            "폭풍: 그날 토론 시간이 절반으로 줄어듭니다.",
            "가면무도회: 그날 채팅은 누가 말했는지 가려집니다.",
            "공개 투표: 그날 표가 들어올 때마다 누가 누구를 찍었는지 공개됩니다.",
            "목격자: 시민 한 명에게만 마피아가 아닌 사람 한 명을 알려 줍니다.");

    /** 서버 안내 앞의 "[SYSTEM]: "는 게임 기록 칸에서는 떼고 보인다. */
    public static String notice(String text) {
        return text.startsWith("[SYSTEM]: ") ? text.substring("[SYSTEM]: ".length()) : text;
    }
}
