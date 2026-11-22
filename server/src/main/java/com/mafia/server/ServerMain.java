package com.mafia.server;

import com.mafia.common.protocol.MessageCodec;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.character.night.DefaultCharacterService;
import com.mafia.server.character.role.RoleCatalog;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.engine.MessageRouter;
import com.mafia.server.engine.WiringCheck;
import com.mafia.server.game.GameMessageHandler;
import com.mafia.server.game.GameSessionFactory;
import com.mafia.server.game.ScheduledPhaseTimer;
import com.mafia.server.lobby.LobbyHandlers;
import com.mafia.server.lobby.RoomRegistry;
import com.mafia.server.lobby.RoomService;
import com.mafia.server.net.GameServer;
import com.mafia.server.net.LanAddresses;
import com.mafia.server.net.LoginHandler;
import com.mafia.server.net.SessionRegistry;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 서버 조립 지점: 로그인·로비·대기방·게임. 직업은 character.role, 밤 처리는 character.night의 정식 구현을 쓴다.
 * 실행 인자는 ServerArgs: [주소] [포트] [--demo].
 */
public final class ServerMain {

    /** 접속 후 처리할 메시지 등록. 테스트도 같은 조립을 쓴다. */
    public static MessageRouter router(SessionRegistry sessions, RoomService rooms) {
        MessageRouter router = new MessageRouter();
        router.register(MessageType.LOGIN, new LoginHandler(sessions));
        LobbyHandlers.register(router, rooms);
        // 밤 능력 3종과 투표는 그 사람이 속한 방의 GameSession으로 넘긴다 (단계·생존·직업 검사는 판이 한다).
        GameMessageHandler game = new GameMessageHandler(rooms::gameOf);
        for (MessageType command : MessageType.gameCommands()) router.register(command, game);
        return router;
    }

    public static void main(String[] args) throws Exception {
        ServerArgs options;
        try {
            options = ServerArgs.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }
        GameConfig config = options.config();
        String host = options.host();
        int port = options.port();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            GameSessionFactory games = new GameSessionFactory(config, RoleCatalog.standard(),
                    DefaultCharacterService::new, new ScheduledPhaseTimer(scheduler), new Random());
            RoomService rooms = new RoomService(config, games, new RoomRegistry(new Random()));
            SessionRegistry sessions = new SessionRegistry();
            MessageRouter router = router(sessions, rooms);

            // 클라이언트가 보내는 메시지가 하나라도 처리되지 않으면 시작하지 않는다.
            WiringCheck.orFail(router, config);
            try (GameServer server = new GameServer(host, port, new MessageCodec(), router, sessions, rooms::disconnected)) {
                Thread shutdown = new Thread(server::close, "mafia-shutdown");
                Runtime.getRuntime().addShutdownHook(shutdown);
                try {
                    System.out.println("마피넷 서버: " + host + ":" + server.port());
                    if (options.demo())
                        System.out.printf("시연 모드: 밤 %d초, 낮 결과 %d초, 토론 %d초, 투표 %d초%n", config.nightSeconds(),
                                config.dayResultSeconds(), config.talkSeconds(), config.voteSeconds());
                    // 모든 네트워크 카드로 열었으면 다른 PC가 "서버 주소" 칸에 넣을 주소를 알려 준다.
                    if (host.equals("0.0.0.0"))
                        LanAddresses.guide(LanAddresses.ipv4(), server.port())
                                .forEach(address -> System.out.println("  다른 PC 접속 주소: " + address));
                    server.start();
                } finally {
                    Runtime.getRuntime().removeShutdownHook(shutdown);
                }
            }
        } finally {
            scheduler.shutdownNow();
        }
    }
}
