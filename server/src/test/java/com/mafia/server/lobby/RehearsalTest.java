package com.mafia.server.lobby;

import com.mafia.client.bot.BotClient;
import com.mafia.client.bot.RoomBotFiller;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageCodec;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.ServerMain;
import com.mafia.server.character.night.DefaultCharacterService;
import com.mafia.server.character.role.RoleCatalog;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.GameSessionFactory;
import com.mafia.server.game.ScheduledPhaseTimer;
import com.mafia.server.net.GameServer;
import com.mafia.server.net.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 13주차 리허설의 자동판: 사람 2명(아무것도 하지 않음) + 테스트 봇 4명이 실제 TCP로 한 판을 끝까지 하고,
 * 두 사람이 결과를 받은 뒤 로비로 나간다. 시연 순서(접속 → 방 → 봇 채우기 → 준비 → 게임 → 결과 → 로비) 그대로다.
 */
final class RehearsalTest {

    @Test
    void twoPeopleAndFourBotsPlayAFullGameThenReturnToTheLobby() throws Exception {
        // 밤·투표 1초, 결과·토론 0초. 사람은 행동하지 않으므로 그 몫은 시간 만료로 넘어간다.
        GameConfig config = new GameConfig(0, 6, 1, 0, 0, 1, GameConfig.defaults().roleCounts());
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        SessionRegistry sessions = new SessionRegistry();
        RoomService rooms = new RoomService(config, new GameSessionFactory(config, RoleCatalog.standard(),
                DefaultCharacterService::new, new ScheduledPhaseTimer(scheduler), new Random()), new RoomRegistry(new Random()));
        ExecutorService accept = Executors.newSingleThreadExecutor();
        List<AutoCloseable> opened = new ArrayList<>();

        try (GameServer server = new GameServer("127.0.0.1", 0, new MessageCodec(),
                ServerMain.router(sessions, rooms), sessions, rooms::disconnected)) {
            accept.submit(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            LobbyNetworkTest.Client a = new LobbyNetworkTest.Client("userA", server.port());
            LobbyNetworkTest.Client b = new LobbyNetworkTest.Client("userB", server.port());
            opened.add(a);
            opened.add(b);

            a.send(MessageType.CREATE_ROOM, "리허설", "PUBLIC");
            a.awaitState("userA:false");
            b.send(MessageType.JOIN_ROOM, "1");
            b.awaitState("userA:false,userB:false");

            List<BotClient> bots = RoomBotFiller.fill("127.0.0.1", server.port(), 1, "", List.of("userA", "userB"));
            opened.addAll(bots);
            assertEquals(4, bots.size(), "빈자리 4개를 봇이 채운다");
            a.await(MessageType.ROOM_STATE, m -> m.field(4).split(",").length == 6 && m.field(4).split(":true", -1).length - 1 == 4);
            a.send(MessageType.READY, "true");
            b.send(MessageType.READY, "true");

            Message endA = awaitLong(a, MessageType.GAME_END);
            Message endB = awaitLong(b, MessageType.GAME_END);
            assertEquals(endA, endB, "두 사람이 같은 결과를 받는다");
            assertTrue(List.of("CITIZEN", "MAFIA").contains(endA.field(0)), endA.field(0));
            assertEquals(6, endA.field(1).split(",").length, "직업 공개는 6명 전원");
            for (BotClient bot : bots) assertEquals(0, bot.rejections(), bot.name() + "의 행동이 서버에서 거절됨");

            // 결과 화면의 "방에서 나가 로비로" = LEAVE_ROOM. 서버가 ROOM_LEFT를 보내야 로비로 간다.
            a.send(MessageType.LEAVE_ROOM);
            b.send(MessageType.LEAVE_ROOM);
            a.await(MessageType.ROOM_LEFT, m -> m.field(0).equals("1"));
            b.await(MessageType.ROOM_LEFT, m -> m.field(0).equals("1"));
            a.send(MessageType.ROOM_LIST);
            Message list = a.await(MessageType.ROOM_LIST, m -> true);
            assertEquals("1,리허설,4,false", list.field(0), "봇만 남은 방은 대기 상태로 목록에 남는다");
        } finally {
            for (AutoCloseable c : opened) c.close();
            accept.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    /** 한 판 전체를 기다린다 (봇이 무작위라 몇 라운드 걸릴 수 있다). */
    private static Message awaitLong(LobbyNetworkTest.Client c, MessageType type) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
        while (System.nanoTime() < end) {
            Message m = c.received.poll(100, TimeUnit.MILLISECONDS);
            if (m != null && m.type() == type) return m;
        }
        throw new AssertionError(c.name + ": " + type + "을 90초 안에 받지 못함");
    }
}
