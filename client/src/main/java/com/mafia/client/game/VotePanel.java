package com.mafia.client.game;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.ClientState;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.*;
import javax.swing.*;
public final class VotePanel extends JPanel {
    private final JComboBox<String> target=new JComboBox<>();private final JButton vote=new JButton("투표"),abstain=new JButton("기권");
    private final ClientState state;
    public VotePanel(ClientState state,Connection connection){
        this.state=state;add(target);add(vote);add(abstain);
        vote.addActionListener(e->{if(target.getSelectedItem()!=null)connection.send(Message.of(MessageType.VOTE,target.getSelectedItem().toString()));});
        abstain.addActionListener(e->connection.send(Message.of(MessageType.VOTE,"")));
    }
    public void render(){
        Object selected=target.getSelectedItem();target.removeAllItems();
        for(String p:state.alive())if(!p.equals(state.myName()))target.addItem(p);
        if(selected!=null)target.setSelectedItem(selected);
        boolean active=state.amAlive()&&state.phase()==Phase.VOTE;target.setEnabled(active);vote.setEnabled(active);abstain.setEnabled(active);
    }
}
