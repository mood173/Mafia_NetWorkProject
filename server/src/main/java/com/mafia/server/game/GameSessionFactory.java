package com.mafia.server.game;
import com.mafia.common.model.RoleName;
import com.mafia.server.character.CharacterService;
import com.mafia.server.character.RoleAbility;
import com.mafia.server.engine.GameConfig;
import java.util.*;
import java.util.function.Supplier;
public final class GameSessionFactory {
    private final GameConfig config;
    private final Map<RoleName,RoleAbility> catalog;
    private final Supplier<CharacterService> characters;
    private final PhaseTimer timer;
    private final Random random;
    public GameSessionFactory(GameConfig c,Map<RoleName,RoleAbility> roles,Supplier<CharacterService> chars,PhaseTimer timer,Random random) {
        config=c; catalog=Map.copyOf(roles); characters=chars; this.timer=timer; this.random=random;
        if(c.totalRoles()!=c.roomCapacity()) throw new IllegalArgumentException("직업 합과 정원 불일치");
        c.roleCounts().forEach((r,n) -> { if(n<0 || !catalog.containsKey(r)) throw new IllegalArgumentException("직업 설정 오류"); });
    }
    public GameSession create(RoomChannel channel) {
        List<String> players=channel.players();
        if(players.size()!=config.roomCapacity() || new HashSet<>(players).size()!=players.size())
            throw new IllegalArgumentException("게임 시작 인원 또는 중복 이름 오류");
        List<RoleAbility> shuffled=new ArrayList<>();
        config.roleCounts().forEach((role,n) -> { for(int i=0;i<n;i++) shuffled.add(catalog.get(role)); });
        Collections.shuffle(shuffled,random);
        Map<String,RoleAbility> assigned=new LinkedHashMap<>();
        for(int i=0;i<players.size();i++) assigned.put(players.get(i),shuffled.get(i));
        return new GameSession(channel,assigned,characters.get(),timer,new PhaseTable(config),new VoteCounter(),new WinChecker());
    }
}
