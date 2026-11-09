package com.mafia.client.demo;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.ClientState;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
/** 경탁 채팅 컴포넌트를 위한 별도 시연 구현. */
public final class DemoChatPanel extends JPanel implements com.mafia.client.ui.ChatView {
    public JComponent view(){return this;}
    private final JTextArea log=new JTextArea(9,40);
    private final JTextField input=new JTextField(24);
    private final JButton send=new JButton("채팅 보내기");
    public DemoChatPanel(Connection connection){
        setLayout(new BorderLayout());log.setEditable(false);add(new JScrollPane(log));JPanel row=new JPanel();row.add(input);row.add(send);add(row,BorderLayout.SOUTH);
        send.addActionListener(e->{if(!input.getText().isBlank()){connection.send(Message.of(MessageType.CHAT,input.getText()));input.setText("");}});
    }
    public void accept(Message m){if(m.type()==MessageType.CHAT)log.append(m.field(0)+": "+m.field(1)+"\n");}
    public void reset(){log.setText("");}
    public void render(ClientState state){boolean active=state.amAlive()&&(state.phase()==Phase.DAY_TALK||state.phase()==Phase.VOTE);input.setEnabled(active);send.setEnabled(active);}
}
