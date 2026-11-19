package com.mafia.server.net;
import com.mafia.common.protocol.*;
import com.mafia.server.engine.MessageHandler;
public final class LoginHandler implements MessageHandler {
    private final SessionRegistry sessions;
    public LoginHandler(SessionRegistry sessions) { this.sessions=sessions; }
    public void handle(Session from,Message message) {
        String name=message.field(0);
        if(!name.matches("[\\p{L}\\p{N}_-]{1,16}") || !(from instanceof ClientHandler client) || !client.login(name,sessions))
            from.send(Message.of(MessageType.ERROR,"닉네임 중복, 형식 오류 또는 이미 로그인된 연결입니다"));
        else from.send(Message.of(MessageType.LOGIN_OK,name));
    }
}
