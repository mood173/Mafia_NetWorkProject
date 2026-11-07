package com.mafia.server.engine;

import com.mafia.common.protocol.Direction;
import com.mafia.common.protocol.MessageType;

import java.util.ArrayList;
import java.util.List;

/**
 * 시작 검사. 등록이 빠진 채로 서버가 뜨면 게임 도중에야 터지므로, 시작할 때 미리 확인한다.
 * 뼈대 단계에서는 report()로 남은 일 목록을 보고, 다 채워지면 ServerMain을 orFail()로 바꾼다.
 */
public final class WiringCheck {

    private WiringCheck() {
    }

    public static List<String> problems(MessageRouter router, GameConfig config) {
        List<String> problems = new ArrayList<>();
        for (MessageType type : MessageType.allUsedIn(Direction.CLIENT_TO_SERVER)) {
            if (!router.registered().contains(type)) {
                problems.add("핸들러 없음: " + type);
            }
        }
        if (config.totalRoles() != config.roomCapacity()) {
            problems.add("직업 수 합(" + config.totalRoles() + ") ≠ 방 정원(" + config.roomCapacity() + ")");
        }
        return problems;
    }

    public static void report(MessageRouter router, GameConfig config) {
        List<String> problems = problems(router, config);
        System.out.println("[WiringCheck] 남은 항목 " + problems.size() + "개");
        problems.forEach(p -> System.out.println("  - " + p));
    }

    public static void orFail(MessageRouter router, GameConfig config) {
        List<String> problems = problems(router, config);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("서버 조립 오류: " + problems);
        }
    }
}
