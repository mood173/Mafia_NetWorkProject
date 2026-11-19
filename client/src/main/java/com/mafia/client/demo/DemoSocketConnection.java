package com.mafia.client.demo;
import com.mafia.client.net.*;
import com.mafia.common.protocol.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** 시연용 전송 어댑터. 경탁의 Connection 구현으로 교체할 수 있다. */
public final class DemoSocketConnection implements Connection {
    private final MessageCodec codec;
    private final ExecutorService sender=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"demo-client-send");t.setDaemon(true);return t;});
    private volatile Socket socket;private MessageListener listener;
    public DemoSocketConnection(MessageCodec codec){this.codec=codec;}
    public void setListener(MessageListener listener){this.listener=listener;}
    public void connect(String host,int port) throws IOException {
        if(socket!=null&&!socket.isClosed())throw new IOException("이미 연결됨. 기존 연결 종료 후 다시 시도");
        Socket next=new Socket();next.connect(new InetSocketAddress(host,port),1500);socket=next;
        Thread reader=new Thread(()->read(next),"demo-client-receive");reader.setDaemon(true);reader.start();
    }
    public void send(Message message){
        String line=codec.encode(message);Socket current=socket;
        if(current==null||current.isClosed())throw new IllegalStateException("서버에 연결되지 않음");
        sender.execute(()->{try{var out=new OutputStreamWriter(current.getOutputStream(),StandardCharsets.UTF_8);out.write(line+"\n");out.flush();}catch(IOException e){closeSocket(current);}});
    }
    private void read(Socket current){
        try(var input=new BufferedReader(new InputStreamReader(current.getInputStream(),StandardCharsets.UTF_8))){
            String line;while((line=input.readLine())!=null){if(line.length()>MessageCodec.MAX_LENGTH)throw new IOException("너무 긴 메시지");listener.onMessage(codec.decode(line,Direction.SERVER_TO_CLIENT));}
        }catch(IOException|IllegalArgumentException e){System.err.println("[시연 연결] "+e.getMessage());}
        finally{closeSocket(current);if(socket==current)listener.onDisconnected();}
    }
    private void closeSocket(Socket current){try{current.close();}catch(IOException ignored){}}
    public void close(){Socket current=socket;if(current!=null)closeSocket(current);}
}
