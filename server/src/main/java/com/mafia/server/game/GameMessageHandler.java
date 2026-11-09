package com.mafia.server.game;
import com.mafia.common.protocol.*;
import com.mafia.server.engine.MessageHandler;
import com.mafia.server.net.Session;
import java.util.function.Function;
/** 경탁의 RoomRegistry 조회 함수를 조립 지점에서 주입한다. */
public final class GameMessageHandler implements MessageHandler {
    private final Function<String,GameEventHandler> games;
    public GameMessageHandler(Function<String,GameEventHandler> games) { this.games=games; }
    public void handle(Session from,Message message) {
        GameEventHandler game=games.apply(from.name());
        if(game==null) from.send(Message.of(MessageType.ERROR,"진행 중인 게임 없음"));
        else game.onPlayerMessage(from.name(),message);
    }
}
