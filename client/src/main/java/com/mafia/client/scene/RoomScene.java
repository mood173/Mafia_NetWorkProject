package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.*;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
public final class RoomScene implements Scene {
    private final ClientState state;
    private final JPanel panel=new JPanel(new BorderLayout(10,10));
    private final JLabel title=new JLabel();
    private final JTextArea people=new JTextArea();
    private final JLabel status=new JLabel("6명 모두 준비하면 서버가 게임을 시작합니다.");
    private final JButton ready=new JButton("준비");
    public RoomScene(ClientState state,Connection connection){
        this.state=state;people.setEditable(false);
        JButton leave=new JButton("로비로 나가기");JPanel buttons=new JPanel();buttons.add(ready);buttons.add(leave);buttons.add(status);
        ready.addActionListener(e->connection.send(Message.of(MessageType.READY,Boolean.toString(!state.ready().getOrDefault(state.myName(),false)))));
        leave.addActionListener(e->connection.send(Message.of(MessageType.LEAVE_ROOM)));
        panel.add(title,BorderLayout.NORTH);panel.add(new JScrollPane(people));panel.add(buttons,BorderLayout.SOUTH);
    }
    private void render(){
        title.setText("방 "+state.roomId()+" / "+state.roomName()+" / 방장 "+state.host()+" / 코드 "+state.roomCode());
        people.setText("");state.ready().forEach((name,r)->people.append(name+" : "+(r?"준비 완료":"대기")+"\n"));
        ready.setText(state.ready().getOrDefault(state.myName(),false)?"준비 취소":"준비");
    }
    public JComponent view(){return panel;}public void onEnter(){render();}
    public void onMessage(Message m){render();if(m.type()==MessageType.ERROR)status.setText(m.field(0));}
}
