package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.Scene;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;

public final class LobbyScene implements Scene {
    private final Connection connection;
    private final JPanel panel=new JPanel(new BorderLayout(10,10));
    private final DefaultListModel<String> rooms=new DefaultListModel<>();
    private final JList<String> list=new JList<>(rooms);
    private final JLabel status=new JLabel("연동 시연 로비 - 팀원 로비 서비스와 교체 가능");
    public LobbyScene(Connection connection) {
        this.connection=connection;
        JPanel top=new JPanel(); JTextField roomName=new JTextField("연습방",10);
        JComboBox<String> kind=new JComboBox<>(new String[]{"PUBLIC","PRIVATE"});
        JButton create=new JButton("방 만들기"),refresh=new JButton("새로고침"),join=new JButton("선택 방 입장");
        JTextField code=new JTextField(8); JButton privateJoin=new JButton("코드로 입장");
        create.addActionListener(e->connection.send(Message.of(MessageType.CREATE_ROOM,roomName.getText().trim(),kind.getSelectedItem().toString())));
        refresh.addActionListener(e->onEnter());
        join.addActionListener(e->{if(list.getSelectedValue()!=null)connection.send(Message.of(MessageType.JOIN_ROOM,list.getSelectedValue().split(",",2)[0]));});
        privateJoin.addActionListener(e->connection.send(Message.of(MessageType.JOIN_PRIVATE,code.getText().trim())));
        top.add(roomName);top.add(kind);top.add(create);top.add(refresh);top.add(join);
        JPanel bottom=new JPanel();bottom.add(new JLabel("입장 코드"));bottom.add(code);bottom.add(privateJoin);bottom.add(status);
        panel.add(top,BorderLayout.NORTH);panel.add(new JScrollPane(list));panel.add(bottom,BorderLayout.SOUTH);
    }
    public JComponent view(){return panel;}
    public void onEnter(){connection.send(Message.of(MessageType.ROOM_LIST));}
    public void onMessage(Message message){
        if(message.type()==MessageType.ROOM_LIST){rooms.clear();if(!message.field(0).isEmpty())for(String r:message.field(0).split(";"))rooms.addElement(r);}
        if(message.type()==MessageType.ERROR)status.setText(message.field(0));
    }
}
