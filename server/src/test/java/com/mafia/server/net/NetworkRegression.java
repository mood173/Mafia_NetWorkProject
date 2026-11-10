package com.mafia.server.net;

import com.mafia.client.net.*;
import com.mafia.common.protocol.*;
import com.mafia.server.engine.MessageRouter;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NetworkRegression {
    static final class Client implements MessageListener, AutoCloseable {
        final BlockingQueue<Message> received=new LinkedBlockingQueue<>();
        final AtomicInteger ended=new AtomicInteger();
        final SocketConnection connection=new SocketConnection(new MessageCodec());
        Client(int port) throws Exception { connection.setListener(this); connection.connect("127.0.0.1",port); }
        public void onMessage(Message m) { received.add(m); }
        public void onDisconnected() { ended.incrementAndGet(); }
        void send(MessageType t,String... fields) { connection.send(Message.of(t,fields)); }
        Message await(MessageType type) throws Exception {
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<end) {
                Message m=received.poll(100,TimeUnit.MILLISECONDS);
                if(m!=null&&m.type()==type) return m;
            }
            throw new AssertionError("missing "+type);
        }
        public void close() { connection.close(); }
    }
    static void check(boolean b,String reason) { if(!b) throw new AssertionError(reason); }
    @org.junit.jupiter.api.Test
    public void regression() throws Exception { main(new String[0]); }

    @org.junit.jupiter.api.Test
    public void closedSessionCannotReserveNickname() throws Exception {
        SessionRegistry registry=new SessionRegistry();
        try(ServerSocket listener=new ServerSocket(0);
            Socket peer=new Socket("127.0.0.1",listener.getLocalPort());
            Socket accepted=listener.accept()) {
            ClientHandler handler=new ClientHandler(accepted,new MessageCodec(),new MessageRouter(),
                    h->registry.remove(h.name(),h));
            handler.close();
            check(!handler.login("closed_name",registry),"closed session must reject late LOGIN");
            Session replacement=new Session() {
                public String name() { return "closed_name"; }
                public void send(Message message) { }
            };
            check(registry.register("closed_name",replacement),"late LOGIN must not reserve nickname");
        }
    }

    public static void main(String[] args) throws Exception {
        ExecutorService accept=Executors.newSingleThreadExecutor();
        List<Client> clients=new ArrayList<>();
        SessionRegistry registry=new SessionRegistry();
        MessageRouter router=new MessageRouter();
        router.register(MessageType.LOGIN,new LoginHandler(registry));
        // Test-only echo: exercises the wire without implementing chat or room business rules.
        router.register(MessageType.CHAT,(from,m)->from.send(Message.of(MessageType.CHAT,from.name(),m.field(0))));
        try(GameServer server=new GameServer("127.0.0.1",0,new MessageCodec(),router,registry,s->{})) {
            Future<?> running=accept.submit(()->{ try { server.start(); } catch(IOException e) { throw new UncheckedIOException(e); } });
            Client first=new Client(server.port()); clients.add(first);
            first.send(MessageType.READY,"true"); first.await(MessageType.ERROR);
            first.send(MessageType.LOGIN,""); first.await(MessageType.ERROR);
            first.send(MessageType.LOGIN,"사용자0"); first.await(MessageType.LOGIN_OK);
            for(int i=1;i<6;i++) {
                Client c=new Client(server.port()); clients.add(c);
                c.send(MessageType.LOGIN,"사용자0"); c.await(MessageType.ERROR);
                c.send(MessageType.LOGIN,"사용자"+i); c.await(MessageType.LOGIN_OK);
            }
            first.send(MessageType.ROOM_LIST);
            check(first.await(MessageType.ERROR).field(0).contains("ROOM_LIST"),"unintegrated room service");
            for(int i=0;i<20;i++) first.send(MessageType.CHAT,"한글|"+i);
            for(int i=0;i<20;i++) {
                Message echoed=first.await(MessageType.CHAT);
                check(echoed.field(0).equals("사용자0")&&echoed.field(1).equals("한글|"+i),"FIFO and UTF-8");
            }
            for(int i=1;i<6;i++) check(clients.get(i).received.isEmpty(),"echo is private");
            first.close(); first.close();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(first.ended.get()==0&&System.nanoTime()<deadline) Thread.sleep(10);
            check(first.ended.get()==1,"single close notification");
            first.connection.connect("127.0.0.1",server.port());
            // Disconnect cleanup is asynchronous. Retry the same name until it is released.
            boolean relogged=false;
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(!relogged&&System.nanoTime()<deadline) {
                first.send(MessageType.LOGIN,"사용자0");
                Message response=first.received.poll(3,TimeUnit.SECONDS);
                check(response!=null,"login response");
                relogged=response.type()==MessageType.LOGIN_OK;
                if(!relogged) { check(response.type()==MessageType.ERROR,"login rejection"); Thread.sleep(10); }
            }
            check(relogged,"nickname released after disconnect");
            try(Socket raw=new Socket("127.0.0.1",server.port())) {
                raw.setSoTimeout(3000);
                var out=raw.getOutputStream();
                var in=new BufferedReader(new InputStreamReader(raw.getInputStream(),StandardCharsets.UTF_8));
                out.write("LOGIN\n".getBytes(StandardCharsets.UTF_8)); out.flush();
                check(in.readLine().startsWith("ERROR|"),"missing field");
                out.write("LOG".getBytes(StandardCharsets.UTF_8)); out.flush();
                out.write("IN|fragment\r\nROOM_LIST\n".getBytes(StandardCharsets.UTF_8)); out.flush();
                check(in.readLine().equals("LOGIN_OK|fragment"),"fragmentation");
                check(in.readLine().startsWith("ERROR|"),"coalesced frames and unregistered request");
                out.write(("x".repeat(8193)+"\n").getBytes(StandardCharsets.UTF_8)); out.flush();
                check(in.read()==-1,"oversize close");
            }
            server.close(); running.get(5,TimeUnit.SECONDS);
            System.out.println("PASS: real TCP six-client login, nickname retry/release, auth gate, private FIFO echo, reconnect, framing and length");
        } finally { clients.forEach(Client::close); accept.shutdownNow(); }
    }
}
