package com.mafia.server.game;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.MessageType;
import java.util.Set;
/** 단계표의 한 줄: 단계, 제한 시간(초), 그 단계에서 받는 요청, 다음 단계. */
public record PhaseStep(Phase phase, int seconds, Set<MessageType> accepted, Phase next) {
    public PhaseStep { accepted=Set.copyOf(accepted); if(seconds<0) throw new IllegalArgumentException("음수 시간"); }
    public boolean accepts(MessageType type) { return accepted.contains(type); }
}
