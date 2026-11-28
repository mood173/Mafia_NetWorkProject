package com.mafia.client.game;
import com.mafia.client.stream.ClientState;
import javax.swing.*;
/** 게임 화면 맨 위 한 줄: 내 이름, 직업, 단계, 남은 시간, 생존 여부, 오늘의 사건. 0.2초마다 다시 그려 남은 시간을 줄인다. */
public final class PhaseBanner extends JLabel {
    private final ClientState state;
    private final Timer timer;
    public PhaseBanner(ClientState state){this.state=state;timer=new Timer(200,e->render());}
    public void render(){setText(state.myName()+" / "+Words.role(state.myRole())+" / "+Words.phase(state.phase().name())+" / 남은 시간 "+state.remainingSeconds()+"초 / "+(state.amAlive()?"생존":"관전")+(state.todayEvent().isEmpty()?"":" / 사건: "+state.todayEventName()));}
    public void start(){render();timer.start();}public void stop(){timer.stop();}
}
