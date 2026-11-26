package com.mafia.server.game.event;

import com.mafia.common.model.Team;

import java.util.ArrayList;
import java.util.List;

/**
 * 목격자: 살아 있는 시민 편 한 명에게만 "누구는 마피아가 아니다"를 알린다.
 * 받는 사람 말고 시민 편 생존자가 없으면 힌트는 나가지 않는다.
 */
public final class WitnessEvent implements DayEvent {
    public static final String HINT_PREFIX = "목격: ";

    @Override public String code() { return "WITNESS"; }

    @Override public String description() {
        return "목격자: 누군가 지난밤의 일을 보았습니다.";
    }

    @Override public List<PrivateNote> onStart(DayContext day) {
        List<String> citizens = new ArrayList<>();
        for (String name : day.alive()) {
            if (day.teams().get(name) != Team.MAFIA) citizens.add(name);
        }
        if (citizens.size() < 2) return List.of();
        String witness = citizens.remove(day.random().nextInt(citizens.size()));
        String cleared = citizens.get(day.random().nextInt(citizens.size()));
        return List.of(new PrivateNote(witness, HINT_PREFIX + cleared + "님은 마피아가 아닙니다."));
    }
}
