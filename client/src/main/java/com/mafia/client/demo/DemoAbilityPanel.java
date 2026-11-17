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
    private Phase lastPhase;
    private boolean used; // 이번 밤에 이미 능력을 보냈는지. 단계가 바뀌면 초기화한다.
    public JComponent view(){return this;}
    public DemoAbilityPanel(ClientState state,Connection connection){
        this.state=state;add(target);add(send);
        send.addActionListener(e->{
            MessageType type=switch(state.myRole()){case "MAFIA"->MessageType.MAFIA_ATTACK;case "DOCTOR"->MessageType.DOCTOR_HEAL;case "POLICE"->MessageType.POLICE_CHECK;default->null;};
            if(type!=null && target.getSelectedItem()!=null){
                connection.send(Message.of(type,target.getSelectedItem().toString()));
                used=true;send.setEnabled(false);target.setEnabled(false);
            }
        });
    }
    public void render(){
        if(state.phase()!=lastPhase){lastPhase=state.phase();used=false;}
        Object selected=target.getSelectedItem();target.removeAllItems();
        for(String name:state.alive())if(state.myRole().equals("DOCTOR") || !name.equals(state.myName()))target.addItem(name);
        if(selected!=null)target.setSelectedItem(selected);
        boolean active=!used && state.amAlive() && state.phase()==Phase.NIGHT && !state.myRole().equals("CITIZEN") && !state.myRole().isEmpty();
        send.setEnabled(active);target.setEnabled(active);
    }
}
