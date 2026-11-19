package com.mafia.client.bot;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 테스트 봇의 무작위 행동. 서버가 거절하지 않도록 단계와 직업 규칙에 맞는 대상 중에서만 고른다.
 * 밤: 마피아·경찰은 자신을 뺀 생존자, 의사는 자신 포함 생존자. 투표: 자신을 뺀 생존자.
 */
final class RandomBotStrategy {
    private final Random random;

    RandomBotStrategy(Random random) {
        this.random = random;
    }

    /** 이번 단계에 보낼 행동. 죽었거나 할 일이 없으면 비어 있다. */
    Optional<Message> act(String me, String role, Phase phase, List<String> alive) {
        if (!alive.contains(me)) return Optional.empty();
        List<String> others = alive.stream().filter(p -> !p.equals(me)).toList();
        return switch (phase) {
            case NIGHT -> switch (role) {
                case "MAFIA" -> pick(MessageType.MAFIA_ATTACK, others);
                case "POLICE" -> pick(MessageType.POLICE_CHECK, others);
                case "DOCTOR" -> pick(MessageType.DOCTOR_HEAL, alive);
                default -> Optional.empty();
            };
            case VOTE -> pick(MessageType.VOTE, others);
            default -> Optional.empty();
        };
    }

    private Optional<Message> pick(MessageType type, List<String> candidates) {
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.of(Message.of(type, candidates.get(random.nextInt(candidates.size()))));
    }
}
