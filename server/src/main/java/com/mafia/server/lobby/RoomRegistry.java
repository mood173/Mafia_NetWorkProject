package com.mafia.server.lobby;

import java.util.*;

/**
 * 방 목록과 "참가자 → 현재 방" 색인. 스레드 안전하지 않으며 RoomService 락 안에서만 쓴다.
 * 조립 지점(ServerMain)에서 만들어 RoomService 생성자로 넘긴다. 방(Room)은 여기서 만든다.
 */
public final class RoomRegistry {
    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 6;

    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Map<String, Room> byPlayer = new HashMap<>();
    private final Random random;
    private int nextId = 1;

    /** random: 비공개방 입장 코드 생성용. 검사에서는 씨앗을 고정해 넣는다. */
    public RoomRegistry(Random random) { this.random = random; }

    Room create(String title, boolean privateRoom) {
        Room room = new Room(nextId++, title, privateRoom, privateRoom ? uniqueCode() : "");
        rooms.put(room.id(), room);
        return room;
    }

    Optional<Room> byId(int id) { return Optional.ofNullable(rooms.get(id)); }

    Optional<Room> byCode(String code) {
        return rooms.values().stream().filter(r -> r.isPrivate() && r.code().equals(code)).findFirst();
    }

    Optional<Room> of(String player) { return Optional.ofNullable(byPlayer.get(player)); }

    void enter(String player, Room room) { byPlayer.put(player, room); }

    void exit(String player) { byPlayer.remove(player); }

    void remove(Room room) { rooms.remove(room.id()); }

    Collection<Room> all() { return rooms.values(); }

    private String uniqueCode() {
        while (true) {
            StringBuilder code = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            if (byCode(code.toString()).isEmpty()) return code.toString();
        }
    }
}
