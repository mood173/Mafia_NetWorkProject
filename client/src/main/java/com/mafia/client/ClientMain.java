package com.mafia.client;
import com.mafia.client.net.*;
import com.mafia.client.stream.*;
import com.mafia.client.scene.*;
import com.mafia.client.game.*;
import com.mafia.client.demo.*;
import com.mafia.common.protocol.MessageCodec;
import javax.swing.*;

/** 클라이언트를 켜는 곳. 화면 다섯 개를 등록하고 서버 연결을 붙인다. --offline으로 켜면 서버 없이 화면만 시험한다. */
public final class ClientMain {
    public static void main(String[] args){SwingUtilities.invokeLater(()->assemble(args));}
    private static void assemble(String[] args){
        SocketConnection socket=args.length>0&&args[0].equals("--offline")?null:new SocketConnection(new MessageCodec(),SwingUtilities::invokeLater);
        Connection connection=socket==null?new OfflineConnection():socket;
        GameStream stream=new GameStream(new JFrame("마피넷"));
        var scenes=stream.scenes();var state=stream.state();
        scenes.register(SceneId.MAIN,new MainScene(connection));
        scenes.register(SceneId.LOBBY,new LobbyScene(connection));
        // 테스트 봇 버튼은 실제 서버에 연결할 때만 둔다.
        scenes.register(SceneId.ROOM,new RoomScene(state,connection,socket==null?null:com.mafia.client.bot.RoomBotFiller.forRoom(socket,state)));
        scenes.register(SceneId.GAME,new GameScene(state,connection,new PhaseBanner(state),new VotePanel(state,connection),new DemoAbilityPanel(state,connection),new DemoChatPanel(connection)));
        scenes.register(SceneId.RESULT,new ResultScene(state,connection,stream::returnToRoom));
        WiringCheck.orFail(scenes);connection.setListener(stream);stream.start(SceneId.MAIN);
    }
}
