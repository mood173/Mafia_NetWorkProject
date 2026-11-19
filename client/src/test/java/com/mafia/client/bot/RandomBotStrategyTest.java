package com.mafia.client.bot;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class RandomBotStrategyTest {
    private static final List<String> ALIVE = List.of("1-1", "userA", "userB");

    /** 여러 번 뽑아 나온 대상 모음. 행동이 없으면 실패. */
    private static Set<String> targets(String role, Phase phase, MessageType expected) {
        // 봇처럼 Random 하나로 여러 번 뽑는다. (연속 시드마다 새로 만들면 첫 값이 거의 같아 분포를 볼 수 없다)
        Set<String> seen = new HashSet<>();
        RandomBotStrategy strategy = new RandomBotStrategy(new Random(42));
        for (int draw = 0; draw < 60; draw++) {
            Message m = strategy.act("1-1", role, phase, ALIVE).orElseThrow();
            assertEquals(expected, m.type());
            seen.add(m.field(0));
        }
        return seen;
    }

    @Test
    void mafiaAttacksOnlyOthersAtNight() {
        assertEquals(Set.of("userA", "userB"), targets("MAFIA", Phase.NIGHT, MessageType.MAFIA_ATTACK));
    }

    @Test
    void policeChecksOnlyOthersAtNight() {
        assertEquals(Set.of("userA", "userB"), targets("POLICE", Phase.NIGHT, MessageType.POLICE_CHECK));
    }

    @Test
    void doctorMayHealAnyoneIncludingSelf() {
        assertEquals(Set.copyOf(ALIVE), targets("DOCTOR", Phase.NIGHT, MessageType.DOCTOR_HEAL));
    }

    @Test
    void everyRoleVotesForAnotherAlivePlayer() {
        for (String role : List.of("CITIZEN", "POLICE", "DOCTOR", "MAFIA"))
            assertEquals(Set.of("userA", "userB"), targets(role, Phase.VOTE, MessageType.VOTE));
    }

    @Test
    void noActionWhenNothingToDo() {
        RandomBotStrategy strategy = new RandomBotStrategy(new Random(1));
        assertEquals(Optional.empty(), strategy.act("1-1", "CITIZEN", Phase.NIGHT, ALIVE), "시민은 밤 능력 없음");
        assertEquals(Optional.empty(), strategy.act("1-1", "MAFIA", Phase.DAY_TALK, ALIVE), "토론 단계는 행동 없음");
        assertEquals(Optional.empty(), strategy.act("1-9", "MAFIA", Phase.NIGHT, ALIVE), "죽은 봇은 행동 없음");
        assertEquals(Optional.empty(), strategy.act("1-1", "MAFIA", Phase.NIGHT, List.of("1-1")), "대상 없음");
    }
}
