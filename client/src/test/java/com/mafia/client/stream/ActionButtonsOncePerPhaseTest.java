package com.mafia.client.stream;

import com.mafia.client.demo.DemoAbilityPanel;
import com.mafia.client.game.VotePanel;
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

/** 밤 능력·투표 버튼은 한 단계에 한 번만 누를 수 있다. ClientState.apply가 패키지 전용이라 이 패키지에 둔다. */
final class ActionButtonsOncePerPhaseTest {
    private final List<Message> sent = new ArrayList<>();
    private final Connection capture = new Connection() {
        public void connect(String host, int port) { }
        public void send(Message message) { sent.add(message); }
        public void setListener(MessageListener listener) { }
        public void close() { }
    };

    private static JButton button(Container root, String text) {
        JButton found = find(root, text);
        if (found == null) throw new AssertionError("버튼 없음: " + text);
        return found;
    }

    private static JButton find(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && text.equals(b.getText())) return b;
            if (c instanceof Container child) {
                JButton found = find(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static ClientState inGame(String role) {
        ClientState state = new ClientState();
        state.apply(Message.of(MessageType.LOGIN_OK, "userA"));
        state.apply(Message.of(MessageType.GAME_START, "userA,userB,1-1"));
        state.apply(Message.of(MessageType.ROLE, role));
        return state;
    }

    private static void phase(ClientState state, String phase) {
        state.apply(Message.of(MessageType.PHASE_CHANGE, phase, "30"));
    }

    @Test
    void nightAbilityCanBeUsedOncePerNight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientState state = inGame("POLICE");
            phase(state, "NIGHT");
            DemoAbilityPanel panel = new DemoAbilityPanel(state, capture);
            panel.render();
            JButton use = button(panel, "밤 능력 선택");
            assertTrue(use.isEnabled());

            use.doClick();
            assertEquals(MessageType.POLICE_CHECK, sent.get(0).type());
            assertFalse(use.isEnabled(), "사용 직후 비활성화");
            panel.render(); // 같은 밤에 다른 메시지가 와도 유지
            assertFalse(use.isEnabled());

            phase(state, "DAY_RESULT"); panel.render();
            phase(state, "NIGHT"); panel.render();
            assertTrue(use.isEnabled(), "다음 밤에는 다시 사용 가능");
        });
    }

    @Test
    void voteOrAbstainCanBeChosenOncePerVote() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientState state = inGame("CITIZEN");
            phase(state, "VOTE");
            VotePanel panel = new VotePanel(state, capture);
            panel.render();
            JButton vote = button(panel, "투표"), abstain = button(panel, "기권");

            vote.doClick();
            assertEquals(MessageType.VOTE, sent.get(0).type());
            assertFalse(vote.isEnabled() || abstain.isEnabled(), "투표 직후 투표·기권 모두 비활성화");
            panel.render();
            assertFalse(vote.isEnabled() || abstain.isEnabled());

            phase(state, "NIGHT"); panel.render();
            phase(state, "VOTE"); panel.render();
            assertTrue(vote.isEnabled() && abstain.isEnabled(), "다음 투표에는 다시 가능");

            abstain.doClick();
            assertEquals(Message.of(MessageType.VOTE, ""), sent.get(1));
            assertFalse(vote.isEnabled() || abstain.isEnabled(), "기권 직후에도 비활성화");
        });
    }
}
