package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.*;
import com.mafia.client.game.*;
import com.mafia.client.character.AbilityView;
import com.mafia.client.ui.ChatView;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
public final class GameScene implements Scene {
    private final ClientState state;private final JPanel panel=new JPanel(new BorderLayout(10,10));
    private final DefaultListModel<String> alive=new DefaultListModel<>();
    private final PhaseBanner banner;private final VotePanel votes;
    private final AbilityView abilities;private final ChatView chat;
    private final JTextArea notice=new JTextArea(4,30);
    public GameScene(ClientState state,Connection connection,PhaseBanner banner,VotePanel votes,AbilityView abilities,ChatView chat){
        this.state=state;this.banner=banner;this.votes=votes;this.abilities=abilities;this.chat=chat;
        panel.add(banner,BorderLayout.NORTH);panel.add(new JScrollPane(new JList<>(alive)),BorderLayout.WEST);
        JPanel center=new JPanel(new BorderLayout());notice.setEditable(false);center.add(new JScrollPane(notice),BorderLayout.NORTH);center.add(chat.view());panel.add(center);
        JPanel bottom=new JPanel();bottom.add(abilities.view());bottom.add(votes);JButton leave=new JButton("게임 나가기");bottom.add(leave);
        leave.addActionListener(e->connection.send(Message.of(MessageType.LEAVE_ROOM)));panel.add(bottom,BorderLayout.SOUTH);
    }
    public JComponent view(){return panel;}
    public void onEnter(){notice.setText("");chat.reset();banner.start();render();}
    public void onExit(){banner.stop();}
    private void render(){banner.render();alive.clear();state.alive().forEach(alive::addElement);votes.render();abilities.render();chat.render(state);}
    public void onMessage(Message m){
        render();chat.accept(m);
        switch(m.type()){
            case ERROR,SYSTEM_CHAT -> notice.append(m.field(0)+"\n");
            case POLICE_RESULT -> notice.append("조사: "+m.field(0)+" / 마피아="+m.field(1)+"\n");
            case NIGHT_RESULT -> notice.append("밤 결과: "+(m.field(0).isEmpty()?"사망 없음":m.field(0))+"\n");
            case PLAYER_DIED -> notice.append(m.field(0)+" 사망 ("+m.field(1)+")\n");
            case VOTE_RESULT -> notice.append("투표 결과: "+(m.field(0).isEmpty()?"처형 없음":m.field(0))+"\n");
            default -> { }
        }
    }
}
