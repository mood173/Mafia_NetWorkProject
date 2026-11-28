package com.mafia.client.demo;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.ClientState;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
/** 채팅 패널. 게임 중에는 살아 있는 사람만 토론·투표 단계에 보낼 수 있다. */
public final class DemoChatPanel extends JPanel implements com.mafia.client.ui.ChatView {
    public JComponent view(){return this;}
    private final JTextArea log=new JTextArea(9,40);
    private final JTextField input=new JTextField(24);
    private final JButton send=new JButton("채팅 보내기");
    public DemoChatPanel(Connection connection){
        setLayout(new BorderLayout());log.setEditable(false);log.setLineWrap(true);log.setWrapStyleWord(true); // 긴 채팅은 줄을 바꿔 보여 준다
        add(new JScrollPane(log));JPanel row=new JPanel();row.add(input);row.add(send);add(row,BorderLayout.SOUTH);
        java.awt.event.ActionListener submit=e->{if(!input.getText().isBlank()){connection.send(Message.of(MessageType.CHAT,input.getText()));input.setText("");}};
        send.addActionListener(submit);input.addActionListener(submit); // 입력창에서 Enter를 눌러도 전송
    }
    public void accept(Message m){if(m.type()==MessageType.CHAT)log.append(m.field(0)+": "+m.field(1)+"\n");}
    public void reset(){log.setText("");}
    public void render(ClientState state){boolean active=state.amAlive()&&(state.phase()==Phase.DAY_TALK||state.phase()==Phase.VOTE);input.setEnabled(active);send.setEnabled(active);}
}
