package com.mafia.client.stream;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 클라이언트가 아는 게임 상태 (M). 서버 메시지로만 바뀐다.
 * 씬은 읽기만 하고, 바꾸는 곳은 GameStream.onMessage → apply 한 곳뿐이다.
 */
public final class ClientState {

    private String myName = "";
    private String myRole = "";
    private Phase phase = Phase.WAITING;
    private final List<String> alive = new ArrayList<>();

    void apply(Message m) {
        switch (m.type()) {
            case LOGIN_OK -> myName = m.field(0);
            case GAME_START -> {
                alive.clear();
                alive.addAll(Arrays.asList(m.field(0).split(",")));
                phase = Phase.WAITING;
            }
            case ROLE -> myRole = m.field(0);
            case PHASE_CHANGE -> phase = Phase.valueOf(m.field(0));
            case PLAYER_DIED -> alive.remove(m.field(0));
            case GAME_END -> phase = Phase.GAME_END;
            default -> { }
        }
    }

    public String myName() { return myName; }
    public String myRole() { return myRole; }
    public Phase phase() { return phase; }
    public List<String> alive() { return List.copyOf(alive); }
    public boolean amAlive() { return alive.contains(myName); }
}
