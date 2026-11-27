import StreamSystem.*;
import com.mafia.common.protocol.*;
import java.util.*;
import com.mafia.common.model.*;
import com.mafia.server.character.*;
import com.mafia.server.game.*;
import com.mafia.server.game.event.*;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.lobby.*;
import com.mafia.server.net.Session;

public final class PersonalChecks {
    static int checks;
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){flow(); room(); night(); voting(); disconnect(); factory(); events(); roomEvents(); System.out.println("PASS personal checks: "+checks);}

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
    static Fixture fixture(){return fixture(EventDirector.none());}
    static Fixture fixture(EventDirector events){
        Map<String,RoleAbility> roles=new LinkedHashMap<>();RoleName[] kinds={RoleName.MAFIA,RoleName.DOCTOR,RoleName.POLICE,RoleName.CITIZEN,RoleName.CITIZEN,RoleName.CITIZEN};
        for(int i=0;i<6;i++)roles.put("ABCDEF".substring(i,i+1),new StubRole(kinds[i]));
        Channel channel=new Channel();Clock clock=new Clock();FakeCharacters character=new FakeCharacters();
        return new Fixture(new GameSession(channel,roles,character,clock,new PhaseTable(GameConfig.defaults()),new VoteCounter(),new WinChecker(),events),channel,clock); }
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

    /** 15주차: 판을 팩토리로 만들어도 설정대로 6명에게 직업이 배정된다. */
    static void factory(){
        Map<RoleName,RoleAbility> catalog=new EnumMap<>(RoleName.class);for(RoleName name:RoleName.values())catalog.put(name,new StubRole(name));
        Channel channel=new Channel();GameSessionFactory factory=new GameSessionFactory(GameConfig.defaults(),catalog,FakeCharacters::new,new Clock(),new Random(7));
        factory.create(channel).start();check(channel.own.size()==6,"six assigned");
        check(channel.own.values().stream().flatMap(List::stream).filter(m->m.type()==MessageType.ROLE&&m.field(0).equals("MAFIA")).count()==1,"configured distribution");
    }
    /** 14주차: 그날의 사건. 아침에 하나 작동하고 밤에 풀리며, 사건마다 끼어드는 자리가 하나다. */
    static void events(){
        // 사건 없는 판에는 EVENT도 VOTE_CAST도 나가지 않는다
        Fixture plain=fixture();plain.game.start();toVote(plain);abstainAll(plain);
        check(count(plain,MessageType.EVENT)==0&&count(plain,MessageType.VOTE_CAST)==0,"no events without a deck");
        check(talkSeconds(plain)==90,"talk time unchanged without event");

        // 폭풍 → 공개 투표 순서의 덱: 하루에 하나씩, 덱이 비면 그만 나온다
        Fixture f=fixture(EventDirector.inOrder(List.of(new StormEvent(),new OpenVoteEvent()),new Random(1)));
        f.game.start();check(count(f,MessageType.EVENT)==0,"no event on the first night");
        toVote(f);
        check(count(f,MessageType.EVENT)==1&&last(f,MessageType.EVENT).field(0).equals("STORM"),"first morning draws the first event");
        check(talkSeconds(f)==45,"storm halves talk time");
        abstainAll(f);check(f.game.phase()==Phase.NIGHT&&count(f,MessageType.VOTE_CAST)==0,"storm day does not reveal votes");
        toVote(f);
        check(count(f,MessageType.EVENT)==2&&last(f,MessageType.EVENT).field(0).equals("OPEN_VOTE"),"second morning draws the next event");
        check(talkSeconds(f)==90,"talk time is back after the storm day");
        f.game.onPlayerMessage("B",Message.of(MessageType.VOTE,"B"));
        check(count(f,MessageType.VOTE_CAST)==0,"rejected vote is not revealed");
        f.game.onPlayerMessage("B",Message.of(MessageType.VOTE,"C"));
        Message cast=last(f,MessageType.VOTE_CAST);
        check(count(f,MessageType.VOTE_CAST)==1&&cast.field(0).equals("B")&&cast.field(1).equals("C"),"accepted vote is revealed to the room");
        for(String voter:List.of("A","C","E","F"))f.game.onPlayerMessage(voter,Message.of(MessageType.VOTE,""));
        check(f.game.phase()==Phase.NIGHT&&count(f,MessageType.VOTE_CAST)==5,"one reveal per accepted vote");
        toVote(f);abstainAll(f);
        check(count(f,MessageType.EVENT)==2&&count(f,MessageType.VOTE_CAST)==5,"no event after the deck is empty");

        // 가면무도회: 그날 채팅만 이름이 가려지고 말끝이 붙는다
        Fixture m=fixture(EventDirector.inOrder(List.of(new MasqueradeEvent()),new Random(1)));
        m.game.start();
        check(m.game.chatMessage("B","안녕").equals(Message.of(MessageType.CHAT,"B","안녕")),"chat is plain before the event");
        toVote(m);Message masked=m.game.chatMessage("B","안녕");
        check(masked.field(0).equals(MasqueradeEvent.MASKED_NAME)&&masked.field(1).equals("안녕"+MasqueradeEvent.TAIL),"masquerade hides the sender and adds a tail");
        abstainAll(m);
        check(m.game.chatMessage("B","안녕").field(0).equals("B"),"masquerade ends at night");

        // 목격자: 시민 편 한 명에게만, 자기 자신도 마피아도 아닌 이름이 간다. 씨앗을 바꿔도 지켜진다
        for(int seed=0;seed<20;seed++){
            Fixture w=fixture(EventDirector.inOrder(List.of(new WitnessEvent()),new Random(seed)));
            w.game.start();w.clock.fire();
            List<Map.Entry<String,String>> hints=new ArrayList<>();
            w.channel.own.forEach((player,list)->list.stream().filter(x->x.type()==MessageType.SYSTEM_CHAT&&x.field(0).contains(WitnessEvent.HINT_PREFIX))
                    .forEach(x->hints.add(Map.entry(player,x.field(0)))));
            boolean ok=hints.size()==1;
            if(ok){
                String witness=hints.get(0).getKey(),text=hints.get(0).getValue();
                String named=text.substring(text.indexOf(WitnessEvent.HINT_PREFIX)+WitnessEvent.HINT_PREFIX.length(),text.indexOf("님은"));
                ok=!witness.equals("A")&&!named.equals("A")&&!named.equals(witness)&&w.game.alive().contains(named)&&w.game.alive().contains(witness);
            }
            check(ok,"witness hint goes to one citizen and names another living citizen (seed "+seed+")");
            check(w.channel.all.stream().noneMatch(x->x.type()==MessageType.SYSTEM_CHAT&&x.field(0).contains(WitnessEvent.HINT_PREFIX)),"witness hint is never broadcast");
        }

        // 판이 끝나면 사건을 꺼내지 않는다
        Fixture over=fixture(EventDirector.inOrder(List.of(new StormEvent()),new Random(1)));
        over.game.start();over.game.onPlayerLeft("A");
        check(over.game.phase()==Phase.GAME_END&&count(over,MessageType.EVENT)==0,"no event when the game ends at night");

        // 덱 만들기: 같은 씨앗이면 같은 순서, 한 판에 같은 사건은 한 번, 횟수는 0~3
        check(drawAll(7).equals(drawAll(7)),"same seed gives the same order");
        check(new HashSet<>(drawAll(7)).size()==3&&drawAll(7).size()==3,"no event repeats in one game");
        check(EventDirector.shuffled(DayEvents.all(),0,new Random(1)).newDay().isEmpty(),"zero events means none");
        boolean refused=false;try{EventDirector.shuffled(DayEvents.all(),4,new Random(1));}catch(IllegalArgumentException e){refused=true;}
        check(refused,"more than three events is refused");

        // 새 메시지는 줄로 바꿨다가 되읽어도 그대로다. 사건 설명은 구분 문자 없이 보낼 수 있는 글이다
        MessageCodec codec=new MessageCodec();
        for(DayEvent kind:DayEvents.all()){
            Message event=Message.of(MessageType.EVENT,kind.code(),kind.description());
            check(codec.decode(codec.encode(event),Direction.SERVER_TO_CLIENT).equals(event),"event round trip "+kind.code());
        }
        Message abstain=Message.of(MessageType.VOTE_CAST,"B","");
        check(codec.decode(codec.encode(abstain),Direction.SERVER_TO_CLIENT).equals(abstain),"vote cast round trip with abstain");
    }
    /** 밤, 낮 결과, 토론을 시간 만료로 넘겨 투표 단계까지 간다. */
    static void toVote(Fixture f){f.clock.fire();f.clock.fire();f.clock.fire();check(f.game.phase()==Phase.VOTE,"reached vote");}
    static void abstainAll(Fixture f){for(String voter:new ArrayList<>(f.game.alive()))f.game.onPlayerMessage(voter,Message.of(MessageType.VOTE,""));}
    static long count(Fixture f,MessageType type){return f.channel.all.stream().filter(x->x.type()==type).count();}
    static Message last(Fixture f,MessageType type){Message found=null;for(Message x:f.channel.all)if(x.type()==type)found=x;return found;}
    static int talkSeconds(Fixture f){
        Message found=null;for(Message x:f.channel.all)if(x.type()==MessageType.PHASE_CHANGE&&x.field(0).equals("DAY_TALK"))found=x;
        return Integer.parseInt(found.field(1));
    }
    static List<String> drawAll(long seed){
        EventDirector deck=EventDirector.shuffled(DayEvents.all(),3,new Random(seed));List<String> codes=new ArrayList<>();
        for(Optional<DayEvent> e=deck.newDay();e.isPresent();e=deck.newDay())codes.add(e.get().code());
        return codes;
    }
    /** 방 안의 한 사람. 받은 메시지를 모아 둔다. */
    static final class Member implements Session {
        final String name;final List<Message> got=new ArrayList<>();
        Member(String name){this.name=name;}
        public String name(){return name;}public void send(Message m){got.add(m);}
        long count(MessageType type){return got.stream().filter(x->x.type()==type).count();}
        Message last(MessageType type){Message found=null;for(Message x:got)if(x.type()==type)found=x;return found;}
    }
    static boolean refused(Runnable request){try{request.run();return false;}catch(IllegalArgumentException e){return true;}}
    /** 14주차: 방장이 대기방에서 정한 사건 횟수가 방 전체에 전달되고, 그 횟수로 판이 만들어진다. */
    static void roomEvents(){
        Map<RoleName,RoleAbility> catalog=new EnumMap<>(RoleName.class);for(RoleName name:RoleName.values())catalog.put(name,new StubRole(name));
        Clock clock=new Clock();
        RoomService rooms=new RoomService(GameConfig.defaults(),new GameSessionFactory(GameConfig.defaults(),catalog,FakeCharacters::new,clock,new Random(3)),new RoomRegistry(new Random(3)));
        List<Member> people=new ArrayList<>();for(String name:List.of("a","b","c","d","e","f"))people.add(new Member(name));
        Member host=people.get(0),second=people.get(1);
        rooms.create(host,"사건방","PUBLIC");
        check(host.count(MessageType.ROOM_EVENTS)==0,"no event count notice for the default");
        rooms.join(second,"1");
        check(second.count(MessageType.ROOM_EVENTS)==0,"joiner hears nothing while the count is zero");
        check(refused(()->rooms.events(second,"2")),"only the host sets the event count");
        check(refused(()->rooms.events(host,"4"))&&refused(()->rooms.events(host,"-1"))&&refused(()->rooms.events(host,"두번")),"event count must be 0 to 3");
        rooms.events(host,"2");
        check(host.last(MessageType.ROOM_EVENTS).field(0).equals("2")&&second.last(MessageType.ROOM_EVENTS).field(0).equals("2"),"event count is announced to the room");
        for(int i=2;i<6;i++)rooms.join(people.get(i),"1");
        check(people.get(5).last(MessageType.ROOM_EVENTS).field(0).equals("2"),"late joiner learns the event count");
        for(Member m:people)rooms.ready(m,"true");
        check(host.count(MessageType.GAME_START)==1,"game starts when six are ready");
        check(refused(()->rooms.events(host,"1")),"event count is fixed once the game starts");
        clock.fire();
        check(host.count(MessageType.EVENT)==1&&people.get(5).count(MessageType.EVENT)==1,"the room's event count reaches the game");
        // 진행 중 채팅은 판을 거쳐 나간다. 토론 단계까지 가서 한 줄을 보내 본다
        clock.fire();
        Member alive=null;for(Member m:people)if(alive==null&&!refused(()->rooms.chat(m,"안녕")))alive=m;
        Message chat=host.last(MessageType.CHAT);
        boolean masked=host.last(MessageType.EVENT).field(0).equals("MASQUERADE");
        check(alive!=null&&chat!=null&&chat.field(0).equals(masked?MasqueradeEvent.MASKED_NAME:alive.name),"in-game chat goes through the game session");

        // 횟수를 정하지 않은 방은 사건 없이 진행된다
        Clock quietClock=new Clock();
        RoomService quiet=new RoomService(GameConfig.defaults(),new GameSessionFactory(GameConfig.defaults(),catalog,FakeCharacters::new,quietClock,new Random(3)),new RoomRegistry(new Random(3)));
        List<Member> others=new ArrayList<>();for(String name:List.of("a","b","c","d","e","f"))others.add(new Member(name));
        quiet.create(others.get(0),"조용한방","PUBLIC");for(int i=1;i<6;i++)quiet.join(others.get(i),"1");
        for(Member m:others)quiet.ready(m,"true");quietClock.fire();
        check(others.get(0).count(MessageType.EVENT)==0,"a room with no event count plays without events");
    }
}
