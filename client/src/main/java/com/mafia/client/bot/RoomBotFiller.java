package com.mafia.client.bot;

import com.mafia.client.net.SocketConnection;
import com.mafia.client.stream.ClientState;

import javax.swing.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 대기방의 "테스트 봇 추가" 버튼용: 빈자리 수만큼 테스트 봇을 실제 TCP로 접속시킨다.
 * 봇은 누른 클라이언트 프로세스 안에서 돌며, 그 클라이언트가 종료되면 함께 끊긴다.
 */
public final class RoomBotFiller {
    public static final int ROOM_CAPACITY = 6;

    private RoomBotFiller() {
    }

    /**
     * 빈자리만큼 봇을 만든다. 이름은 방에서 아직 쓰지 않는 방번호-n 중 작은 번호부터.
     * code가 비어 있으면 공개방 번호로, 있으면 비공개방 코드로 입장한다.
     */
    public static List<BotClient> fill(String host, int port, int roomId, String code, Collection<String> members)
            throws IOException {
        Set<String> taken = Set.copyOf(members);
        List<BotClient> bots = new ArrayList<>();
        try {
            for (int number = 1; taken.size() + bots.size() < ROOM_CAPACITY; number++) {
                if (taken.contains(roomId + "-" + number)) continue;
                BotClient bot = new BotClient(roomId, number, code);
                bots.add(bot);
                bot.connect(host, port);
            }
        } catch (IOException | RuntimeException e) {
            bots.forEach(BotClient::close);
            throw e;
        }
        return bots;
    }

    /** 버튼 동작: 화면 스레드에서 현재 방 정보를 읽고, 접속은 백그라운드에서 한다. */
    public static Runnable forRoom(SocketConnection connection, ClientState state) {
        return () -> {
            InetSocketAddress server = connection.remoteAddress();
            if (server == null || state.roomId().isEmpty()) return;
            int roomId = Integer.parseInt(state.roomId());
            String code = state.roomCode();
            List<String> members = List.copyOf(state.ready().keySet());
            Thread worker = new Thread(() -> {
                try {
                    fill(server.getHostString(), server.getPort(), roomId, code, members);
                } catch (IOException | RuntimeException e) {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(null, "테스트 봇 접속 실패: " + e.getMessage()));
                }
            }, "test-bot-filler");
            worker.setDaemon(true);
            worker.start();
        };
    }
}
