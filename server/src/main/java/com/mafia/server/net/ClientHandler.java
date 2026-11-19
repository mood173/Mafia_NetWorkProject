package com.mafia.server.net;
import com.mafia.common.net.TcpChannel;
import com.mafia.common.protocol.*;
import com.mafia.server.engine.MessageRouter;
import java.net.Socket;
import java.util.function.Consumer;
public final class ClientHandler implements Session, AutoCloseable {
    private volatile String name;
    private final TcpChannel channel;
    public ClientHandler(Socket socket,MessageCodec codec,MessageRouter router,Consumer<ClientHandler> departed) {
        channel=new TcpChannel(socket,codec,Direction.CLIENT_TO_SERVER,m->{
            if(name==null && m.type()!=MessageType.LOGIN) send(Message.of(MessageType.ERROR,"닉네임 등록이 필요합니다"));
            else router.dispatch(this,m);
        },error->send(Message.of(MessageType.ERROR,error)),()->departed.accept(this));
    }
    public void start() { channel.start(); }
    public String name() { return name; }
    public synchronized boolean login(String nickname,SessionRegistry registry) {
        // Cleanup takes this same monitor after the channel has closed.
        // A LOGIN racing with EOF must either precede cleanup or be rejected.
        if(!channel.isOpen() || name!=null || !registry.register(nickname,this)) return false;
        name=nickname; return true;
    }
    public void send(Message message) {
        try { channel.send(message); } catch(IllegalStateException e) { close(); }
    }
    public void close() { channel.close(); }
}
