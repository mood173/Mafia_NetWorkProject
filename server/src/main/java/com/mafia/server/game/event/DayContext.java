package com.mafia.server.game.event;

import com.mafia.common.model.Team;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** 사건이 작동할 때 판이 건네는 정보: 생존자(입장 순서), 생존자의 편, 무작위 값. */
public record DayContext(List<String> alive, Map<String, Team> teams, Random random) {
    public DayContext {
        alive = List.copyOf(alive);
        teams = Map.copyOf(teams);
    }
}
