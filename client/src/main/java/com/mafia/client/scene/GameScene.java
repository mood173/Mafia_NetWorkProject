package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.*;
import com.mafia.client.game.*;
import com.mafia.client.character.AbilityView;
import com.mafia.client.ui.ChatView;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
public final class GameScene implements Scene {
    private final ClientState state;private final JPanel panel=new JPanel(new BorderLayout(10,10));
    private final DefaultListModel<String> alive=new DefaultListModel<>();
    private final PhaseBanner banner;private final VotePanel votes;
    private final AbilityView abilities;private final ChatView chat;
    private final JTextArea notice=new JTextArea(4,30);
    public GameScene(ClientState state,Connection connection,PhaseBanner banner,VotePanel votes,AbilityView abilities,ChatView chat){
        this.state=state;this.banner=banner;this.votes=votes;this.abilities=abilities;this.chat=chat;
        panel.add(banner,BorderLayout.NORTH);panel.add(new JScrollPane(new JList<>(alive)),BorderLayout.WEST);
        // 가운데는 게임 기록(서버 안내)과 채팅을 반반으로 나눈다. 게임 기록을 먼저 넣는다.
        JPanel center=new JPanel(new GridLayout(1,2,10,0));notice.setEditable(false);notice.setLineWrap(true);notice.setWrapStyleWord(true);
        center.add(titled("게임 기록",new JScrollPane(notice)));center.add(titled("채팅",chat.view()));panel.add(center);
        JPanel bottom=new JPanel();bottom.add(abilities.view());bottom.add(votes);JButton leave=new JButton("게임 나가기");bottom.add(leave);
        leave.addActionListener(e->connection.send(Message.of(MessageType.LEAVE_ROOM)));panel.add(bottom,BorderLayout.SOUTH);
    }
    /** 제목을 단 칸. 안쪽 부품은 가운데를 가득 채운다. */
    private static JPanel titled(String title,JComponent body){
        JPanel box=new JPanel(new BorderLayout(0,4));box.add(new JLabel(title),BorderLayout.NORTH);box.add(body);return box;
    }
    public JComponent view(){return panel;}
    public void onEnter(){notice.setText("");chat.reset();banner.start();render();}
    public void onExit(){banner.stop();}
    private void render(){
        banner.render();
        alive.clear();
        Set<String> aliveSet=new HashSet<>(state.alive());
        if(!state.participants().isEmpty()){
            for(String name:state.participants()) alive.addElement(name+(aliveSet.contains(name)?" [생존]":" [사망]"));
        } else {
            for(String name:state.alive()) alive.addElement(name+" [생존]");
        }
        votes.render();abilities.render();chat.render(state);
    }
    public void onMessage(Message m){
        render();chat.accept(m);
        switch(m.type()){
            case ERROR -> {
                Rejection rejection=Rejection.from(m);
                notice.append(rejection.reason()+"\n");
                // 거절된 요청의 버튼만 다시 연다. 채팅처럼 다른 요청이 거절된 것이면 건드리지 않고,
                // 이미 받아들여진 행동을 또 보낸 거절(ALREADY_DONE)이면 잠금을 그대로 둔다.
                if(rejection.code()!=RejectCode.ALREADY_DONE){
                    if(rejection.isAbout(MessageType.MAFIA_ATTACK,MessageType.DOCTOR_HEAL,MessageType.POLICE_CHECK)) abilities.unlockOnError();
                    else if(rejection.isAbout(MessageType.VOTE)) votes.unlockOnError();
                }
            }
            case SYSTEM_CHAT -> notice.append(Words.notice(m.field(0))+"\n");
            case EVENT -> notice.append("[사건] "+m.field(1)+"\n");
            case VOTE_CAST -> notice.append("공개 투표: "+m.field(0)+" → "+(m.field(1).isEmpty()?"기권":m.field(1))+"\n");
            case POLICE_RESULT -> notice.append("조사 결과: "+m.field(0)+"님은 마피아"+(Boolean.parseBoolean(m.field(1))?"입니다":"가 아닙니다")+"\n");
            case NIGHT_RESULT -> notice.append("밤 결과: "+(m.field(0).isEmpty()?"아무도 죽지 않았습니다":m.field(0)+" 사망")+"\n");
            case PLAYER_DIED -> notice.append(m.field(0)+" 사망 ("+Words.cause(m.field(1))+")\n");
            case VOTE_RESULT -> {
                String eliminated=m.field(0).isEmpty()?"처형 없음":m.field(0)+" 처형";
                String tally=m.field(1).isEmpty()?"":" ("+m.field(1)+")";
                notice.append("투표 결과: "+eliminated+tally+"\n");
            }
            default -> { }
        }
    }
}
