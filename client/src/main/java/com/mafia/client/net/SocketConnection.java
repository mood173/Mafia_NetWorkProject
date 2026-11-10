package com.mafia.client.net;

import com.mafia.common.net.TcpChannel;
import com.mafia.common.protocol.*;
import java.io.IOException;
import java.net.*;
import java.util.function.Consumer;

public final class SocketConnection implements Connection {
    private final MessageCodec codec;
    private final Consumer<Runnable> callbacks;
    private MessageListener listener;
    private TcpChannel channel;
    private Socket pending;
    private long generation;
    public SocketConnection(MessageCodec codec) { this(codec,Runnable::run); }
    public SocketConnection(MessageCodec codec, Consumer<Runnable> callbacks) { this.codec=codec; this.callbacks=callbacks; }
    public synchronized void setListener(MessageListener listener) { this.listener=listener; }
    public void connect(String host,int port) throws IOException {
        Socket socket=new Socket(); long id;
        synchronized(this) {
            if(listener==null) throw new IllegalStateException("수신자를 먼저 등록하세요");
            if(pending!=null || channel!=null && channel.isOpen()) throw new IllegalStateException("이미 연결 중이거나 연결되어 있습니다");
            pending=socket; id=++generation;
        }
        try {
            socket.connect(new InetSocketAddress(host,port),3000);
            socket.setTcpNoDelay(true);
            synchronized(this) {
                if(id!=generation) throw new IOException("연결 요청이 취소되었습니다");
                channel=new TcpChannel(socket,codec,Direction.SERVER_TO_CLIENT,
                    m->deliver(id,()->listener.onMessage(m)), error->close(),
                    ()->deliver(id,()->listener.onDisconnected()));
                pending=null; channel.start();
            }
        } catch(IOException | RuntimeException e) {
            synchronized(this) { if(pending==socket) pending=null; }
            try { socket.close(); } catch(IOException ignored) { }
            throw e;
        }
    }
    private void deliver(long id,Runnable callback) {
        callbacks.accept(()->{ synchronized(this) { if(id==generation) callback.run(); } });
    }
    public void send(Message message) {
        TcpChannel current;
        synchronized(this) { current=channel; }
        if(current==null) throw new IllegalStateException("먼저 서버에 연결하세요");
        current.send(message);
    }
    public void close() {
        TcpChannel current; Socket connecting;
        synchronized(this) { current=channel; connecting=pending; pending=null; if(connecting!=null) generation++; }
        if(connecting!=null) try { connecting.close(); } catch(IOException ignored) { }
        if(current!=null) current.close();
    }
}
