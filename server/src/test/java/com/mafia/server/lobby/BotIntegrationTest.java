package com.mafia.server.lobby;

import com.mafia.client.bot.BotClient;
import com.mafia.client.bot.BotRunner;
import com.mafia.client.bot.RoomBotFiller;
import com.mafia.common.protocol.MessageCodec;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.ServerMain;
import com.mafia.server.demo.DemoCharacterService;
import com.mafia.server.demo.DemoRole;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.GameSessionFactory;
import com.mafia.server.net.GameServer;
import com.mafia.server.net.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/** 실제 TCP로 접속하는 테스트 봇이 지정한 방에 들어가 자동 준비하는지 검사한다. */
final class BotIntegrationTest {
    private final ExecutorService accept = Executors.newSingleThreadExecutor();
    private final List<AutoCloseable> opened = new ArrayList<>();
    private GameServer server;

    @BeforeEach
    void startServer() throws IOException {
        GameConfig config = GameConfig.defaults();
        SessionRegistry sessions = new SessionRegistry();
        RoomService rooms = new RoomService(config, new GameSessionFactory(config, DemoRole.catalog(),
                DemoCharacterService::new, RoomServiceTest.FROZEN, new Random()), new RoomRegistry(new Random()));
        server = new GameServer("127.0.0.1", 0, new MessageCodec(),
                ServerMain.router(sessions, rooms), sessions, rooms::disconnected);
        accept.submit(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
    }

    @AfterEach
    void stopServer() throws Exception {
        for (AutoCloseable c : opened) c.close();
        server.close();
        accept.shutdownNow();
    }

    private LobbyNetworkTest.Client player(String name) throws Exception {
        LobbyNetworkTest.Client c = new LobbyNetworkTest.Client(name, server.port());
        opened.add(c);
        return c;
    }

    /** 방 인원 6명이고 봇("1-n")은 모두 준비 완료인 ROOM_STATE. */
    private static boolean sixWithReadyBots(com.mafia.common.protocol.Message m) {
        String[] entries = m.field(4).split(",");
        return entries.length == 6 && Arrays.stream(entries).filter(e -> e.startsWith("1-")).allMatch(e -> e.endsWith(":true"));
    }

    private List<BotClient> bots(int roomId, int count) throws IOException {
        List<BotClient> bots = BotRunner.start("127.0.0.1", server.port(), roomId, count);
        opened.addAll(bots);
        return bots;
    }

    @Test
    void fourBotsNamedAfterRoomJoinReadyAndStartGameWithTwoPlayers() throws Exception {
        LobbyNetworkTest.Client a = player("userA"), b = player("userB");
        a.send(MessageType.CREATE_ROOM, "테스트방", "PUBLIC");
        a.awaitState("userA:false");
        b.send(MessageType.JOIN_ROOM, "1");
        a.awaitState("userA:false,userB:false");

        List<BotClient> bots = bots(1, 4);
        assertEquals(List.of("1-1", "1-2", "1-3", "1-4"), bots.stream().map(BotClient::name).toList());

        // 봇 입장 순서는 연결마다 다르므로 구성원과 준비 여부만 검사한다.
        a.await(MessageType.ROOM_STATE, m -> {
            List<String> members = Arrays.asList(m.field(4).split(","));
            return members.size() == 6 && members.containsAll(
                    List.of("userA:false", "userB:false", "1-1:true", "1-2:true", "1-3:true", "1-4:true"));
        });

        a.send(MessageType.READY, "true");
        b.send(MessageType.READY, "true");
        assertEquals(6, a.await(MessageType.GAME_START, m -> true).field(0).split(",").length);
        b.await(MessageType.GAME_START, m -> true);
        for (BotClient bot : bots) assertTrue(bot.awaitRole(5000), bot.name() + "가 직업을 받지 못함");
    }

    @Test
    void fillAddsOnlyMissingSeatsWithUnusedNames() throws Exception {
        LobbyNetworkTest.Client a = player("userA");
        a.send(MessageType.CREATE_ROOM, "테스트방", "PUBLIC");
        a.awaitState("userA:false");
        bots(1, 1);
        a.awaitState("userA:false,1-1:true");

        List<BotClient> added = RoomBotFiller.fill("127.0.0.1", server.port(), 1, "", List.of("userA", "1-1"));
        opened.addAll(added);
        assertEquals(List.of("1-2", "1-3", "1-4", "1-5"), added.stream().map(BotClient::name).toList());
        a.await(MessageType.ROOM_STATE, BotIntegrationTest::sixWithReadyBots);

        assertTrue(RoomBotFiller.fill("127.0.0.1", server.port(), 1, "",
                List.of("userA", "1-1", "1-2", "1-3", "1-4", "1-5")).isEmpty(), "가득 찬 방에는 봇을 만들지 않는다");
    }

    @Test
    void fillJoinsPrivateRoomWithCode() throws Exception {
        LobbyNetworkTest.Client a = player("userA");
        a.send(MessageType.CREATE_ROOM, "비밀방", "PRIVATE");
        String code = a.await(MessageType.ROOM_CODE, m -> true).field(0);

        opened.addAll(RoomBotFiller.fill("127.0.0.1", server.port(), 1, code, List.of("userA")));
        // 봇 입장 순서는 매번 다르므로 인원과 준비 여부만 본다.
        a.await(MessageType.ROOM_STATE, BotIntegrationTest::sixWithReadyBots);
    }

    @Test
    void memberWhoJoinedByCodeCanFillPrivateRoomWithBots() throws Exception {
        LobbyNetworkTest.Client host = player("host"), member = player("member");
        host.send(MessageType.CREATE_ROOM, "비밀방", "PRIVATE");
        String code = host.await(MessageType.ROOM_CODE, m -> true).field(0);
        member.send(MessageType.JOIN_PRIVATE, code);
        String memberCode = member.await(MessageType.ROOM_CODE, m -> true).field(0);
        assertEquals(code, memberCode);

        // 방장이 아닌 사람의 화면이 아는 코드로 봇을 채운다 (버튼과 같은 경로)
        opened.addAll(RoomBotFiller.fill("127.0.0.1", server.port(), 1, memberCode, List.of("host", "member")));
        host.await(MessageType.ROOM_STATE, BotIntegrationTest::sixWithReadyBots);
    }

    @Test
    void socketConnectionReportsConnectedServerAddress() throws Exception {
        LobbyNetworkTest.Client a = player("userA");
        assertEquals(server.port(), a.connection.remoteAddress().getPort());
    }

    @Test
    void botForMissingRoomStopsInsteadOfWaitingForever() throws Exception {
        BotClient bot = bots(99, 1).get(0);
        assertTrue(bot.awaitStopped(5000), "없는 방이면 봇이 종료되어야 함");
    }

    @Test
    void secondRunForSameRoomIsRejectedByDuplicateNames() throws Exception {
        LobbyNetworkTest.Client a = player("userA");
        a.send(MessageType.CREATE_ROOM, "테스트방", "PUBLIC");
        bots(1, 1);
        a.awaitState("userA:false,1-1:true");

        BotClient duplicate = bots(1, 1).get(0);
        assertTrue(duplicate.awaitStopped(5000), "같은 이름의 봇은 로그인 거절 후 종료");
    }
}
