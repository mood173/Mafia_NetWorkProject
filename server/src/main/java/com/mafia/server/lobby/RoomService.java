package com.mafia.server.lobby;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.GameEventHandler;
import com.mafia.server.game.GameSession;
import com.mafia.server.game.GameSessionFactory;
import com.mafia.server.game.event.EventDirector;
import com.mafia.server.net.Session;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 로비·대기방 규칙: 방 생성, 입장, 퇴장, 준비, 6명 전원 준비 시 게임 시작.
 * 판이 끝난 방은 다음 준비·입장 요청 때 대기방(전원 준비 해제)으로 돌아가 다시 쓸 수 있다.
 * 규칙 위반은 IllegalArgumentException(사유)으로 알리고, LobbyHandlers가 ERROR로 바꾼다.
 *
 * 락 순서: RoomService → GameSession 한 방향만 허용한다.
 * GameSession은 Room의 송신 메서드만 부르고 Room은 그때 락을 잡지 않으므로 역방향 대기가 없다.
 * 그래서 게임 시작·퇴장 반영을 이 락 안에서 호출해 방 인원과 판의 참가자가 어긋나지 않게 한다.
 */
public final class RoomService {
    private static final int MAX_TITLE_LENGTH = 24;
    private static final int MAX_CHAT_LENGTH = 500;

    private final GameConfig config;
    private final GameSessionFactory games;
    private final RoomRegistry registry;

    /** 필요한 것은 전부 밖(조립 지점)에서 받는다. */
    public RoomService(GameConfig config, GameSessionFactory games, RoomRegistry registry) {
        this.config = config;
        this.games = games;
        this.registry = registry;
    }

    public synchronized void roomList(Session from) {
        String list = registry.all().stream().filter(r -> !r.isPrivate())
                .map(Room::listing).collect(Collectors.joining(";"));
        from.send(Message.of(MessageType.ROOM_LIST, list));
    }

    public synchronized void create(Session from, String title, String kind) {
        if (title.isBlank() || title.length() > MAX_TITLE_LENGTH || title.chars().anyMatch(c -> "|,:;".indexOf(c) >= 0))
            throw new IllegalArgumentException("방 이름은 1~24자이며 | , : ; 를 쓸 수 없습니다");
        if (!kind.equals("PUBLIC") && !kind.equals("PRIVATE"))
            throw new IllegalArgumentException("공개 여부는 PUBLIC 또는 PRIVATE입니다");
        requireOutsideRoom(from);
        Room room = registry.create(title, kind.equals("PRIVATE"));
        enter(from, room);
        if (room.isPrivate()) from.send(Message.of(MessageType.ROOM_CODE, room.code()));
    }

    public synchronized void join(Session from, String roomId) {
        int id;
        try { id = Integer.parseInt(roomId); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("방 번호가 올바르지 않습니다"); }
        Room room = registry.byId(id).filter(r -> !r.isPrivate())
                .orElseThrow(() -> new IllegalArgumentException("공개방을 찾을 수 없습니다"));
        enter(from, room);
    }

    public synchronized void joinPrivate(Session from, String code) {
        Room room = registry.byCode(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("입장 코드가 올바르지 않습니다"));
        enter(from, room);
        // 코드로 들어온 사람은 이미 코드를 안다. 화면에 표시하고 테스트 봇도 이 코드로 넣을 수 있게 돌려준다.
        from.send(Message.of(MessageType.ROOM_CODE, room.code()));
    }

    public synchronized void leave(Session from) {
        if (!exit(from)) throw new IllegalArgumentException("입장한 방이 없습니다");
    }

    /** 연결이 끊긴 사용자 정리. GameServer의 종료 콜백에서 부른다. */
    public synchronized void disconnected(Session from) {
        if (from.name() != null) exit(from);
    }

    /**
     * 방 채팅. 대기방과 게임 종료 뒤에는 누구나, 진행 중에는 GameSession 규칙
     * (생존자, 낮 토론·투표 단계)을 따른다. 같은 방에만 방송한다.
     */
    public synchronized void chat(Session from, String text) {
        Room room = roomOf(from);
        if (text.isBlank() || text.length() > MAX_CHAT_LENGTH)
            throw new IllegalArgumentException("채팅은 1~" + MAX_CHAT_LENGTH + "자입니다");
        if (room.inProgress() && !room.game().canChat(from.name()))
            throw new IllegalArgumentException("지금은 채팅할 수 없습니다 (생존자만 낮 토론·투표 중 가능)");
        // 진행 중이면 그날의 사건이 전달 모양을 바꿀 수 있으므로 판에게 묻는다 (가면무도회)
        room.broadcast(room.inProgress() ? room.game().chatMessage(from.name(), text)
                : Message.of(MessageType.CHAT, from.name(), text));
    }

    public synchronized void ready(Session from, String value) {
        Room room = roomOf(from);
        room.resetIfFinished(); // 결과를 본 뒤 같은 방에서 다시 준비할 수 있다
        if (room.game() != null) throw new IllegalArgumentException("게임이 진행 중인 방입니다");
        if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("준비 값은 true 또는 false입니다");
        room.setReady(from.name(), Boolean.parseBoolean(value));
        room.broadcast(room.state());
        if (room.size() == config.roomCapacity() && room.allReady()) {
            GameSession game = games.create(room, room.eventCount());
            room.startGame(game);
            game.start();
        }
    }

    /** 방장이 대기방에서 한 판의 사건 횟수를 정한다. 방 전체에 알린다. */
    public synchronized void events(Session from, String value) {
        Room room = roomOf(from);
        room.resetIfFinished();
        if (room.game() != null) throw new IllegalArgumentException("게임이 진행 중인 방입니다");
        if (!room.host().equals(from.name())) throw new IllegalArgumentException("사건 횟수는 방장만 정할 수 있습니다");
        int count;
        try { count = Integer.parseInt(value); }
        catch (NumberFormatException e) { count = -1; }
        if (count < 0 || count > EventDirector.MAX_EVENTS)
            throw new IllegalArgumentException("사건 횟수는 0~" + EventDirector.MAX_EVENTS + "입니다");
        room.setEventCount(count);
        room.broadcast(room.events());
    }

    /** 이 참가자가 속한 방의 판. 없으면 null. 게임 명령 핸들러가 쓴다. */
    public synchronized GameEventHandler gameOf(String player) {
        return registry.of(player).map(Room::game).orElse(null);
    }

    private Room roomOf(Session player) {
        return registry.of(player.name()).orElseThrow(() -> new IllegalArgumentException("방에 먼저 입장하세요"));
    }

    private void requireOutsideRoom(Session player) {
        if (registry.of(player.name()).isPresent()) throw new IllegalArgumentException("이미 방에 있습니다");
    }

    private void enter(Session player, Room room) {
        requireOutsideRoom(player);
        room.resetIfFinished(); // 끝난 방은 대기방으로 돌아가 다시 들어올 수 있다
        if (room.game() != null) throw new IllegalArgumentException("이미 게임을 시작한 방입니다");
        if (room.size() >= config.roomCapacity()) throw new IllegalArgumentException("방이 가득 찼습니다");
        room.add(player);
        registry.enter(player.name(), room);
        room.broadcast(Message.of(MessageType.PLAYER_JOIN, player.name()));
        room.broadcast(room.state());
        // 늦게 들어온 사람도 방장이 정해 둔 사건 횟수를 알게 한다. 0이면 기본값이라 보내지 않는다.
        if (room.eventCount() > 0) player.send(room.events());
    }

    /** 방에서 내보낸다. 방에 없었으면 false. */
    private boolean exit(Session player) {
        Room room = registry.of(player.name()).orElse(null);
        if (room == null) return false;
        room.remove(player);
        registry.exit(player.name());
        if (room.game() != null) room.game().onPlayerLeft(player.name());
        room.resetIfFinished(); // 남은 사람에게 보낼 ROOM_STATE가 지난 판의 준비 표시를 담지 않게
        player.send(Message.of(MessageType.ROOM_LEFT, Integer.toString(room.id())));
        if (room.size() == 0) {
            registry.remove(room);
        } else {
            room.broadcast(Message.of(MessageType.PLAYER_LEAVE, player.name()));
            room.broadcast(room.state());
        }
        return true;
    }
}
