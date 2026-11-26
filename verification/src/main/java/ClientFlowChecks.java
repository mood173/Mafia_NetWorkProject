import com.mafia.client.net.*;
import com.mafia.client.stream.*;
import com.mafia.client.scene.*;
import com.mafia.client.game.*;

import com.mafia.common.protocol.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** 실제 Swing/GameStream의 컴포넌트 검사. 메시지를 직접 넣는 검사이며 실제 TCP 시험과 구분한다. */
public final class ClientFlowChecks {
    static int assertions;
    static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    static final class Conn implements Connection {
        final List<Message> sent=new ArrayList<>();public void connect(String h,int p){}public void send(Message m){sent.add(m);}
        public void setListener(MessageListener l){}public void close(){}
    }
    static final class Observed implements Scene {
        final Scene delegate;final SceneId id;final GameStream stream;
        Observed(Scene s,SceneId id,GameStream stream){delegate=s;this.id=id;this.stream=stream;}
        public JComponent view(){return delegate.view();}
        public void onEnter(){check(SwingUtilities.isEventDispatchThread(),"enter EDT");delegate.onEnter();}
        public void onExit(){delegate.onExit();}
        public void onMessage(Message m){check(SwingUtilities.isEventDispatchThread(),"message EDT");check(stream.current()==id,"message delivered after transition");delegate.onMessage(m);}
    }
    static final class TestAbilityView extends JPanel implements com.mafia.client.character.AbilityView {
        int unlocked;
        public JComponent view(){return this;} public void render(){} public void unlockOnError(){unlocked++;}
    }
    static final class TestChatView extends JPanel implements com.mafia.client.ui.ChatView {
        public JComponent view(){return this;}public void accept(Message m){}public void render(ClientState s){}public void reset(){}
    }
    static GameStream stream;static JFrame frame;static TestAbilityView ability;
    static void send(Message message)throws Exception{
        Thread receive=new Thread(()->stream.onMessage(message));receive.start();receive.join();
        SwingUtilities.invokeAndWait(()->{});
    }
    static void snapshot(String name)throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{frame.validate();BufferedImage image=new BufferedImage(frame.getContentPane().getWidth(),frame.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);
                var g=image.createGraphics();frame.getContentPane().printAll(g);g.dispose();Path out=Path.of("build","ui-checks",name+".png");Files.createDirectories(out.getParent());ImageIO.write(image,"png",out.toFile());
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    public static void main(String[] args)throws Exception{
        try{
            SwingUtilities.invokeAndWait(()->{
                Conn conn=new Conn();frame=new JFrame("화면 흐름 검사");stream=new GameStream(frame);ClientState state=stream.state();ability=new TestAbilityView();
                Map<SceneId,Scene> all=new EnumMap<>(SceneId.class);
                all.put(SceneId.MAIN,new MainScene(conn));all.put(SceneId.LOBBY,new LobbyScene(conn));all.put(SceneId.ROOM,new RoomScene(state,conn));
                all.put(SceneId.GAME,new GameScene(state,conn,new PhaseBanner(state),new VotePanel(state,conn),ability,new TestChatView()));
                all.put(SceneId.RESULT,new ResultScene(state,conn,stream::returnToRoom));
                all.forEach((id,scene)->stream.scenes().register(id,new Observed(scene,id,stream)));stream.start(SceneId.MAIN);
            });
            snapshot("01-main");
            send(Message.of(MessageType.LOGIN_OK,"민준"));check(stream.current()==SceneId.LOBBY,"login response routes lobby");snapshot("02-lobby");
            send(Message.of(MessageType.ROOM_STATE,"1","연동 시연","PUBLIC","민준","민준:true,경탁:false"));
            check(stream.current()==SceneId.ROOM&&stream.state().ready().size()==2,"room state rendered");snapshot("03-room");
            send(Message.of(MessageType.GAME_START,"민준,경탁,A,B,C,D"));send(Message.of(MessageType.ROLE,"MAFIA"));
            send(Message.of(MessageType.PHASE_CHANGE,"NIGHT","30"));
            check(stream.current()==SceneId.GAME&&stream.state().amAlive(),"game entry and own player");
            // 거절 응답: 채팅 거절로는 능력 패널을 건드리지 않고, 능력 거절이면 한 번 풀며, 이미 받아들여진 거절이면 풀지 않는다
            send(Rejection.of("지금은 채팅할 수 없습니다",MessageType.CHAT).toMessage());
            check(stream.current()==SceneId.GAME&&ability.unlocked==0,"chat rejection leaves ability locked");
            send(Rejection.of("그 대상에게는 능력을 쓸 수 없습니다",MessageType.MAFIA_ATTACK,RejectCode.INVALID_TARGET).toMessage());
            check(ability.unlocked==1,"ability rejection unlocks ability");
            send(Rejection.of("이미 능력을 사용했습니다",MessageType.MAFIA_ATTACK,RejectCode.ALREADY_DONE).toMessage());
            check(ability.unlocked==1,"already-done rejection keeps lock");
            // 사건: 아침에 받은 사건이 그날 표시되고, 공개 투표 알림은 화면을 바꾸지 않으며, 밤이 되면 지워진다
            send(Message.of(MessageType.PHASE_CHANGE,"DAY_RESULT","5"));send(Message.of(MessageType.EVENT,"OPEN_VOTE","공개 투표: 오늘은 누가 누구에게 투표했는지 바로 공개됩니다."));
            check(stream.current()==SceneId.GAME&&stream.state().todayEventName().equals("공개 투표"),"event shown for the day");
            send(Message.of(MessageType.PHASE_CHANGE,"VOTE","30"));send(Message.of(MessageType.VOTE_CAST,"경탁","민준"));
            check(stream.current()==SceneId.GAME&&!stream.state().todayEvent().isEmpty(),"open vote notice keeps game scene");snapshot("04-game");
            send(Message.of(MessageType.PHASE_CHANGE,"NIGHT","30"));check(stream.state().todayEvent().isEmpty(),"event cleared at night");
            send(Message.of(MessageType.GAME_END,"CITIZEN","민준:MAFIA,경탁:POLICE,A:DOCTOR,B:CITIZEN,C:CITIZEN,D:CITIZEN"));
            check(stream.current()==SceneId.RESULT,"result entry");snapshot("05-result");
            send(Message.of(MessageType.ROOM_STATE,"1","연동 시연","PUBLIC","경탁","경탁:true"));
            check(stream.current()==SceneId.RESULT,"late room state preserves result");
            send(Message.of(MessageType.ROOM_LEFT,"1"));check(stream.current()==SceneId.LOBBY&&stream.state().myRole().isEmpty()&&stream.state().alive().isEmpty(),"leave clears previous game");
            Thread receive=new Thread(stream::onDisconnected);receive.start();receive.join();SwingUtilities.invokeAndWait(()->{});
            check(stream.current()==SceneId.MAIN&&stream.state().myName().isEmpty(),"disconnect clears identity");
            System.out.println("PASS Swing flow checks: "+assertions+" / 5 component renders (injected messages)");
        }finally{SwingUtilities.invokeAndWait(()->{if(frame!=null)frame.dispose();});}
    }
}
