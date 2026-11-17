package com.mafia.client.stream;

import com.mafia.client.net.Connection;
import com.mafia.client.net.MessageListener;
import com.mafia.client.scene.RoomScene;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** ClientState.apply가 패키지 전용이라 같은 패키지에 둔다. */
final class RoomSceneBotButtonTest {
    private static final Connection NO_NETWORK = new Connection() {
        public void connect(String host, int port) { }
        public void send(Message message) { }
        public void setListener(MessageListener listener) { }
        public void close() { }
    };

    private static List<JButton> buttons(Container root) {
        List<JButton> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JScrollBar) continue; // 스크롤바 화살표도 JButton이라 제외
            if (c instanceof JButton b) found.add(b);
            if (c instanceof Container child) found.addAll(buttons(child));
        }
        return found;
    }

    private static List<String> labels(Container root) {
        List<String> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (c instanceof JLabel l) found.add(l.getText());
            if (c instanceof Container child) found.addAll(labels(child));
        }
        return found;
    }

    private static Message roomState(String members) {
        return Message.of(MessageType.ROOM_STATE, "1", "테스트방", "PUBLIC", "userA", members);
    }

    @Test
    void botButtonSitsNextToLeaveAndRunsActionUntilRoomIsFull() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            ClientState state = new ClientState();
            state.apply(Message.of(MessageType.LOGIN_OK, "userA"));
            state.apply(roomState("userA:false,userB:false"));
            RoomScene scene = new RoomScene(state, NO_NETWORK, calls::incrementAndGet);
            scene.onEnter();

            List<JButton> all = buttons(scene.view());
            assertEquals(List.of("준비", "로비로 나가기", "테스트 봇 추가"), all.stream().map(JButton::getText).toList());
            JButton add = all.get(2);
            assertTrue(add.isEnabled());
            add.doClick();
            assertEquals(1, calls.get());
            assertFalse(add.isEnabled(), "봇이 들어오는 동안 중복 클릭 방지");

            Message full = roomState("userA:false,userB:false,1-1:true,1-2:true,1-3:true,1-4:true");
            state.apply(full);
            scene.onMessage(full);
            assertFalse(add.isEnabled(), "6명이면 비활성화");
            assertTrue(labels(scene.view()).contains("6명 모두 준비하면 서버가 게임을 시작합니다."),
                    "다 채워지면 '부르는 중' 안내를 원래 문구로 되돌린다");
        });
    }

    @Test
    void originalConstructorKeepsOriginalButtons() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RoomScene scene = new RoomScene(new ClientState(), NO_NETWORK);
            assertEquals(List.of("준비", "로비로 나가기"), buttons(scene.view()).stream().map(JButton::getText).toList());
        });
    }
}
