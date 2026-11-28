package com.mafia.server.game;
import com.mafia.common.protocol.*;
import com.mafia.server.engine.MessageHandler;
import com.mafia.server.net.Session;
import java.util.function.Function;
/** 게임 요청(능력, 투표)을 보낸 사람이 속한 방의 판으로 넘긴다. 방을 찾는 방법은 ServerMain에서 넣어 준다. */
public final class GameMessageHandler implements MessageHandler {
    private final Function<String,GameEventHandler> games;
    public GameMessageHandler(Function<String,GameEventHandler> games) { this.games=games; }
    public void handle(Session from,Message message) {
        GameEventHandler game=games.apply(from.name());
        if(game==null) from.send(Rejection.of("진행 중인 게임 없음",message.type(),RejectCode.NOT_ALLOWED_NOW).toMessage());
        else game.onPlayerMessage(from.name(),message);
    }
}
