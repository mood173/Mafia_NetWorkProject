package com.mafia.server;

import com.mafia.server.engine.GameConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 서버 실행 인자: [주소] [포트] [--demo].
 * 주소 기본값 0.0.0.0(모든 네트워크 카드, 다른 PC 접속용), 포트 기본값은 GameConfig.
 * --demo는 시연·리허설용으로 단계 시간만 줄인다. 정원과 직업 구성은 그대로다.
 */
public record ServerArgs(String host, int port, GameConfig config, boolean demo) {
    public static final int DEMO_NIGHT = 15;
    public static final int DEMO_DAY_RESULT = 3;
    public static final int DEMO_TALK = 20;
    public static final int DEMO_VOTE = 15;
    static final String USAGE = "사용법: ServerMain [주소] [포트] [--demo]  (예: 0.0.0.0 5000 --demo)";

    public static ServerArgs parse(String[] args) {
        List<String> positional = new ArrayList<>();
        boolean demo = false;
        for (String arg : args) {
            if (arg.equals("--demo")) demo = true;
            else if (arg.startsWith("--")) throw new IllegalArgumentException("알 수 없는 옵션 " + arg + ". " + USAGE);
            else positional.add(arg);
        }
        if (positional.size() > 2) throw new IllegalArgumentException("인자가 너무 많습니다. " + USAGE);

        GameConfig base = GameConfig.defaults();
        String host = positional.isEmpty() ? "0.0.0.0" : positional.get(0);
        int port = positional.size() > 1 ? port(positional.get(1)) : base.port();
        GameConfig config = demo
                ? new GameConfig(base.port(), base.roomCapacity(), DEMO_NIGHT, DEMO_DAY_RESULT, DEMO_TALK, DEMO_VOTE, base.roleCounts())
                : base;
        return new ServerArgs(host, port, config, demo);
    }

    private static int port(String text) {
        try {
            int port = Integer.parseInt(text);
            if (port >= 0 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
            // 아래에서 같은 안내로 알린다.
        }
        throw new IllegalArgumentException("포트는 0~65535 숫자입니다: " + text + ". " + USAGE);
    }
}
