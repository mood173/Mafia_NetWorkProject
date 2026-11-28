package com.mafia.server.lobby;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.game.GameSession;
import com.mafia.server.game.RoomChannel;
import com.mafia.server.net.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 방 하나: 참가자(입장 순서 = 방장 순서), 준비 상태, 진행 중인 판.
 * 상태 변경은 RoomService 락 안에서만 한다.
 * RoomChannel 메서드(players/broadcast/send)는 락을 잡지 않는다.
 * GameSession이 자기 락을 잡은 채 타이머 스레드에서 호출하기 때문이다.
 */
public final class Room implements RoomChannel {
    private final int id;
    private final String title;
    private final boolean privateRoom;
    private final String code;
    private final CopyOnWriteArrayList<Session> members = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> ready = new HashMap<>();
    private GameSession game;
    private int eventCount; // 한 판의 사건 횟수. 방장이 대기방에서 정한다

    Room(int id, String title, boolean privateRoom, String code) {
        this.id = id;
        this.title = title;
        this.privateRoom = privateRoom;
        this.code = code;
    }

    int id() { return id; }
    boolean isPrivate() { return privateRoom; }
    String code() { return code; }
    int size() { return members.size(); }
    GameSession game() { return game; }

    void add(Session player) {
        members.add(player);
        ready.put(player.name(), false);
    }

    void remove(Session player) {
        members.remove(player);
        ready.remove(player.name());
    }

    int eventCount() { return eventCount; }
    void setEventCount(int count) { eventCount = count; }
    String host() { return members.get(0).name(); }

    /** ROOM_EVENTS: 이 방의 사건 횟수 */
    Message events() { return Message.of(MessageType.ROOM_EVENTS, Integer.toString(eventCount)); }

    void setReady(String player, boolean value) { ready.put(player, value); }

    boolean allReady() { return ready.values().stream().allMatch(Boolean::booleanValue); }

    void startGame(GameSession session) { game = session; }

    boolean inProgress() { return game != null && game.phase() != Phase.GAME_END; }

    /**
     * 끝난 판을 치우고 전원을 준비 해제로 돌린다. 판이 없거나 진행 중이면 아무것도 하지 않는다.
     * 판이 끝나는 순간(GameSession 락 안, 타이머 스레드)에 하지 않고 다음 방 요청 때 한다. 락 순서를 지키기 위해서다.
     */
    void resetIfFinished() {
        if (game == null || inProgress()) return;
        game = null;
        ready.replaceAll((name, value) -> false);
    }

    /** ROOM_LIST 항목: 번호,이름,인원,진행여부 */
    String listing() { return id + "," + title + "," + members.size() + "," + inProgress(); }

    /** ROOM_STATE: 방번호, 이름, PUBLIC/PRIVATE, 방장, 이름:준비,... */
    Message state() {
        String entries = members.stream()
                .map(p -> p.name() + ":" + ready.getOrDefault(p.name(), false))
                .collect(Collectors.joining(","));
        return Message.of(MessageType.ROOM_STATE, Integer.toString(id), title,
                privateRoom ? "PRIVATE" : "PUBLIC", members.get(0).name(), entries);
    }

    @Override
    public List<String> players() {
        return members.stream().map(Session::name).toList();
    }

    @Override
    public void broadcast(Message message) {
        for (Session member : members) member.send(message);
    }

    @Override
    public void send(String playerName, Message message) {
        for (Session member : members) {
            if (member.name().equals(playerName)) member.send(message);
        }
    }
}
