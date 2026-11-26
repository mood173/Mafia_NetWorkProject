package com.mafia.server.game.event;

import java.util.List;

/** 사건 목록. 사건을 늘릴 때는 클래스를 만들고 여기에 한 줄을 더한다. */
public final class DayEvents {
    private DayEvents() {
    }

    /** 채팅 중계가 판에게 묻도록 연결된 서버에서 쓰는 전체 목록. */
    public static List<DayEvent> all() {
        return List.of(new StormEvent(), new MasqueradeEvent(), new OpenVoteEvent(), new WitnessEvent());
    }

    /** 채팅 중계를 판에 연결하지 않은 서버용. 가면무도회는 효과가 나지 않으므로 뺀다. */
    public static List<DayEvent> withoutChatEffects() {
        return List.of(new StormEvent(), new OpenVoteEvent(), new WitnessEvent());
    }
}
