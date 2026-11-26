package com.mafia.server.character.role;

import com.mafia.common.model.RoleName;
import com.mafia.common.model.Team;
import com.mafia.server.character.ActionType;
import com.mafia.server.character.RoleAbility;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** 직업 4종의 팀·밤 행동·대상 규칙 (제안서 5장, 역할 분담 "같이 정할 것"). */
final class RoleCatalogTest {
    private final Map<RoleName, RoleAbility> catalog = RoleCatalog.standard();

    /** 한 판의 배정: maf=마피아, pol=경찰, doc=의사, c1=시민 */
    private Map<String, RoleAbility> table() {
        Map<String, RoleAbility> roles = new LinkedHashMap<>();
        roles.put("maf", catalog.get(RoleName.MAFIA));
        roles.put("pol", catalog.get(RoleName.POLICE));
        roles.put("doc", catalog.get(RoleName.DOCTOR));
        roles.put("c1", catalog.get(RoleName.CITIZEN));
        return roles;
    }

    @Test
    void catalogHasEveryRoleWithItsTeamAndNightAction() {
        assertEquals(Map.of(
                RoleName.CITIZEN, Optional.<ActionType>empty(),
                RoleName.POLICE, Optional.of(ActionType.CHECK),
                RoleName.DOCTOR, Optional.of(ActionType.HEAL),
                RoleName.MAFIA, Optional.of(ActionType.ATTACK)),
                Map.of(RoleName.CITIZEN, catalog.get(RoleName.CITIZEN).nightAction(),
                        RoleName.POLICE, catalog.get(RoleName.POLICE).nightAction(),
                        RoleName.DOCTOR, catalog.get(RoleName.DOCTOR).nightAction(),
                        RoleName.MAFIA, catalog.get(RoleName.MAFIA).nightAction()));
        for (RoleName name : RoleName.values()) {
            assertEquals(name, catalog.get(name).name());
            assertEquals(name.team(), catalog.get(name).team());
        }
        assertEquals(Team.MAFIA, catalog.get(RoleName.MAFIA).team());
    }

    @Test
    void mafiaAttacksOnlyNonMafia() {
        RoleAbility mafia = catalog.get(RoleName.MAFIA);
        assertTrue(mafia.canTarget("maf", "c1", table()));
        assertTrue(mafia.canTarget("maf", "pol", table()));
        assertFalse(mafia.canTarget("maf", "maf", table()), "자기 자신(마피아 팀) 불가");
    }

    @Test
    void policeChecksAnyoneButSelf() {
        RoleAbility police = catalog.get(RoleName.POLICE);
        assertTrue(police.canTarget("pol", "maf", table()));
        assertFalse(police.canTarget("pol", "pol", table()));
    }

    @Test
    void doctorMayHealSelf() {
        RoleAbility doctor = catalog.get(RoleName.DOCTOR);
        assertTrue(doctor.canTarget("doc", "doc", table()), "제안서 5.2: 의사 자기 보호 허용");
        assertTrue(doctor.canTarget("doc", "c1", table()));
    }

    @Test
    void citizenHasNoTargetAndUnknownTargetsAreRejected() {
        assertFalse(catalog.get(RoleName.CITIZEN).canTarget("c1", "maf", table()));
        for (RoleName name : RoleName.values())
            assertFalse(catalog.get(name).canTarget("x", "nobody", table()), name + ": 판에 없는 대상");
    }
}
