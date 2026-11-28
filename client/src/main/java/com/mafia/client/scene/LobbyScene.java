package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.Scene;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;

/**
 * 로비 화면: 공개방 목록, 방 만들기, 공개방 입장, 입장 코드로 비공개방 입장.
 * 버튼은 요청만 보낸다. 대기실로 넘어가는 것은 서버가 ROOM_STATE를 보냈을 때다.
 */
public final class LobbyScene implements Scene {
    private final Connection connection;
    private final JPanel panel=new JPanel(new BorderLayout(10,10));
    private final DefaultListModel<String> rooms=new DefaultListModel<>();
    private final JList<String> list=new JList<>(rooms);
    private final JLabel status=new JLabel(" ");
    private final java.util.List<String> roomIds=new java.util.ArrayList<>(); // 목록 줄과 같은 순서의 방 번호
    public LobbyScene(Connection connection) {
        this.connection=connection;
        JPanel top=new JPanel(); JTextField roomName=new JTextField("연습방",10);
        JComboBox<String> kind=new JComboBox<>(new String[]{"공개방","비공개방"});
        JButton create=new JButton("방 만들기"),refresh=new JButton("새로고침"),join=new JButton("선택 방 입장");
        JTextField code=new JTextField(8); JButton privateJoin=new JButton("코드로 입장");
        create.addActionListener(e->connection.send(Message.of(MessageType.CREATE_ROOM,roomName.getText().trim(),kind.getSelectedIndex()==1?"PRIVATE":"PUBLIC")));
        refresh.addActionListener(e->onEnter());
        join.addActionListener(e->{int i=list.getSelectedIndex();if(i>=0&&i<roomIds.size())connection.send(Message.of(MessageType.JOIN_ROOM,roomIds.get(i)));});
        privateJoin.addActionListener(e->connection.send(Message.of(MessageType.JOIN_PRIVATE,code.getText().trim())));
        top.add(roomName);top.add(kind);top.add(create);top.add(refresh);top.add(join);
        JPanel bottom=new JPanel();bottom.add(new JLabel("입장 코드"));bottom.add(code);bottom.add(privateJoin);bottom.add(status);
        panel.add(top,BorderLayout.NORTH);panel.add(new JScrollPane(list));panel.add(bottom,BorderLayout.SOUTH);
    }
    public JComponent view(){return panel;}
    public void onEnter(){connection.send(Message.of(MessageType.ROOM_LIST));}
    public void onMessage(Message message){
        if(message.type()==MessageType.ROOM_LIST){
            rooms.clear();roomIds.clear();
            // 서버 형식: 번호,이름,인원,진행여부;... → "1번  민준방  (3/6명, 대기 중)"
            if(!message.field(0).isEmpty())for(String r:message.field(0).split(";")){
                String[] f=r.split(",");
                if(f.length<4){rooms.addElement(r);roomIds.add(f[0]);continue;}
                roomIds.add(f[0]);rooms.addElement(f[0]+"번  "+f[1]+"  ("+f[2]+"/6명, "+(Boolean.parseBoolean(f[3])?"게임 중":"대기 중")+")");
            }
        }
        if(message.type()==MessageType.ERROR)status.setText(message.field(0));
    }
}
