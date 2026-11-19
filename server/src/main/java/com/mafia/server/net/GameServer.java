package com.mafia.server.net;
import com.mafia.common.protocol.MessageCodec;
import com.mafia.server.engine.MessageRouter;
import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
public final class GameServer implements AutoCloseable {
    private final ServerSocket socket;
    private final MessageCodec codec;
    private final MessageRouter router;
    private final SessionRegistry sessions;
    private final Consumer<Session> onLeft;
    private final Set<ClientHandler> clients=ConcurrentHashMap.newKeySet();
    private final ExecutorService cleanup=Executors.newSingleThreadExecutor();
    private final AtomicBoolean open=new AtomicBoolean(true);
    public GameServer(String host,int port,MessageCodec codec,MessageRouter router,SessionRegistry sessions,Consumer<Session> onLeft) throws IOException {
        this.codec=codec; this.router=router; this.sessions=sessions; this.onLeft=onLeft;
        socket=new ServerSocket();
        try { socket.bind(new InetSocketAddress(host,port)); } catch(IOException | RuntimeException e) { socket.close(); throw e; }
    }
    public int port() { return socket.getLocalPort(); }
    public void start() throws IOException {
        while(open.get()) {
            Socket accepted;
            try { accepted=socket.accept(); } catch(SocketException e) { if(!open.get()) return; throw e; }
            accepted.setTcpNoDelay(true);
            ClientHandler client=new ClientHandler(accepted,codec,router,this::departed);
            synchronized(this) {
                if(!open.get()) { accepted.close(); return; }
                clients.add(client); client.start();
            }
        }
    }
    private void departed(ClientHandler client) {
        cleanup.execute(()->{
            try { onLeft.accept(client); }
            finally { synchronized(client) { sessions.remove(client.name(),client); } clients.remove(client); if(!open.get()&&clients.isEmpty()) cleanup.shutdown(); }
        });
    }
    public synchronized void close() {
        if(!open.getAndSet(false)) return;
        try { socket.close(); } catch(IOException ignored) { }
        clients.forEach(ClientHandler::close); if(clients.isEmpty()) cleanup.shutdown();
    }
}
