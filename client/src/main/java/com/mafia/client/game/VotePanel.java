package com.mafia.client.game;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.ClientState;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.*;
import javax.swing.*;
public final class VotePanel extends JPanel {
    private final JComboBox<String> target=new JComboBox<>();private final JButton vote=new JButton("투표"),abstain=new JButton("기권");
    private final ClientState state;
    private Phase lastPhase;
    private boolean voted; // 이번 투표 단계에 이미 투표·기권했는지. 단계가 바뀌면 초기화한다.
    public VotePanel(ClientState state,Connection connection){
        this.state=state;add(target);add(vote);add(abstain);
        vote.addActionListener(e->{if(target.getSelectedItem()!=null){connection.send(Message.of(MessageType.VOTE,target.getSelectedItem().toString()));lockAfterVote();}});
        abstain.addActionListener(e->{connection.send(Message.of(MessageType.VOTE,""));lockAfterVote();});
    }
    private void lockAfterVote(){voted=true;target.setEnabled(false);vote.setEnabled(false);abstain.setEnabled(false);}
    public void render(){
        if(state.phase()!=lastPhase){lastPhase=state.phase();voted=false;}
        Object selected=target.getSelectedItem();target.removeAllItems();
        for(String p:state.alive())if(!p.equals(state.myName()))target.addItem(p);
        if(selected!=null)target.setSelectedItem(selected);
        boolean active=!voted&&state.amAlive()&&state.phase()==Phase.VOTE;target.setEnabled(active);vote.setEnabled(active);abstain.setEnabled(active);
    }
}
