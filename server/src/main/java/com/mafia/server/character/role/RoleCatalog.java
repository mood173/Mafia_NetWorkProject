package com.mafia.server.character.role;

import com.mafia.common.model.RoleName;
import com.mafia.server.character.RoleAbility;

import java.util.EnumMap;
import java.util.Map;

/** 직업 이름 → 직업 객체. 직업은 상태가 없으므로 판끼리 같은 객체를 써도 된다. GameSessionFactory에 넘긴다. */
public final class RoleCatalog {
    private RoleCatalog() {
    }

    public static Map<RoleName, RoleAbility> standard() {
        Map<RoleName, RoleAbility> roles = new EnumMap<>(RoleName.class);
        roles.put(RoleName.CITIZEN, new CitizenRole());
        roles.put(RoleName.POLICE, new PoliceRole());
        roles.put(RoleName.DOCTOR, new DoctorRole());
        roles.put(RoleName.MAFIA, new MafiaRole());
        return Map.copyOf(roles);
    }
}
