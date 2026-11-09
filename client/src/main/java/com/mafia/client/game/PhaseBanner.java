package com.mafia.client.game;
import com.mafia.client.stream.ClientState;
import javax.swing.*;
public final class PhaseBanner extends JLabel {
    private final ClientState state;
    private final Timer timer;
    public PhaseBanner(ClientState state){this.state=state;timer=new Timer(200,e->render());}
    public void render(){setText(state.myName()+" / "+state.myRole()+" / "+state.phase()+" / 남은 시간 "+state.remainingSeconds()+"초 / "+(state.amAlive()?"생존":"관전"));}
    public void start(){render();timer.start();}public void stop(){timer.stop();}
}
