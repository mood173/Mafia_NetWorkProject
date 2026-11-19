package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.*;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
public final class ResultScene implements Scene {
    private final JPanel panel=new JPanel(new BorderLayout());private final JLabel result=new JLabel();
    private final JTextArea roles=new JTextArea();private final ClientState state;
    public ResultScene(ClientState state,Connection connection){
        this.state=state;roles.setEditable(false);panel.add(result,BorderLayout.NORTH);panel.add(new JScrollPane(roles));
        JButton back=new JButton("방에서 나가 로비로");back.addActionListener(e->connection.send(Message.of(MessageType.LEAVE_ROOM)));panel.add(back,BorderLayout.SOUTH);
    }
    public JComponent view(){return panel;}public void onEnter(){render();}
    private void render(){result.setText("승리 팀: "+state.winner());roles.setText(state.reveal().replace(",","\n"));}
    public void onMessage(Message message){render();}
}
