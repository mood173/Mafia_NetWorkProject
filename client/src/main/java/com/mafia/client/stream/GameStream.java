package com.mafia.client.stream;

import com.mafia.client.net.MessageListener;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.util.EnumMap;
import java.util.Map;

/**
 * 게임 스트림 관리자. 메인 → 로비 → 대기방 → 게임 → 결과까지 씬 전환을 여기 한 곳에서만 한다.
 * 서버 메시지: 상태 갱신 → (필요하면) 자동 전환 → 현재 씬에 전달.
 */
public final class GameStream implements MessageListener {

    private final JFrame frame;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final SceneRegistry scenes = new SceneRegistry();
    private final ClientState state = new ClientState();
    private final Map<MessageType, SceneId> autoTransitions = new EnumMap<>(MessageType.class);
    private SceneId current;

    public GameStream(JFrame frame) {
        this.frame = frame;
        autoTransitions.put(MessageType.LOGIN_OK, SceneId.LOBBY);
        autoTransitions.put(MessageType.GAME_START, SceneId.GAME);
        autoTransitions.put(MessageType.GAME_END, SceneId.RESULT);
    }

    public SceneRegistry scenes() { return scenes; }
    public ClientState state() { return state; }
    public SceneId current() { return current; }

    /** 등록이 끝난 뒤 한 번 부른다. */
    public void start(SceneId first) {
        scenes.all().forEach((id, scene) -> root.add(scene.view(), id.name()));
        frame.setContentPane(root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 640);
        frame.setLocationRelativeTo(null);
        go(first);
        frame.setVisible(true);
    }

    public void go(SceneId next) {
        if (next == current) return;
        if (current != null) scenes.get(current).onExit();
        current = next;
        cards.show(root, next.name());
        scenes.get(next).onEnter();
    }

    @Override
    public void onMessage(Message message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onMessage(message));
            return;
        }
        state.apply(message);
        SceneId next = autoTransitions.get(message.type());
        if (next != null) go(next);
        scenes.get(current).onMessage(message);
    }

    @Override
    public void onDisconnected() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onDisconnected);
            return;
        }
        go(SceneId.MAIN);
        scenes.get(SceneId.MAIN).onMessage(Message.of(MessageType.ERROR, "서버 연결이 끊겼습니다"));
    }
}
