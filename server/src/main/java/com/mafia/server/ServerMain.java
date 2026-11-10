package com.mafia.server;

import com.mafia.common.protocol.MessageCodec;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.engine.MessageRouter;
import com.mafia.server.engine.WiringCheck;
import com.mafia.server.net.GameServer;
import com.mafia.server.net.LoginHandler;
import com.mafia.server.net.SessionRegistry;

/** Login and transport composition only. Room/game services are supplied by their owners. */
public final class ServerMain {
    public static void main(String[] args) throws Exception {
        GameConfig config=GameConfig.defaults();
        String host=args.length>0?args[0]:"0.0.0.0";
        int port=args.length>1?Integer.parseInt(args[1]):config.port();
        SessionRegistry sessions=new SessionRegistry();
        MessageRouter router=new MessageRouter();
        router.register(MessageType.LOGIN,new LoginHandler(sessions));

        // The full-game check stays in report mode until the other services are integrated.
        WiringCheck.report(router,config);
        try(GameServer server=new GameServer(host,port,new MessageCodec(),router,sessions,session->{})) {
            Thread shutdown=new Thread(server::close,"mafia-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdown);
            try {
                System.out.println("마피넷 로그인·네트워크 서버: "+host+":"+server.port());
                System.out.println("방·채팅·게임 서비스는 연결되지 않았습니다.");
                server.start();
            } finally {
                Runtime.getRuntime().removeShutdownHook(shutdown);
            }
        }
    }
}
