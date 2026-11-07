package com.mafia.server.engine;

import com.mafia.common.model.RoleName;

import java.util.EnumMap;
import java.util.Map;

/** 서버 설정 값 (읽기 전용). 단계 시간·정원·직업 구성은 여기서만 바꾼다. */
public record GameConfig(
        int port,
        int roomCapacity,
        int nightSeconds,
        int dayResultSeconds,
        int talkSeconds,
        int voteSeconds,
        Map<RoleName, Integer> roleCounts) {

    public GameConfig {
        roleCounts = Map.copyOf(roleCounts);
    }

    public static GameConfig defaults() {
        Map<RoleName, Integer> roles = new EnumMap<>(RoleName.class);
        roles.put(RoleName.CITIZEN, 3);
        roles.put(RoleName.POLICE, 1);
        roles.put(RoleName.DOCTOR, 1);
        roles.put(RoleName.MAFIA, 1);
        return new GameConfig(5000, 6, 40, 5, 90, 30, roles);
    }

    public int totalRoles() {
        return roleCounts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
