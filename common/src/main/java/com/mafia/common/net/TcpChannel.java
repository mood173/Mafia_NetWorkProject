package com.mafia.common.net;

import com.mafia.common.protocol.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One reader and one bounded FIFO writer per socket. Callbacks never hold domain locks. */
public final class TcpChannel implements AutoCloseable {
    private final Socket socket;
    private final MessageCodec codec;
    private final Direction inbound;
    private final Consumer<Message> receiver;
    private final Consumer<String> invalid;
    private final Runnable disconnected;
    private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(256);
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final Thread reader, writer;

    public TcpChannel(Socket socket, MessageCodec codec, Direction inbound,
                      Consumer<Message> receiver, Consumer<String> invalid, Runnable disconnected) {
        this.socket=socket; this.codec=codec; this.inbound=inbound;
        this.receiver=receiver; this.invalid=invalid; this.disconnected=disconnected;
        reader=new Thread(this::read, "mafia-receive");
        writer=new Thread(this::write, "mafia-send");
        reader.setDaemon(true); writer.setDaemon(true);
    }
    public void start() { writer.start(); reader.start(); }
    public boolean isOpen() { return open.get(); }
    public void send(Message message) {
        Direction outbound=inbound==Direction.CLIENT_TO_SERVER ? Direction.SERVER_TO_CLIENT : Direction.CLIENT_TO_SERVER;
        String line=codec.encode(message,outbound);
        if(!open.get()) throw new IllegalStateException("연결이 종료되었습니다");
        if(!queue.offer(line)) { close(); throw new IllegalStateException("송신 대기열이 가득 찼습니다"); }
    }
    private void read() {
        try(Reader in=new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8)) {
            StringBuilder line=new StringBuilder(); boolean carriage=false; int c;
            while(open.get() && (c=in.read())!=-1) {
                if(c=='\n') {
                    try { receiver.accept(codec.decode(line.toString(),inbound)); }
                    catch(IllegalArgumentException e) { invalid.accept(e.getMessage()); }
                    line.setLength(0); carriage=false;
                } else if(c=='\r') {
                    if(carriage) throw new IOException("잘못된 개행");
                    carriage=true;
                } else {
                    if(carriage || line.length()>=MessageCodec.MAX_LENGTH) throw new IOException("잘못된 줄 또는 길이 초과");
                    line.append((char)c);
                }
            }
        } catch(IOException e) {
            // Peer disconnects and invalid frames converge on close.
        } catch(RuntimeException e) {
            System.getLogger(TcpChannel.class.getName()).log(System.Logger.Level.WARNING,"수신 처리 실패",e);
        } finally { close(); }
    }
    private void write() {
        try(Writer out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))) {
            while(open.get()) { out.write(queue.take()); out.write('\n'); out.flush(); }
        } catch(IOException | InterruptedException e) {
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally { close(); }
    }
    public void close() {
        if(!open.getAndSet(false)) return;
        try { socket.close(); } catch(IOException ignored) { }
        reader.interrupt(); writer.interrupt(); queue.clear(); disconnected.run();
    }
}
