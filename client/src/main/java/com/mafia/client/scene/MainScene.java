package com.mafia.client.scene;

import com.mafia.client.net.Connection;
import com.mafia.client.stream.Scene;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.GridLayout;
import java.io.IOException;

/** 메인: 서버 주소·포트·닉네임 입력 → 들어가기. LOGIN_OK 가 오면 GameStream 이 로비로 넘긴다. */
public final class MainScene implements Scene {

    private final Connection connection;
    private final JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
    private final JTextField host = new JTextField("localhost");
    private final JTextField port = new JTextField("5000");
    private final JTextField name = new JTextField();
    private final JLabel status = new JLabel(" ");

    public MainScene(Connection connection) {
        this.connection = connection;
        JButton enter = new JButton("들어가기");
        enter.addActionListener(e -> enter());
        panel.add(new JLabel("서버 주소")); panel.add(host);
        panel.add(new JLabel("포트"));      panel.add(port);
        panel.add(new JLabel("닉네임"));    panel.add(name);
        panel.add(status);                  panel.add(enter);
    }

    private void enter() {
        try {
            connection.connect(host.getText().trim(), Integer.parseInt(port.getText().trim()));
            connection.send(Message.of(MessageType.LOGIN, name.getText().trim()));
        } catch (IOException | NumberFormatException e) {
            status.setText("연결 실패: " + e.getMessage());
        }
    }

    @Override
    public JComponent view() { return panel; }

    @Override
    public void onMessage(Message message) {
        if (message.type() == MessageType.ERROR) status.setText(message.field(0));
    }
}
