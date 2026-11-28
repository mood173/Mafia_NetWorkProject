package com.mafia.server.game;
import com.mafia.common.model.RoleName;
import com.mafia.server.character.CharacterService;
import com.mafia.server.character.RoleAbility;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.event.DayEvent;
import com.mafia.server.game.event.DayEvents;
import com.mafia.server.game.event.EventDirector;
import java.util.*;
import java.util.function.Supplier;
/**
 * 판을 만드는 곳. 6명에게 설정대로 직업을 무작위로 나눠 주고, 방이 정한 횟수만큼 사건 덱을 만든 뒤
 * GameSession에 필요한 부품(단계표, 투표 집계, 승패 판정, 시계)을 넣어 준다.
 * 무작위 값을 밖에서 받아서, 검사할 때는 씨앗을 고정해 같은 결과를 얻는다.
 */
public final class GameSessionFactory {
    private final GameConfig config;
    private final Map<RoleName,RoleAbility> catalog;
    private final Supplier<CharacterService> characters;
    private final PhaseTimer timer;
    private final Random random;
    private final List<DayEvent> eventKinds;
    private final int eventCount;
    /** 사건 종류는 전부 쓰고, 횟수를 따로 주지 않은 판은 사건 없이 만든다. */
    public GameSessionFactory(GameConfig c,Map<RoleName,RoleAbility> roles,Supplier<CharacterService> chars,PhaseTimer timer,Random random) {
        this(c,roles,chars,timer,random,DayEvents.all(),0);
    }
    /** eventCount는 create(channel)로 만드는 판의 기본 사건 횟수다. 방이 정한 횟수는 create(channel, 횟수)로 넘긴다. */
    public GameSessionFactory(GameConfig c,Map<RoleName,RoleAbility> roles,Supplier<CharacterService> chars,PhaseTimer timer,Random random,List<DayEvent> eventKinds,int eventCount) {
        this.eventKinds=List.copyOf(eventKinds); this.eventCount=eventCount;
        EventDirector.shuffled(this.eventKinds,eventCount,new Random(0)); // 횟수 범위 검사
        config=c; catalog=Map.copyOf(roles); characters=chars; this.timer=timer; this.random=random;
        if(c.totalRoles()!=c.roomCapacity()) throw new IllegalArgumentException("직업 합과 정원 불일치");
        c.roleCounts().forEach((r,n) -> { if(n<0 || !catalog.containsKey(r)) throw new IllegalArgumentException("직업 설정 오류"); });
    }
    public GameSession create(RoomChannel channel) { return create(channel,eventCount); }
    /** events: 이 판에 쓸 사건 횟수(0~3). 종류와 순서는 판마다 무작위로 정한다. */
    public GameSession create(RoomChannel channel,int events) {
        List<String> players=channel.players();
        if(players.size()!=config.roomCapacity() || new HashSet<>(players).size()!=players.size())
            throw new IllegalArgumentException("게임 시작 인원 또는 중복 이름 오류");
        List<RoleAbility> shuffled=new ArrayList<>();
        config.roleCounts().forEach((role,n) -> { for(int i=0;i<n;i++) shuffled.add(catalog.get(role)); });
        Collections.shuffle(shuffled,random);
        Map<String,RoleAbility> assigned=new LinkedHashMap<>();
        for(int i=0;i<players.size();i++) assigned.put(players.get(i),shuffled.get(i));
        return new GameSession(channel,assigned,characters.get(),timer,new PhaseTable(config),new VoteCounter(),new WinChecker(),
                EventDirector.shuffled(eventKinds,events,random));
    }
}
