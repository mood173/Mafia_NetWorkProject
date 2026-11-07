package com.mafia.client.scene;

import com.mafia.client.stream.GameStream;
import com.mafia.client.stream.Scene;
import com.mafia.common.protocol.Message;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;

/**
 * 아직 만들지 않은 씬 자리. 받은 메시지를 그대로 찍어서 흐름만 확인한다.
 * LobbyScene, RoomScene, GameScene, ResultScene 을 만들면 ClientMain 에서 바꿔 끼운다.
 */
public final class PlaceholderScene implements Scene {

    private final GameStream stream;
    private final JPanel panel = new JPanel(new BorderLayout());
    private final JTextArea log = new JTextArea();

    public PlaceholderScene(GameStream stream, String title) {
        this.stream = stream;
        log.setEditable(false);
        panel.add(new JLabel(title + " (임시 화면)"), BorderLayout.NORTH);
        panel.add(new JScrollPane(log), BorderLayout.CENTER);
    }

    @Override
    public JComponent view() { return panel; }

    @Override
    public void onEnter() {
        log.append("들어옴 · 내 이름=" + stream.state().myName() + "\n");
    }

    @Override
    public void onMessage(Message message) {
        log.append(message.type() + " " + message.fields() + "\n");
    }
}
