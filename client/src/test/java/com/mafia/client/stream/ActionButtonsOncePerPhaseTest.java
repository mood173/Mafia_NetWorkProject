package com.mafia.client.stream;

import StreamSystem.AppStream;
import StreamSystem.StreamManager;
import com.mafia.client.demo.DemoAbilityPanel;
import com.mafia.client.demo.DemoChatPanel;
import com.mafia.client.game.PhaseBanner;
import com.mafia.client.game.VotePanel;
import com.mafia.client.net.Connection;
import com.mafia.client.net.MessageListener;
import com.mafia.client.scene.GameScene;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.common.protocol.RejectCode;
import com.mafia.common.protocol.Rejection;
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

    private static <T extends Component> T findByType(Container root, Class<T> type) {
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) return type.cast(c);
            if (c instanceof Container child) {
                T found = findByType(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test
    void errorUnlocksActionButtonsAndShowsAliveDeadAndVoteTally() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ClientState state = inGame("MAFIA");
            phase(state, "NIGHT");
            DemoAbilityPanel ability = new DemoAbilityPanel(state, capture);
            VotePanel votePanel = new VotePanel(state, capture);
            GameScene scene = new GameScene(state, capture, new PhaseBanner(state), votePanel, ability, new DemoChatPanel(capture));
            scene.onEnter();

            // 밤 능력 거절(ERROR) 시 버튼 잠금 해제 확인
            JButton use = button(ability, "밤 능력 선택");
            use.doClick();
            assertFalse(use.isEnabled());
            // 채팅이 거절된 것이면 능력 버튼은 잠긴 채로 둔다
            scene.onMessage(Rejection.of("지금은 채팅할 수 없습니다", MessageType.CHAT).toMessage());
            assertFalse(use.isEnabled(), "다른 요청의 거절로는 밤 능력 버튼이 풀리지 않는다");
            // 이미 받아들여진 능력을 또 보낸 거절이면 풀지 않는다
            scene.onMessage(Rejection.of("이미 능력을 사용했습니다", MessageType.MAFIA_ATTACK, RejectCode.ALREADY_DONE).toMessage());
            assertFalse(use.isEnabled(), "ALREADY_DONE이면 잠금 유지");
            scene.onMessage(Rejection.of("같은 팀은 공격할 수 없습니다", MessageType.MAFIA_ATTACK, RejectCode.INVALID_TARGET).toMessage());
            assertTrue(use.isEnabled(), "능력이 거절되면 밤 능력 버튼 잠금 해제");

            // 사망 처리 후 좌측 목록에 생존/사망 표시 확인
            state.apply(Message.of(MessageType.PLAYER_DIED, "userB", "ATTACK"));
            scene.onMessage(Message.of(MessageType.PLAYER_DIED, "userB", "ATTACK"));
            @SuppressWarnings("unchecked")
            JList<String> list = findByType((Container) scene.view(), JList.class);
            assertNotNull(list);
            List<String> items = new ArrayList<>();
            for (int i = 0; i < list.getModel().getSize(); i++) items.add(list.getModel().getElementAt(i));
            assertTrue(items.contains("userA [생존]"));
            assertTrue(items.contains("userB [사망]"));

            // 투표 거절(ERROR) 시 잠금 해제 및 VOTE_RESULT 집계 출력 확인
            phase(state, "VOTE");
            scene.onMessage(Message.of(MessageType.PHASE_CHANGE, "VOTE", "15"));
            JButton voteBtn = button(votePanel, "투표");
            voteBtn.doClick();
            assertFalse(voteBtn.isEnabled());
            scene.onMessage(Rejection.of("채팅은 1~200자입니다", MessageType.CHAT).toMessage());
            assertFalse(voteBtn.isEnabled(), "다른 요청의 거절로는 투표 버튼이 풀리지 않는다");
            scene.onMessage(Rejection.of("투표 대상 오류", MessageType.VOTE, RejectCode.INVALID_TARGET).toMessage());
            assertTrue(voteBtn.isEnabled(), "투표가 거절되면 투표 버튼 잠금 해제");

            scene.onMessage(Message.of(MessageType.VOTE_RESULT, "1-1", "1-1:3,userA:1"));
            JTextArea notice = findByType((Container) scene.view(), JTextArea.class);
            assertNotNull(notice);
            assertTrue(notice.getText().contains("1-1 처형 (1-1:3,userA:1)"));
            scene.onExit();
        });
    }

    @Test
    void streamManagerReturnsToRoomOnlyWhenRequested() {
        StreamManager sm = new StreamManager();
        sm.accept(Message.of(MessageType.LOGIN_OK, "userA"));
        sm.accept(Message.of(MessageType.GAME_START, "userA,userB"));
        sm.accept(Message.of(MessageType.GAME_END, "CITIZEN", "userA:CITIZEN,userB:MAFIA"));
        assertEquals(AppStream.End, sm.app());

        // 다른 사람의 ROOM_STATE로는 결과 화면을 벗어나지 않음
        sm.accept(Message.of(MessageType.ROOM_STATE, "1", "room", "PUBLIC", "userA", "userA:false"));
        assertEquals(AppStream.End, sm.app());

        // 본인이 직접 대기방 복귀 호출 시 FrontDesk로 전환
        sm.returnToRoom();
        assertEquals(AppStream.FrontDesk, sm.app());
    }
}
