package com.mafia.server.lobby;

import com.mafia.client.bot.BotClient;
import com.mafia.client.bot.RoomBotFiller;
import com.mafia.common.model.RoleName;
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

/** 실제 타이머로 진행되는 판에서 봇이 밤 능력·투표를 보내고 서버가 받아들이는지 검사한다. */
final class BotGameTest {

    @Test
    void botsActAtNightAndVoteSoTheGameMovesOn() throws Exception {
        // 빠른 진행: 밤 2초, 결과·토론 0초, 투표 2초
        GameConfig config = new GameConfig(0, 6, 2, 0, 0, 2, GameConfig.defaults().roleCounts());
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
            opened.add(a);
            a.send(MessageType.CREATE_ROOM, "봇게임", "PUBLIC");
            a.awaitState("userA:false");

            List<BotClient> bots = RoomBotFiller.fill("127.0.0.1", server.port(), 1, "", List.of("userA"));
            opened.addAll(bots);
            a.await(MessageType.ROOM_STATE, m -> m.field(4).split(",").length == 6 && m.field(4).indexOf(":false") == m.field(4).lastIndexOf(":false"));
            a.send(MessageType.READY, "true");

            a.await(MessageType.GAME_START, m -> true);
            a.await(MessageType.SYSTEM_CHAT, m -> m.field(0).equals("[SYSTEM]: 밤이 찾아왔습니다."));
            a.await(MessageType.NIGHT_RESULT, m -> true);
            a.await(MessageType.SYSTEM_CHAT, m -> m.field(0).equals("[SYSTEM]: 아침이 밝았습니다."));
            Message vote = a.await(MessageType.VOTE_RESULT, m -> true);
            assertFalse(vote.field(1).isEmpty(), "봇이 투표해서 득표가 있어야 함");

            // 투표 뒤에는 다음 밤으로 가거나 (처형으로 승패가 나면) 게임이 끝난다.
            Message next = awaitAny(a, m -> m.type() == MessageType.GAME_END
                    || m.type() == MessageType.PHASE_CHANGE && m.field(0).equals("NIGHT"));
            assertNotNull(next);
            for (BotClient bot : bots) {
                assertTrue(List.of(RoleName.values()).stream().anyMatch(r -> r.name().equals(bot.role())), bot.name());
                assertEquals(0, bot.rejections(), bot.name() + "의 행동이 서버에서 거절됨");
            }
        } finally {
            for (AutoCloseable c : opened) c.close();
            accept.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    @Test
    void botsReadyAgainAfterTheGameEndsSoTheSameRoomCanStartOver() throws Exception {
        // 밤을 길게 두어 마피아가 나가기 전에 판이 움직이지 않게 한다.
        GameConfig config = new GameConfig(0, 6, 30, 0, 0, 30, GameConfig.defaults().roleCounts());
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
            opened.add(a);
            a.send(MessageType.CREATE_ROOM, "다시하기", "PUBLIC");
            a.awaitState("userA:false");
            List<BotClient> bots = RoomBotFiller.fill("127.0.0.1", server.port(), 1, "", List.of("userA"));
            opened.addAll(bots);
            a.await(MessageType.ROOM_STATE, m -> m.field(4).split(",").length == 6 && count(m.field(4), ":true") == 5);
            a.send(MessageType.READY, "true");

            // 마피아가 나가면 시민 승리로 판이 바로 끝난다.
            boolean iAmMafia = a.await(MessageType.ROLE, m -> true).field(0).equals("MAFIA");
            int readyBots;
            if (iAmMafia) {
                a.send(MessageType.LEAVE_ROOM);
                a.await(MessageType.ROOM_LEFT, m -> true);
                a.send(MessageType.JOIN_ROOM, "1"); // 끝난 방에 다시 들어갈 수 있다
                readyBots = 5;
            } else {
                for (BotClient bot : bots) assertTrue(bot.awaitRole(5000), bot.name());
                bots.stream().filter(b -> b.role().equals("MAFIA")).findFirst().orElseThrow().close();
                a.await(MessageType.GAME_END, m -> m.field(0).equals("CITIZEN"));
                readyBots = 4;
            }
            Message waiting = a.await(MessageType.ROOM_STATE,
                    m -> m.field(4).contains("userA:false") && count(m.field(4), ":true") == readyBots);
            assertEquals(readyBots + 1, waiting.field(4).split(",").length, waiting.field(4));
        } finally {
            for (AutoCloseable c : opened) c.close();
            accept.shutdownNow();
            scheduler.shutdownNow();
        }
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    private static Message awaitAny(LobbyNetworkTest.Client c, java.util.function.Predicate<Message> condition)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            Message m = c.received.poll(100, TimeUnit.MILLISECONDS);
            if (m != null && condition.test(m)) return m;
        }
        throw new AssertionError("다음 밤 또는 게임 종료를 받지 못함");
    }
}
