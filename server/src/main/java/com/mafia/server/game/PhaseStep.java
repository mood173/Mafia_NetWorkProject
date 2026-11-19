package com.mafia.server.game;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.MessageType;
import java.util.Set;
public record PhaseStep(Phase phase, int seconds, Set<MessageType> accepted, Phase next) {
    public PhaseStep { accepted=Set.copyOf(accepted); if(seconds<0) throw new IllegalArgumentException("음수 시간"); }
    public boolean accepts(MessageType type) { return accepted.contains(type); }
}
