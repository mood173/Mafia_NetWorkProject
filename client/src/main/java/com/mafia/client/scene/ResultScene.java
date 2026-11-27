package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.*;
import com.mafia.client.game.Words;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
public final class ResultScene implements Scene {
    private final JPanel panel=new JPanel(new BorderLayout());private final JLabel result=new JLabel();
    private final JTextArea roles=new JTextArea();private final ClientState state;
    public ResultScene(ClientState state,Connection connection){this(state,connection,null);}
    public ResultScene(ClientState state,Connection connection,Runnable onReturnToRoom){
        this.state=state;roles.setEditable(false);panel.add(result,BorderLayout.NORTH);panel.add(new JScrollPane(roles));
        JPanel buttons=new JPanel();
        if(onReturnToRoom!=null){
            JButton again=new JButton("대기방으로 돌아가기");
            again.addActionListener(e->{onReturnToRoom.run();connection.send(Message.of(MessageType.READY,"false"));});
            buttons.add(again);
        }
        JButton back=new JButton("방에서 나가 로비로");back.addActionListener(e->connection.send(Message.of(MessageType.LEAVE_ROOM)));buttons.add(back);
        panel.add(buttons,BorderLayout.SOUTH);
    }
    public JComponent view(){return panel;}public void onEnter(){render();}
    private void render(){
        result.setText(state.winner().isEmpty()?"":Words.team(state.winner())+" 승리");
        StringBuilder lines=new StringBuilder();
        if(!state.reveal().isEmpty())for(String entry:state.reveal().split(",")){
            String[] pair=entry.split(":",2);lines.append(pair[0]).append(" : ").append(pair.length==2?Words.role(pair[1]):"").append("\n");
        }
        roles.setText(lines.toString());
    }
    public void onMessage(Message message){render();}
}
