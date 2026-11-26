package com.mafia.client.stream;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import java.util.*;

/** 서버 메시지의 화면용 투영. Scene은 읽기만 한다. */
public final class ClientState {
    private String myName="",myRole="",roomId="",roomName="",host="",roomCode="",winner="",reveal="",todayEvent="";
    private Phase phase=Phase.WAITING;
    private long deadline;
    private final List<String> participants=new ArrayList<>();
    private final List<String> alive=new ArrayList<>();
    private final Map<String,Boolean> ready=new LinkedHashMap<>();
    void apply(Message m) {
        switch(m.type()) {
            case LOGIN_OK -> { reset(); myName=m.field(0); }
            case ROOM_STATE -> {
                roomId=m.field(0); roomName=m.field(1); host=m.field(3); ready.clear();
                if(!m.field(4).isEmpty()) for(String entry:m.field(4).split(",")) {
                    String[] pair=entry.split(":",2);
                    if(pair.length!=2 || !(pair[1].equals("true") || pair[1].equals("false")))
                        throw new IllegalArgumentException("준비 상태 형식 오류");
                    ready.put(pair[0],Boolean.parseBoolean(pair[1]));
                }
            }
            case ROOM_CODE -> roomCode=m.field(0);
            case GAME_START -> {
                clearGame();
                if(!m.field(0).isEmpty()){
                    List<String> all=Arrays.asList(m.field(0).split(","));
                    participants.addAll(all); alive.addAll(all);
                }
            }
            case ROLE -> myRole=m.field(0);
            case PHASE_CHANGE -> updatePhase(m.field(0),m.field(1));
            case GAME_STATE -> {
                updatePhase(m.field(0),m.field(2)); alive.clear();
                if(!m.field(1).isEmpty()) alive.addAll(Arrays.asList(m.field(1).split(",")));
            }
            case EVENT -> todayEvent=m.field(1);
            case PLAYER_DIED -> alive.remove(m.field(0));
            case GAME_END -> { winner=m.field(0); reveal=m.field(1); phase=Phase.GAME_END; deadline=0; todayEvent=""; }
            case ROOM_LEFT -> clearRoom();
            default -> { }
        }
    }
    private void updatePhase(String name,String seconds) {
        Phase next=Phase.valueOf(name); int duration=Integer.parseInt(seconds);
        if(duration<0) throw new IllegalArgumentException("음수 시간");
        if(next==Phase.NIGHT || next==Phase.GAME_END) todayEvent="";
        phase=next; deadline=System.nanoTime()+duration*1_000_000_000L;
    }
    private void clearGame() { myRole=""; participants.clear(); alive.clear(); phase=Phase.WAITING; deadline=0; winner=""; reveal=""; todayEvent=""; }
    private void clearRoom() { roomId=""; roomName=""; host=""; roomCode=""; ready.clear(); clearGame(); }
    public void reset() { myName=""; clearRoom(); }
    public String myName(){return myName;} public String myRole(){return myRole;}
    public Phase phase(){return phase;} public List<String> participants(){return List.copyOf(participants);}
    public List<String> alive(){return List.copyOf(alive);}
    public boolean amAlive(){return alive.contains(myName);}
    public String roomId(){return roomId;} public String roomName(){return roomName;}
    public String host(){return host;} public String roomCode(){return roomCode;}
    public Map<String,Boolean> ready(){return Collections.unmodifiableMap(new LinkedHashMap<>(ready));}
    /** 오늘 작동 중인 사건의 설명("이름: 내용"). 없으면 빈 값. 밤이 되면 지워진다. */
    public String todayEvent(){return todayEvent;}
    /** 사건 설명의 이름 부분. */
    public String todayEventName(){int cut=todayEvent.indexOf(':');return cut<0?todayEvent:todayEvent.substring(0,cut);}
    public String winner(){return winner;} public String reveal(){return reveal;}
    public int remainingSeconds(){return phase==Phase.GAME_END || deadline==0 ? 0 : (int)Math.max(0,(deadline-System.nanoTime()+999_999_999L)/1_000_000_000L);}
}
