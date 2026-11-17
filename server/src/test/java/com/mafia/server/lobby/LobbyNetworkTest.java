package com.mafia.server.lobby;

import com.mafia.client.net.MessageListener;
import com.mafia.client.net.SocketConnection;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageCodec;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.ServerMain;
import com.mafia.server.demo.DemoCharacterService;
import com.mafia.server.demo.DemoRole;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.GameSessionFactory;
import com.mafia.server.net.GameServer;
import com.mafia.server.net.SessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** ServerMain과 같은 조립으로 실제 TCP 위에서 방 생성·입장·준비·퇴장·게임 시작을 검사한다. */
final class LobbyNetworkTest {

    static final class Client implements MessageListener, AutoCloseable {
        final String name;
        final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
        final SocketConnection connection = new SocketConnection(new MessageCodec());

        Client(String name, int port) throws Exception {
            this.name = name;
            connection.setListener(this);
            connection.connect("127.0.0.1", port);
            send(MessageType.LOGIN, name);
            await(MessageType.LOGIN_OK, m -> true);
        }

        public void onMessage(Message m) { received.add(m); }
        public void onDisconnected() { }
        void send(MessageType type, String... fields) { connection.send(Message.of(type, fields)); }

        /** 조건에 맞는 메시지가 올 때까지 기다린다. 그 전 메시지는 버린다. */
        Message await(MessageType type, Predicate<Message> condition) throws InterruptedException {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < end) {
                Message m = received.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && m.type() == type && condition.test(m)) return m;
            }
            throw new AssertionError(name + ": " + type + " 메시지를 받지 못함");
        }

        Message awaitState(String members) throws InterruptedException {
            return await(MessageType.ROOM_STATE, m -> m.field(4).equals(members));
        }

        public void close() { connection.close(); }
    }

    @Test
    void serverHandlesEveryClientMessageType() {
        GameConfig config = GameConfig.defaults();
        RoomService rooms = new RoomService(config, new GameSessionFactory(config, DemoRole.catalog(),
                DemoCharacterService::new, RoomServiceTest.FROZEN, new Random()), new RoomRegistry(new Random()));
        assertEquals(List.of(), com.mafia.server.engine.WiringCheck.problems(
                ServerMain.router(new SessionRegistry(), rooms), config), "C→S 메시지 전부 핸들러 등록");
    }

    @Test
    void twoPlayersShareReadyStateAndSixReadyPlayersEnterTheGame() throws Exception {
        GameConfig config = GameConfig.defaults();
        SessionRegistry sessions = new SessionRegistry();
        RoomService rooms = new RoomService(config, new GameSessionFactory(config, DemoRole.catalog(),
                DemoCharacterService::new, RoomServiceTest.FROZEN, new Random()), new RoomRegistry(new Random()));
        ExecutorService accept = Executors.newSingleThreadExecutor();
        List<Client> clients = new ArrayList<>();

        try (GameServer server = new GameServer("127.0.0.1", 0, new MessageCodec(),
                ServerMain.router(sessions, rooms), sessions, rooms::disconnected)) {
            accept.submit(() -> { try { server.start(); } catch (IOException e) { throw new UncheckedIOException(e); } });
            for (int i = 0; i < 6; i++) clients.add(new Client("user" + i, server.port()));
            Client a = clients.get(0), b = clients.get(1);

            a.send(MessageType.CREATE_ROOM, "테스트방", "PUBLIC");
            a.awaitState("user0:false");

            b.send(MessageType.ROOM_LIST);
            assertEquals("1,테스트방,1,false", b.await(MessageType.ROOM_LIST, m -> true).field(0));
            b.send(MessageType.JOIN_ROOM, "1");
            a.awaitState("user0:false,user1:false");

            a.send(MessageType.READY, "true");
            b.awaitState("user0:true,user1:false");

            // 연결이 끊긴 사용자는 GameServer 종료 콜백으로 방에서 빠진다.
            Client temp = new Client("temp", server.port());
            temp.send(MessageType.JOIN_ROOM, "1");
            b.awaitState("user0:true,user1:false,temp:false");
            temp.close();
            assertEquals("temp", b.await(MessageType.PLAYER_LEAVE, m -> true).field(0));
            b.awaitState("user0:true,user1:false");

            // 연결마다 도착 순서가 달라지므로 한 명씩 입장을 확인해 참가자 순서를 고정한다.
            for (int i = 2; i < 6; i++) {
                Client c = clients.get(i);
                c.send(MessageType.JOIN_ROOM, "1");
                c.await(MessageType.ROOM_STATE, m -> m.field(4).contains(c.name + ":"));
            }
            a.awaitState("user0:true,user1:false,user2:false,user3:false,user4:false,user5:false");
            for (int i = 1; i < 6; i++) clients.get(i).send(MessageType.READY, "true");

            for (Client c : clients) {
                assertEquals("user0,user1,user2,user3,user4,user5",
                        c.await(MessageType.GAME_START, m -> true).field(0));
                c.await(MessageType.ROLE, m -> true);
            }

            Client late = new Client("late", server.port());
            clients.add(late);
            late.send(MessageType.JOIN_ROOM, "1");
            late.await(MessageType.ERROR, m -> m.field(0).contains("게임"));
        } finally {
            clients.forEach(Client::close);
            accept.shutdownNow();
        }
    }
}
