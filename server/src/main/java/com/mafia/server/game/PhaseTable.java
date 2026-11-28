package com.mafia.server.game;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.engine.GameConfig;
import java.util.EnumMap;
import java.util.Set;
/**
 * 판의 단계표. 단계마다 제한 시간, 그 단계에서 받는 요청, 다음 단계를 한곳에 적어 둔다.
 * GameSession은 이 표만 보고 "지금 이 요청을 받아도 되는가", "다음은 어느 단계인가"를 정한다.
 * 단계를 바꾸거나 늘릴 때는 이 표만 고치면 된다.
 */
public final class PhaseTable {
    private final EnumMap<Phase,PhaseStep> steps=new EnumMap<>(Phase.class);
    public PhaseTable(GameConfig config) {
        //  단계          제한 시간               받는 요청                     다음 단계
        put(Phase.WAITING,0,Set.of(),Phase.NIGHT);
        put(Phase.NIGHT,config.nightSeconds(),Set.of(MessageType.MAFIA_ATTACK,MessageType.DOCTOR_HEAL,MessageType.POLICE_CHECK),Phase.DAY_RESULT);
        put(Phase.DAY_RESULT,config.dayResultSeconds(),Set.of(),Phase.DAY_TALK);
        put(Phase.DAY_TALK,config.talkSeconds(),Set.of(MessageType.CHAT),Phase.VOTE);
        put(Phase.VOTE,config.voteSeconds(),Set.of(MessageType.VOTE,MessageType.CHAT),Phase.NIGHT);
        put(Phase.GAME_END,0,Set.of(),Phase.GAME_END);
        // 빠진 단계가 있으면 게임 중에 터지지 않게 서버를 켤 때 바로 알린다
        for(Phase p:Phase.values()) if(!steps.containsKey(p) || !steps.containsKey(steps.get(p).next()))
            throw new IllegalStateException("단계 등록 누락");
    }
    private void put(Phase p,int seconds,Set<MessageType> accepted,Phase next) { steps.put(p,new PhaseStep(p,seconds,accepted,next)); }
    public PhaseStep get(Phase p) { return steps.get(p); }
}
