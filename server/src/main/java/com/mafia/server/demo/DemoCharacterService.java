package com.mafia.server.demo;
import com.mafia.server.character.*;
import java.util.*;

/** 밤 종료 때 조사 통보, 마감 전 퇴장 행동 무효를 가정한 연동용 구현. */
public final class DemoCharacterService implements CharacterService {
    private Set<String> alive=new HashSet<>();private Map<String,RoleAbility> roles=Map.of();
    private final Map<String,NightAction> actions=new HashMap<>();private Runnable completed=()->{};private boolean closed=true;
    public void beginNight(Set<String> alive,Map<String,RoleAbility> roles,Runnable callback){
        this.alive=new HashSet<>(alive);this.roles=Map.copyOf(roles);actions.clear();completed=callback;closed=false;checkComplete();
    }
    public SubmitResult submit(String actor,NightAction action){
        RoleAbility role=roles.get(actor);
        if(closed||!alive.contains(actor)||!alive.contains(action.target())||role==null)
            return new SubmitResult(false,"밤 행동 불가");
        if(role.nightAction().isEmpty()||role.nightAction().get()!=action.type()||!role.canTarget(actor,action.target(),roles))
            return new SubmitResult(false,"직업 또는 대상 규칙 위반");
        actions.put(actor,action);checkComplete();return new SubmitResult(true,"");
    }
    private void checkComplete(){
        if(!closed&&alive.stream().filter(p->roles.get(p).nightAction().isPresent()).allMatch(actions::containsKey)){
            Runnable callback=completed;completed=()->{};callback.run();
        }
    }
    public NightResult endNight(){
        closed=true;String attack="",heal="";Map<String,NightResult.PoliceFinding> findings=new HashMap<>();
        for(var e:actions.entrySet()){
            if(!alive.contains(e.getKey())||!alive.contains(e.getValue().target()))continue;
            NightAction a=e.getValue();
            switch(a.type()){
                case ATTACK->attack=a.target();case HEAL->heal=a.target();
                case CHECK->findings.put(e.getKey(),new NightResult.PoliceFinding(a.target(),roles.get(a.target()).team()==com.mafia.common.model.Team.MAFIA));
            }
        }
        return new NightResult(!attack.isEmpty()&&!attack.equals(heal)?List.of(attack):List.of(),findings);
    }
    public void onPlayerLeft(String actor){alive.remove(actor);actions.remove(actor);actions.entrySet().removeIf(e->e.getValue().target().equals(actor));}
}
