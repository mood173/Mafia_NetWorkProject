package com.mafia.client;
import com.mafia.client.net.*;
import com.mafia.client.stream.*;
import com.mafia.client.scene.*;
import com.mafia.client.game.*;
import com.mafia.client.demo.*;
import com.mafia.common.protocol.MessageCodec;
import javax.swing.*;

/** 조립 지점. 실제 Socket 통신이 기본이며 --offline만 이전 LOGIN 가짜 연결을 쓴다. */
public final class ClientMain {
    public static void main(String[] args){SwingUtilities.invokeLater(()->assemble(args));}
    private static void assemble(String[] args){
        Connection connection=args.length>0&&args[0].equals("--offline")?new OfflineConnection():new SocketConnection(new MessageCodec(),SwingUtilities::invokeLater);
        GameStream stream=new GameStream(new JFrame("마피넷"));
        var scenes=stream.scenes();var state=stream.state();
        scenes.register(SceneId.MAIN,new MainScene(connection));
        scenes.register(SceneId.LOBBY,new LobbyScene(connection));
        scenes.register(SceneId.ROOM,new RoomScene(state,connection));
        scenes.register(SceneId.GAME,new GameScene(state,connection,new PhaseBanner(state),new VotePanel(state,connection),new DemoAbilityPanel(state,connection),new DemoChatPanel(connection)));
        scenes.register(SceneId.RESULT,new ResultScene(state,connection));
        WiringCheck.orFail(scenes);connection.setListener(stream);stream.start(SceneId.MAIN);
    }
}
