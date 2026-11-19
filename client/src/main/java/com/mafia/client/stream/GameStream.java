package com.mafia.client.stream;
import StreamSystem.AppStream;
import StreamSystem.StreamManager;
import com.mafia.client.net.MessageListener;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.CardLayout;

/** 사용자의 StreamManager가 전이를 정하고 이 클래스는 Swing 씬 생명주기만 실행한다. */
public final class GameStream implements MessageListener {
    private final JFrame frame;
    private final CardLayout cards=new CardLayout();
    private final JPanel root=new JPanel(cards);
    private final SceneRegistry scenes=new SceneRegistry();
    private final ClientState state=new ClientState();
    private final StreamManager flow=new StreamManager();
    private SceneId current;
    public GameStream(JFrame frame){this.frame=frame;}
    public SceneRegistry scenes(){return scenes;} public ClientState state(){return state;}
    public SceneId current(){return current;} public StreamManager manager(){return flow;}
    public void start(SceneId first) {
        requireEdt();
        scenes.all().forEach((id,scene)->root.add(scene.view(),id.name()));
        frame.setContentPane(root); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(960,720); frame.setLocationRelativeTo(null); go(first); frame.setVisible(true);
    }
    public void go(SceneId next) {
        requireEdt(); Scene scene=scenes.get(next);
        if(next==current)return;
        if(current!=null)scenes.get(current).onExit();
        current=next; cards.show(root,next.name()); scene.onEnter();
    }
    private SceneId sceneFor(AppStream app) {
        return switch(app) {case main->SceneId.MAIN;case Lobby->SceneId.LOBBY;case FrontDesk->SceneId.ROOM;case GameScene->SceneId.GAME;case End->SceneId.RESULT;};
    }
    public void onMessage(Message message) {
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->onMessage(message));return;}
        try {
            state.apply(message); SceneId next=sceneFor(flow.accept(message)); go(next);
            scenes.get(current).onMessage(message);
        } catch(IllegalArgumentException e) {
            if(current!=null)scenes.get(current).onMessage(Message.of(MessageType.ERROR,"서버 메시지 형식 오류: "+e.getMessage()));
        }
    }
    public void onDisconnected() {
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(this::onDisconnected);return;}
        flow.disconnected();state.reset();go(SceneId.MAIN);
        scenes.get(current).onMessage(Message.of(MessageType.ERROR,"서버 연결이 끊겼습니다"));
    }
    private static void requireEdt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("EDT에서 호출해야 함");}
}
