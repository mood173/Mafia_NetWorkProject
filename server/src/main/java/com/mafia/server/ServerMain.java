package com.mafia.server;

import com.mafia.common.protocol.MessageCodec;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.engine.MessageRouter;
import com.mafia.server.engine.WiringCheck;

/**
 * 서버 조립 지점 (Composition Root).
 * 규칙: new 는 여기와 팩토리 안에서만 쓴다. 다른 클래스는 생성자로 받기만 한다. static 싱글턴 금지.
 */
public final class ServerMain {

    public static void main(String[] args) {
        GameConfig config = GameConfig.defaults();
        MessageCodec codec = new MessageCodec();
        MessageRouter router = new MessageRouter();

        // ── 접속 (고경탁) ─────────────────────────────
        // SessionRegistry sessions = new SessionRegistry();
        // router.register(MessageType.LOGIN, new LoginHandler(sessions));

        // ── 로비·방 (강민준) ──────────────────────────
        // RoomRegistry rooms = new RoomRegistry();
        // RoomFactory roomFactory = new RoomFactory(games);
        // router.register(MessageType.ROOM_LIST,    new RoomListHandler(rooms));
        // router.register(MessageType.CREATE_ROOM,  new CreateRoomHandler(rooms, roomFactory));
        // router.register(MessageType.JOIN_ROOM,    new JoinRoomHandler(rooms));
        // router.register(MessageType.JOIN_PRIVATE, new JoinRoomHandler(rooms));
        // router.register(MessageType.READY,        new ReadyHandler(rooms));

        // ── 캐릭터 (고경탁) ───────────────────────────
        // RoleFactory roleFactory = new RoleFactory(config.roleCounts(), List.of(...));
        // CharacterService characters = new DefaultCharacterService(new DefaultNightResolver());

        // ── 판 진행 (강민준) ──────────────────────────
        // GameSessionFactory games = channel -> new GameSession(channel, roleFactory.create(),
        //         characters, new WinChecker(), new VoteCounter(), new PhaseTimer(), config);
        // GameMessageHandler toGame = new GameMessageHandler(rooms);
        // for (MessageType t : MessageType.gameCommands()) router.register(t, toGame);

        // ── 채팅 (고경탁) ─────────────────────────────
        // router.register(MessageType.CHAT, new ChatHandler(rooms));

        WiringCheck.report(router, config);      // 다 채우면 WiringCheck.orFail 로 바꾼다
        // new GameServer(config.port(), codec, router, sessions).start();   // 고경탁
        System.out.println("[ServerMain] 조립 완료. GameServer 연결 전 (포트 " + config.port() + ")");
    }
}
