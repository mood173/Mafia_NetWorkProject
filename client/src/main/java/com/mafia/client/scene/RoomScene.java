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
    private static final String GUIDE="6명 모두 준비하면 서버가 게임을 시작합니다.",CALLING_BOTS="테스트 봇을 부르는 중…";
    private final JLabel status=new JLabel(GUIDE);
    private final JButton ready=new JButton("준비");
    private final JButton addBots=new JButton("테스트 봇 추가");
    private final JRadioButton[] events=new JRadioButton[4]; // 사건 횟수 0~3
    public RoomScene(ClientState state,Connection connection){this(state,connection,null);}
    /** addTestBots가 있으면 "로비로 나가기" 옆에 빈자리를 테스트 봇으로 채우는 버튼을 둔다. */
    public RoomScene(ClientState state,Connection connection,Runnable addTestBots){
        this.state=state;people.setEditable(false);
        JButton leave=new JButton("로비로 나가기");JPanel buttons=new JPanel();buttons.add(ready);buttons.add(leave);
        if(addTestBots!=null){
            addBots.addActionListener(e->{addBots.setEnabled(false);status.setText(CALLING_BOTS);addTestBots.run();});
            buttons.add(addBots);
        }
        // 사건 횟수는 방장만 바꾼다. 고르면 요청만 보내고, 서버가 ROOM_EVENTS로 알려 준 값을 표시한다.
        buttons.add(new JLabel("사건 횟수"));ButtonGroup group=new ButtonGroup();
        for(int i=0;i<events.length;i++){
            String count=Integer.toString(i);events[i]=new JRadioButton(count);group.add(events[i]);buttons.add(events[i]);
            events[i].addActionListener(e->connection.send(Message.of(MessageType.ROOM_EVENTS,count)));
        }
        buttons.add(status);
        ready.addActionListener(e->connection.send(Message.of(MessageType.READY,Boolean.toString(!state.ready().getOrDefault(state.myName(),false)))));
        leave.addActionListener(e->connection.send(Message.of(MessageType.LEAVE_ROOM)));
        panel.add(title,BorderLayout.NORTH);panel.add(new JScrollPane(people));panel.add(buttons,BorderLayout.SOUTH);
    }
    private void render(){
        title.setText("방 "+state.roomId()+" / "+state.roomName()+" / 방장 "+state.host()+" / 코드 "+state.roomCode()+" / 사건 "+state.roomEvents()+"번");
        boolean host=state.host().equals(state.myName());
        for(int i=0;i<events.length;i++){events[i].setSelected(i==state.roomEvents());events[i].setEnabled(host);}
        people.setText("");state.ready().forEach((name,r)->people.append(name+" : "+(r?"준비 완료":"대기")+"\n"));
        ready.setText(state.ready().getOrDefault(state.myName(),false)?"준비 취소":"준비");
        addBots.setEnabled(state.ready().size()<6);
        if(state.ready().size()>=6&&status.getText().equals(CALLING_BOTS))status.setText(GUIDE);
    }
    public JComponent view(){return panel;}public void onEnter(){render();}
    public void onMessage(Message m){render();if(m.type()==MessageType.ERROR)status.setText(m.field(0));}
}
