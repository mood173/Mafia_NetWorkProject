package com.mafia.server.game.event;

import com.mafia.common.model.Phase;

/** 폭풍: 그날 토론 시간이 절반이 된다. 투표 시간은 그대로다. */
public final class StormEvent implements DayEvent {
    @Override public String code() { return "STORM"; }

    @Override public String description() {
        return "폭풍: 오늘 토론 시간이 절반으로 줄어듭니다.";
    }

    @Override public int seconds(Phase phase, int base) {
        return phase == Phase.DAY_TALK ? Math.max(1, base / 2) : base;
    }
}
