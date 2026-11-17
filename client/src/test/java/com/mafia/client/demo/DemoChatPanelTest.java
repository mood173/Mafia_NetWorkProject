package com.mafia.client.demo;

import com.mafia.client.net.Connection;
import com.mafia.client.net.MessageListener;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class DemoChatPanelTest {

    @Test
    void enterInInputSendsChatAndClearsInput() throws Exception {
        List<Message> sent = new ArrayList<>();
        Connection capture = new Connection() {
            public void connect(String host, int port) { }
            public void send(Message message) { sent.add(message); }
            public void setListener(MessageListener listener) { }
            public void close() { }
        };
        SwingUtilities.invokeAndWait(() -> {
            DemoChatPanel panel = new DemoChatPanel(capture);
            JTextField input = findInput(panel);

            input.setText("저는 1-3이 의심됩니다");
            input.postActionEvent(); // JTextField에서 Enter를 누른 것과 같은 이벤트
            assertEquals(List.of(Message.of(MessageType.CHAT, "저는 1-3이 의심됩니다")), sent);
            assertEquals("", input.getText());

            input.setText("   ");
            input.postActionEvent();
            assertEquals(1, sent.size(), "빈 채팅은 보내지 않는다");
        });
    }

    private static JTextField findInput(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField field) return field;
            if (c instanceof Container child) {
                JTextField found = findInput(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
