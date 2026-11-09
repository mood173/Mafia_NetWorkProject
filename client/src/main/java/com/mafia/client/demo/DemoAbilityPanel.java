package com.mafia.client.demo;
import com.mafia.client.net.Connection;
import com.mafia.client.stream.ClientState;
import com.mafia.common.model.Phase;
import com.mafia.common.protocol.*;
import javax.swing.*;
/** 연동용 임시 능력 UI. 경탁의 AbilityPanel로 교체할 자리다. */
public final class DemoAbilityPanel extends JPanel implements com.mafia.client.character.AbilityView {
    private final JButton send=new JButton("밤 능력 선택");
    private final JComboBox<String> target=new JComboBox<>();
    private final ClientState state;
    public JComponent view(){return this;}
    public DemoAbilityPanel(ClientState state,Connection connection){
        this.state=state;add(target);add(send);
        send.addActionListener(e->{
            MessageType type=switch(state.myRole()){case "MAFIA"->MessageType.MAFIA_ATTACK;case "DOCTOR"->MessageType.DOCTOR_HEAL;case "POLICE"->MessageType.POLICE_CHECK;default->null;};
            if(type!=null && target.getSelectedItem()!=null)connection.send(Message.of(type,target.getSelectedItem().toString()));
        });
    }
    public void render(){
        Object selected=target.getSelectedItem();target.removeAllItems();
        for(String name:state.alive())if(state.myRole().equals("DOCTOR") || !name.equals(state.myName()))target.addItem(name);
        if(selected!=null)target.setSelectedItem(selected);
        boolean active=state.amAlive() && state.phase()==Phase.NIGHT && !state.myRole().equals("CITIZEN") && !state.myRole().isEmpty();
        send.setEnabled(active);target.setEnabled(active);
    }
}
