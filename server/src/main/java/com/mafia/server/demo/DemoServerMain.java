package com.mafia.server.demo;

import com.mafia.common.protocol.*;
import com.mafia.server.engine.GameConfig;
import com.mafia.server.game.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 실제 TCP를 사용하는 별도 연동 시연 어댑터.
 * Lobby/Socket/Chat/Character는 팀원 구현을 기다리는 참조 구현이며 개인 담당과 분리한다.
 */
public final class DemoServerMain implements AutoCloseable {
    private final ServerSocket server;
    private final GameConfig config;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
    private final GameSessionFactory games;
    private final Map<String,Peer> users=new LinkedHashMap<>();
    private final Map<Integer,Room> rooms=new LinkedHashMap<>();
    private final Set<Peer> connections=ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running=new AtomicBoolean(true);
    private int nextRoom=1;

    public DemoServerMain(int port,GameConfig config) throws IOException {
        this.config=config;server=new ServerSocket(port,50,InetAddress.getLoopbackAddress());
        games=new GameSessionFactory(config,DemoRole.catalog(),DemoCharacterService::new,new ScheduledPhaseTimer(scheduler),new Random());
        Thread accept=new Thread(this::acceptLoop,"demo-accept");accept.start();
    }
    public int port(){return server.getLocalPort();}
    private void acceptLoop(){
        while(running.get())try{Peer peer=new Peer(server.accept());connections.add(peer);peer.start();}
        catch(IOException e){if(running.get())System.err.println(e.getMessage());}
    }
    private final class Peer {
        private final Socket socket;private final BlockingQueue<Message> outbound=new ArrayBlockingQueue<>(256);
        private final AtomicBoolean open=new AtomicBoolean(true);private Thread writer;
        private String name;private Room room;
        Peer(Socket socket){this.socket=socket;}
        void start(){
            writer=new Thread(()->{
                try(var out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))){
                    MessageCodec codec=new MessageCodec();while(open.get()){Message m=outbound.take();out.write(codec.encode(m));out.newLine();out.flush();}
                }catch(IOException|InterruptedException e){closeSocket();}
            },"demo-send");writer.start();
            new Thread(()->{
                try(var input=new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8)){
                    MessageCodec codec=new MessageCodec();String line;
                    while((line=readBounded(input))!=null){
                        try{handle(this,codec.decode(line,Direction.CLIENT_TO_SERVER));}
                        catch(IllegalArgumentException|IllegalStateException e){send(Message.of(MessageType.ERROR,e.getMessage()==null?"요청 오류":e.getMessage()));}
                    }
                }catch(IOException e){/* EOF is handled below */}
                finally{closeSocket();depart(this);connections.remove(this);}
            },"demo-receive").start();
        }
        void send(Message message){if(open.get()&&!outbound.offer(message))closeSocket();}
        void closeSocket(){if(open.getAndSet(false)){try{socket.close();}catch(IOException ignored){}if(writer!=null)writer.interrupt();}}
    }
    private static String readBounded(Reader input) throws IOException {
        StringBuilder line=new StringBuilder();int c;
        while((c=input.read())!=-1){if(c=='\n')return line.toString();if(c=='\r')continue;if(line.length()>=MessageCodec.MAX_LENGTH)throw new IOException("너무 긴 줄");line.append((char)c);}
        return line.length()==0?null:line.toString();
    }
    private final class Room implements RoomChannel {
        final int id;final String title,code;final boolean privateRoom;
        final CopyOnWriteArrayList<Peer> peers=new CopyOnWriteArrayList<>();
        final Map<String,Boolean> ready=new LinkedHashMap<>();
        GameSession game;
        Room(int id,String title,boolean hidden){this.id=id;this.title=title;privateRoom=hidden;code=UUID.randomUUID().toString().replace("-","").substring(0,6).toUpperCase(Locale.ROOT);}
        public List<String> players(){return peers.stream().map(p->p.name).toList();}
        public void broadcast(Message m){peers.forEach(p->p.send(m));}
        public void send(String name,Message m){peers.stream().filter(p->p.name.equals(name)).findFirst().ifPresent(p->p.send(m));}
        void state(){
            String members=peers.stream().map(p->p.name+":"+ready.getOrDefault(p.name,false)).collect(Collectors.joining(","));
            broadcast(Message.of(MessageType.ROOM_STATE,Integer.toString(id),title,privateRoom?"PRIVATE":"PUBLIC",peers.get(0).name,members));
        }
        boolean active(){return game!=null&&game.phase()!=com.mafia.common.model.Phase.GAME_END;}
    }
    private synchronized void handle(Peer p,Message m){
        if(m.type()==MessageType.LOGIN){
            String name=m.field(0);
            if(p.name!=null||!name.matches("[\\p{L}\\p{N}_-]{1,16}")||users.containsKey(name))throw new IllegalArgumentException("닉네임 중복 또는 형식 오류");
            p.name=name;users.put(name,p);p.send(Message.of(MessageType.LOGIN_OK,name));return;
        }
        if(p.name==null)throw new IllegalArgumentException("닉네임 등록 필요");
        switch(m.type()){
            case ROOM_LIST -> p.send(Message.of(MessageType.ROOM_LIST,rooms.values().stream().filter(r->!r.privateRoom)
                .map(r->r.id+","+r.title+","+r.peers.size()+","+r.active()).collect(Collectors.joining(";"))));
            case CREATE_ROOM -> {
                if(p.room!=null)throw new IllegalArgumentException("이미 방에 있음");
                String title=m.field(0),kind=m.field(1);
                if(title.isBlank()||title.length()>24||title.matches(".*[|,:;\r\n].*"))throw new IllegalArgumentException("방 이름 오류");
                if(!kind.equals("PUBLIC")&&!kind.equals("PRIVATE"))throw new IllegalArgumentException("공개 여부 오류");
                Room room=new Room(nextRoom++,title,kind.equals("PRIVATE"));rooms.put(room.id,room);join(p,room);
                if(room.privateRoom)p.send(Message.of(MessageType.ROOM_CODE,room.code));
            }
            case JOIN_ROOM -> {
                Room room=rooms.get(Integer.parseInt(m.field(0)));
                if(room==null||room.privateRoom)throw new IllegalArgumentException("공개방을 찾을 수 없음");join(p,room);
            }
            case JOIN_PRIVATE -> {
                Room room=rooms.values().stream().filter(r->r.privateRoom&&r.code.equals(m.field(0))).findFirst().orElseThrow(()->new IllegalArgumentException("입장 코드 오류"));join(p,room);
            }
            case LEAVE_ROOM -> leave(p,true);
            case READY -> {
                Room room=roomOf(p);if(room.game!=null)throw new IllegalArgumentException("이미 시작한 방: 로비에서 새 방을 생성하세요");
                if(!m.field(0).equals("true")&&!m.field(0).equals("false"))throw new IllegalArgumentException("준비 값 오류");
                room.ready.put(p.name,Boolean.parseBoolean(m.field(0)));room.state();
                if(room.peers.size()==config.roomCapacity()&&room.ready.values().stream().allMatch(Boolean::booleanValue)){room.game=games.create(room);room.game.start();}
            }
            case CHAT -> {
                Room room=roomOf(p);if(room.game!=null&&!room.game.canChat(p.name))throw new IllegalArgumentException("현재 채팅 불가");
                if(m.field(0).isBlank()||m.field(0).length()>500)throw new IllegalArgumentException("채팅 길이 오류");room.broadcast(Message.of(MessageType.CHAT,p.name,m.field(0)));
            }
            default -> {Room room=roomOf(p);if(room.game==null)throw new IllegalArgumentException("진행 중인 게임 없음");room.game.onPlayerMessage(p.name,m);}
        }
    }
    private Room roomOf(Peer p){if(p.room==null)throw new IllegalArgumentException("방에 입장해야 함");return p.room;}
    private void join(Peer p,Room room){
        if(p.room!=null||room.game!=null||room.peers.size()>=config.roomCapacity())throw new IllegalArgumentException("입장 불가: 정원 또는 게임 상태");
        p.room=room;room.peers.add(p);room.ready.put(p.name,false);room.broadcast(Message.of(MessageType.PLAYER_JOIN,p.name));room.state();
    }
    private void leave(Peer p,boolean reply){
        Room room=p.room;if(room==null){if(reply)throw new IllegalArgumentException("입장한 방 없음");return;}
        room.peers.remove(p);room.ready.remove(p.name);p.room=null;
        if(room.game!=null)room.game.onPlayerLeft(p.name);
        if(reply)p.send(Message.of(MessageType.ROOM_LEFT,Integer.toString(room.id)));
        if(room.peers.isEmpty())rooms.remove(room.id);
        else{room.broadcast(Message.of(MessageType.PLAYER_LEAVE,p.name));room.state();}
    }
    private synchronized void depart(Peer p){leave(p,false);if(p.name!=null)users.remove(p.name,p);}
    public void close(){
        if(!running.getAndSet(false))return;
        try{server.close();}catch(IOException ignored){}connections.forEach(Peer::closeSocket);scheduler.shutdownNow();
    }
    public static void main(String[] args) throws Exception {
        int port=args.length>0?Integer.parseInt(args[0]):5000;GameConfig config=GameConfig.defaults();
        if(Arrays.asList(args).contains("--fast"))config=new GameConfig(port,6,12,2,12,10,config.roleCounts());
        DemoServerMain server=new DemoServerMain(port,config);Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("[AI 보험본 연동 시연] TCP 127.0.0.1:"+server.port()+" / 직업·네트워크·로비는 demo 참조 구현");
        new CountDownLatch(1).await();
    }
}
