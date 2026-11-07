package com.mafia.client;

import com.mafia.client.net.Connection;
import com.mafia.client.net.OfflineConnection;
import com.mafia.client.scene.MainScene;
import com.mafia.client.scene.PlaceholderScene;
import com.mafia.client.stream.GameStream;
import com.mafia.client.stream.SceneId;
import com.mafia.client.stream.SceneRegistry;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * 클라이언트 조립 지점 (Composition Root).
 * 규칙: new 는 여기서만. 씬과 부품은 생성자로 필요한 것을 받는다.
 */
public final class ClientMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClientMain::assemble);
    }

    private static void assemble() {
        Connection connection = new OfflineConnection();
        // Connection connection = new SocketConnection(new MessageCodec());   // 고경탁: 완성되면 이 줄로 교체

        GameStream stream = new GameStream(new JFrame("마피넷"));
        SceneRegistry scenes = stream.scenes();
        scenes.register(SceneId.MAIN,   new MainScene(connection));
        scenes.register(SceneId.LOBBY,  new PlaceholderScene(stream, "로비"));        // → LobbyScene (강민준)
        scenes.register(SceneId.ROOM,   new PlaceholderScene(stream, "대기방"));      // → RoomScene (강민준)
        scenes.register(SceneId.GAME,   new PlaceholderScene(stream, "게임"));        // → GameScene + ChatPanel·AbilityPanel
        scenes.register(SceneId.RESULT, new PlaceholderScene(stream, "결과"));        // → ResultScene (강민준)

        WiringCheck.orFail(scenes);
        connection.setListener(stream);
        stream.start(SceneId.MAIN);
    }
}
