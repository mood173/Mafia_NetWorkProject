package com.mafia.client.bot;

import com.mafia.client.net.MessageListener;
import com.mafia.client.net.SocketConnection;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageCodec;
import com.mafia.common.protocol.MessageType;

import com.mafia.common.model.Phase;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 실제 TCP로 접속하는 테스트 플레이어 (게임 기능이 아닌 테스트 도구).
 * 이름은 "방번호-순번"(예: 101-1)이며 로그인 → 지정한 방 입장 → 자동 준비를 한다.
 * 게임이 시작되면 밤에는 직업 능력을, 투표 단계에는 투표를 무작위 대상에게 보낸다. 판이 끝나면 다시 준비한다.
 * 서버는 일반 클라이언트와 똑같이 검사하므로 정원·중복 이름 규칙을 그대로 따른다. Swing은 쓰지 않는다.
 */
public final class BotClient implements MessageListener, AutoCloseable {
    private final String name;
    private final int roomId;
    private final String code;
    private final SocketConnection connection = new SocketConnection(new MessageCodec());
    private final CountDownLatch stopped = new CountDownLatch(1);
    private final CountDownLatch roleReceived = new CountDownLatch(1);
    private final RandomBotStrategy strategy = new RandomBotStrategy(new Random());
    private final AtomicInteger rejections = new AtomicInteger();
    private volatile String role = "";
    private boolean joined; // 수신 스레드에서만 읽고 쓴다.

    public BotClient(int roomId, int number) {
        this(roomId, number, "");
    }

    /** code가 있으면 비공개방 코드로 입장한다. 이름은 여전히 방번호-순번. */
    public BotClient(int roomId, int number, String code) {
        this.roomId = roomId;
        this.code = code;
        this.name = roomId + "-" + number;
        connection.setListener(this);
    }

    public String name() { return name; }

    /** 서버가 개인 메시지로 보낸 직업. 받기 전에는 빈 문자열. */
    public String role() { return role; }

    public void connect(String host, int port) throws IOException {
        connection.connect(host, port);
        connection.send(Message.of(MessageType.LOGIN, name));
    }

    @Override
    public void onMessage(Message message) {
        switch (message.type()) {
            case LOGIN_OK -> connection.send(code.isEmpty()
                    ? Message.of(MessageType.JOIN_ROOM, Integer.toString(roomId))
                    : Message.of(MessageType.JOIN_PRIVATE, code));
            case ROOM_STATE -> {
                boolean inRoom = Arrays.stream(message.field(4).split(",")).anyMatch(e -> e.startsWith(name + ":"));
                if (inRoom && !joined) {
                    joined = true;
                    log(roomId + "번 방 입장, 준비 완료 전송");
                    connection.send(Message.of(MessageType.READY, "true"));
                }
            }
            case ROLE -> {
                role = message.field(0);
                log("직업 " + role);
                roleReceived.countDown();
            }
            case GAME_STATE -> {
                // GAME_STATE는 단계가 바뀔 때마다 오며 단계와 생존자 명단을 함께 준다.
                List<String> alive = message.field(1).isEmpty() ? List.of() : List.of(message.field(1).split(","));
                strategy.act(name, role, Phase.valueOf(message.field(0)), alive).ifPresent(action -> {
                    log(action.type() + " → " + action.field(0));
                    connection.send(action);
                });
            }
            case GAME_END -> {
                // 판이 끝나도 방에 남는다. 서버가 방을 대기 상태로 돌리므로 바로 다시 준비해 다음 판을 기다린다.
                log("게임 종료 (" + message.field(0) + " 승리), 다시 준비 전송");
                connection.send(Message.of(MessageType.READY, "true"));
            }
            case ERROR -> {
                log("서버 거절: " + message.field(0));
                // 로그인·입장 단계의 거절은 다시 시도할 방법이 없으므로 종료한다.
                if (!joined) close();
                else rejections.incrementAndGet();
            }
            default -> { }
        }
    }

    @Override
    public void onDisconnected() {
        log("연결 종료");
        stopped.countDown();
    }

    /** 방에 들어간 뒤 서버가 거절한 행동 수. 봇이 규칙에 맞게 행동하는지 확인할 때 쓴다. */
    public int rejections() { return rejections.get(); }

    public boolean awaitRole(long millis) throws InterruptedException {
        return roleReceived.await(millis, TimeUnit.MILLISECONDS);
    }

    public boolean awaitStopped(long millis) throws InterruptedException {
        return stopped.await(millis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        connection.close();
        stopped.countDown();
    }

    private void log(String text) {
        System.out.println("[봇 " + name + "] " + text);
    }
}
