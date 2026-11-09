package com.mafia.server.game;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.engine.GameConfig;
import java.util.EnumMap;
import java.util.Set;
public final class PhaseTable {
    private final EnumMap<Phase,PhaseStep> steps=new EnumMap<>(Phase.class);
    public PhaseTable(GameConfig config) {
        put(Phase.WAITING,0,Set.of(),Phase.NIGHT);
        put(Phase.NIGHT,config.nightSeconds(),Set.of(MessageType.MAFIA_ATTACK,MessageType.DOCTOR_HEAL,MessageType.POLICE_CHECK),Phase.DAY_RESULT);
        put(Phase.DAY_RESULT,config.dayResultSeconds(),Set.of(),Phase.DAY_TALK);
        put(Phase.DAY_TALK,config.talkSeconds(),Set.of(MessageType.CHAT),Phase.VOTE);
        put(Phase.VOTE,config.voteSeconds(),Set.of(MessageType.VOTE,MessageType.CHAT),Phase.NIGHT);
        put(Phase.GAME_END,0,Set.of(),Phase.GAME_END);
        for(Phase p:Phase.values()) if(!steps.containsKey(p) || !steps.containsKey(steps.get(p).next()))
            throw new IllegalStateException("단계 등록 누락");
    }
    private void put(Phase p,int seconds,Set<MessageType> accepted,Phase next) { steps.put(p,new PhaseStep(p,seconds,accepted,next)); }
    public PhaseStep get(Phase p) { return steps.get(p); }
}
