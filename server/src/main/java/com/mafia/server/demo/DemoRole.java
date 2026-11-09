package com.mafia.server.demo;
import com.mafia.common.model.*;
import com.mafia.server.character.*;
import java.util.*;
/** 4직업 규칙의 연동 참조 구현. 팀원 RoleFactory/RoleAbility를 대체하는 최종본이 아니다. */
public record DemoRole(RoleName name) implements RoleAbility {
    public Team team(){return name.team();}
    public Optional<ActionType> nightAction(){return switch(name){case MAFIA->Optional.of(ActionType.ATTACK);case DOCTOR->Optional.of(ActionType.HEAL);case POLICE->Optional.of(ActionType.CHECK);case CITIZEN->Optional.empty();};}
    public boolean canTarget(String actor,String target,Map<String,RoleAbility> roles){
        if(!roles.containsKey(target))return false;
        return switch(name){case MAFIA->roles.get(target).team()!=Team.MAFIA;case POLICE->!actor.equals(target);case DOCTOR->true;case CITIZEN->false;};
    }
    public static Map<RoleName,RoleAbility> catalog(){Map<RoleName,RoleAbility> all=new EnumMap<>(RoleName.class);for(RoleName r:RoleName.values())all.put(r,new DemoRole(r));return all;}
}
