import StreamSystem.*;
import com.mafia.common.protocol.*;
import java.util.*;
import com.mafia.common.model.*;
import com.mafia.server.character.*;
import com.mafia.server.game.*;
import com.mafia.server.engine.GameConfig;

public final class PersonalChecks {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){flow(); room(); night(); voting(); disconnect(); System.out.println("PASS personal checks: "+checks);}

    static void flow() {
        StreamManager f = new StreamManager();
        check(f.app()==AppStream.main,"initial");
        f.accept(Message.of(MessageType.LOGIN_OK,"A"));check(f.app()==AppStream.Lobby,"login");
        f.accept(Rejection.of("bad").toMessage());check(f.app()==AppStream.Lobby,"error stays");
        f.accept(Message.of(MessageType.GAME_START,"A,B,C,D,E,F"));check(f.app()==AppStream.GameScene,"game");
        f.accept(Message.of(MessageType.PHASE_CHANGE,"NIGHT","40"));check(f.game()==gameStream.NIGHT,"phase");
        f.accept(Message.of(MessageType.GAME_END,"CITIZEN","A:MAFIA"));check(f.app()==AppStream.End,"result");
        f.disconnected();check(f.app()==AppStream.main,"disconnect");
    }

    static void room() {
        StreamManager f=new StreamManager();f.accept(Message.of(MessageType.LOGIN_OK,"A"));
        f.accept(Message.of(MessageType.ROOM_STATE,"1","room","PUBLIC","A","A:false"));check(f.app()==AppStream.FrontDesk,"room response");
        f.accept(Message.of(MessageType.ROOM_LEFT,"1"));check(f.app()==AppStream.Lobby,"leave response");
        MessageCodec codec=new MessageCodec();
        Message m=Message.of(MessageType.ROOM_STATE,"1","room","PUBLIC","A","A:false");
        check(codec.decode(codec.encode(m),Direction.SERVER_TO_CLIENT).equals(m),"shared draft protocol");
        // 거절 응답: 줄로 바꿨다가 되읽어도 요청 종류와 코드가 그대로다
        Rejection sent=Rejection.of("이미 투표함",MessageType.VOTE,RejectCode.ALREADY_DONE);
        Rejection back=Rejection.from(codec.decode(codec.encode(sent.toMessage()),Direction.SERVER_TO_CLIENT));
        check(back.equals(sent)&&back.isAbout(MessageType.VOTE)&&!back.isAbout(MessageType.CHAT),"rejection round trip");
        Rejection plain=Rejection.from(codec.decode(codec.encode(Rejection.of("연결 오류").toMessage()),Direction.SERVER_TO_CLIENT));
        check(plain.request().isEmpty()&&plain.code()==RejectCode.UNKNOWN,"rejection without request");
        check(Rejection.from(Message.of(MessageType.ERROR,"옛 형식")).code()==RejectCode.UNKNOWN,"one-field error is read as unknown");
    }

    static final class Clock implements PhaseTimer {
        final List<Runnable> jobs=new ArrayList<>();
        public Ticket schedule(int seconds,Runnable callback){jobs.add(callback);return ()->{};}
        public long nowNanos(){return 0;}
        void fire(){jobs.get(jobs.size()-1).run();}
    }
    static final class Channel implements RoomChannel {
        final List<Message> all=new ArrayList<>();final Map<String,List<Message>> own=new HashMap<>();
        public List<String> players(){return List.of("A","B","C","D","E","F");}
        public void broadcast(Message m){all.add(m);}public void send(String p,Message m){own.computeIfAbsent(p,x->new ArrayList<>()).add(m);}
    }
    /** 역할 설명자일 뿐 능력 규칙 구현이 아니다. 테스트 소스 안에서만 사용한다. */
    record StubRole(RoleName name) implements RoleAbility {
        public Team team(){return name.team();}
        public Optional<ActionType> nightAction(){return Optional.empty();}
        public boolean canTarget(String a,String t,Map<String,RoleAbility> r){return true;}
    }
    /** 고정 NightResult를 주입한다. 공격/보호/조사 계산을 구현하거나 검증하지 않는다. */
    static final class FakeCharacters implements CharacterService {
        Runnable completed;int submits;
        public void beginNight(Set<String> a,Map<String,RoleAbility> r,Runnable done){completed=done;submits=0;}
        public SubmitResult submit(String actor,NightAction action){if(++submits==3)completed.run();return new SubmitResult(true,"");}
        public NightResult endNight(){return new NightResult(List.of("D"),Map.of("C",new NightResult.PoliceFinding("A",true)));}
        public void onPlayerLeft(String actor){}
    }
    record Fixture(GameSession game,Channel channel,Clock clock){}
    static Fixture fixture(){
        Map<String,RoleAbility> roles=new LinkedHashMap<>();RoleName[] kinds={RoleName.MAFIA,RoleName.DOCTOR,RoleName.POLICE,RoleName.CITIZEN,RoleName.CITIZEN,RoleName.CITIZEN};
        for(int i=0;i<6;i++)roles.put("ABCDEF".substring(i,i+1),new StubRole(kinds[i]));
        Channel channel=new Channel();Clock clock=new Clock();FakeCharacters character=new FakeCharacters();
        return new Fixture(new GameSession(channel,roles,character,clock,new PhaseTable(GameConfig.defaults()),new VoteCounter(),new WinChecker()),channel,clock); }
    static void finishNight(Fixture f){
        f.game.onPlayerMessage("A",Message.of(MessageType.MAFIA_ATTACK,"D"));
        f.game.onPlayerMessage("B",Message.of(MessageType.DOCTOR_HEAL,"B"));
        f.game.onPlayerMessage("C",Message.of(MessageType.POLICE_CHECK,"A"));
    }
    static void night(){
        Fixture f=fixture();f.game.start();check(f.game.phase()==Phase.NIGHT,"starts night");
        check(f.channel.own.size()==6,"private roles sent");
        check(f.channel.all.stream().noneMatch(m->m.type()==MessageType.ROLE),"no role broadcast");
        f.game.onPlayerMessage("A",Message.of(MessageType.VOTE,"B"));check(f.game.phase()==Phase.NIGHT,"phase gate");
        finishNight(f);check(f.game.phase()==Phase.DAY_RESULT,"fixed night result applied");
        check(!f.game.alive().contains("D"),"death applied by game");
        check(f.channel.all.stream().noneMatch(m->m.type()==MessageType.POLICE_RESULT),"police result private");
        f.clock.jobs.get(0).run();check(f.game.phase()==Phase.DAY_RESULT,"stale timeout ignored");
        f.clock.fire();check(f.game.phase()==Phase.DAY_TALK,"day follows result");
        Fixture timeout=fixture();timeout.game.start();timeout.clock.fire();check(timeout.game.phase()==Phase.DAY_RESULT,"timeout");
    }

    /** 13주차: 투표 집계, 본인 투표·중복 투표 거절, 처형 뒤 승패 판정. */
    static void voting(){
        VoteCounter votes=new VoteCounter();check(votes.count(Map.of("A","B","B","A")).eliminated().isEmpty(),"tie");
        check(votes.count(Map.of("A","","B","")).eliminated().isEmpty(),"abstain");
        Fixture f=fixture();f.game.start();finishNight(f);f.clock.fire();f.clock.fire();
        check(f.game.phase()==Phase.VOTE,"vote after talk");
        f.game.onPlayerMessage("B",Message.of(MessageType.VOTE,"B"));
        check(rejections(f,"B").stream().anyMatch(r->r.isAbout(MessageType.VOTE)&&r.code()==RejectCode.INVALID_TARGET),"self vote rejected as invalid target");
        f.game.onPlayerMessage("B",Message.of(MessageType.VOTE,"A"));f.game.onPlayerMessage("B",Message.of(MessageType.VOTE,"A"));
        check(rejections(f,"B").stream().anyMatch(r->r.isAbout(MessageType.VOTE)&&r.code()==RejectCode.ALREADY_DONE),"duplicate vote rejected as already done");
        for(String voter:List.of("A","C","E","F"))f.game.onPlayerMessage(voter,Message.of(MessageType.VOTE,voter.equals("A")?"":"A"));
        check(f.game.phase()==Phase.GAME_END,"citizen wins");
        f.clock.fire();check(f.channel.all.stream().filter(m->m.type()==MessageType.GAME_END).count()==1,"single ending");
        // 단계 밖 요청의 거절에는 보낸 요청 종류가 그대로 담긴다
        Fixture g=fixture();g.game.start();g.game.onPlayerMessage("A",Message.of(MessageType.VOTE,"B"));
        check(rejections(g,"A").stream().anyMatch(r->r.isAbout(MessageType.VOTE)&&r.code()==RejectCode.NOT_ALLOWED_NOW),"out-of-phase request names itself");
    }
    static List<Rejection> rejections(Fixture f,String player){
        return f.channel.own.getOrDefault(player,List.of()).stream().filter(m->m.type()==MessageType.ERROR).map(Rejection::from).toList();
    }

    /** 14주차: 접속 이탈은 사망으로 처리하고, 마피아가 나가면 판이 끝난다. 두 번 불려도 한 번만 처리한다. */
    static void disconnect(){
        Fixture f=fixture();f.game.start();f.game.onPlayerLeft("A");check(f.game.phase()==Phase.GAME_END,"mafia disconnect ends game");
        f.game.onPlayerLeft("A");check(f.channel.all.stream().filter(m->m.type()==MessageType.PLAYER_DIED).count()==1,"disconnect idempotent");
    }
}
