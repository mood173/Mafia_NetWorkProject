package com.mafia.client.scene;

import com.mafia.client.net.Connection;
import com.mafia.client.stream.Scene;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;

/** Nickname registration only. Server responses determine scene transitions. */
public final class MainScene implements Scene {
    private final Connection connection;
    private final JPanel panel=new JPanel(new GridBagLayout());
    private final JTextField host=new JTextField("localhost",20), port=new JTextField("5000",20), name=new JTextField(20);
    private final JButton enter=new JButton("서버에 접속");
    private final JLabel status=new JLabel("서버 주소와 닉네임을 입력하세요.");
    private boolean connected, busy;
    private int revision;
    private final Timer timeout;
    private final Object connectionWork=new Object();
    private LoginAttempt activeAttempt;
    private SwingWorker<Void,Void> loginWorker;

    private static final class LoginAttempt {
        private boolean cancelled;
        synchronized void cancel() { cancelled=true; }
        synchronized boolean isCancelled() { return cancelled; }
        synchronized boolean send(Connection connection,Message message) {
            if(cancelled) return false;
            connection.send(message);
            return true;
        }
    }
    public MainScene(Connection connection) {
        this.connection=connection;
        GridBagConstraints c=new GridBagConstraints(); c.insets=new Insets(8,8,8,8); c.fill=GridBagConstraints.HORIZONTAL;
        addRow("서버 주소",host,0,c); addRow("포트",port,1,c); addRow("닉네임",name,2,c);
        c.gridy=3; c.gridx=0; c.gridwidth=2; panel.add(status,c); c.gridy=4; panel.add(enter,c);
        enter.addActionListener(e->enter()); name.addActionListener(e->enter());
        timeout=new Timer(8000,e->fail("응답 시간이 초과되었습니다. 다시 접속하세요."));
        timeout.setRepeats(false);
    }
    private void addRow(String text,JTextField field,int row,GridBagConstraints c) {
        JLabel label=new JLabel(text); label.setLabelFor(field);
        c.gridy=row; c.gridx=0; panel.add(label,c); c.gridx=1; panel.add(field,c);
    }
    private void setBusy(boolean value) {
        busy=value; enter.setEnabled(!value); host.setEnabled(!value&&!connected); port.setEnabled(!value&&!connected); name.setEnabled(!value);
    }
    private void enter() {
        if(busy) return;
        String address=host.getText().trim(), nickname=name.getText().trim(); int number;
        try {
            number=Integer.parseInt(port.getText().trim());
            if(address.isEmpty() || number<1 || number>65535) throw new IllegalArgumentException("주소와 포트를 확인하세요");
            if(!nickname.matches("[\\p{L}\\p{N}_-]{1,16}")) throw new IllegalArgumentException("닉네임은 한글·영문·숫자·_- 1~16자입니다");
        } catch(IllegalArgumentException e) { status.setText(e.getMessage()); return; }
        int attempt=++revision; boolean reuse=connected;
        LoginAttempt request=new LoginAttempt(); activeAttempt=request;
        setBusy(true); status.setText("접속 및 닉네임 확인 중…"); timeout.restart();
        loginWorker=new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws IOException {
                // Serialize attempts so cleanup for a cancelled connect cannot close its replacement.
                synchronized(connectionWork) {
                    if(request.isCancelled()) return null;
                    if(!reuse) connection.connect(address,number);
                    if(!request.send(connection,Message.of(MessageType.LOGIN,nickname))) connection.close();
                }
                return null;
            }
            protected void done() {
                if(attempt!=revision) return;
                try { get(); connected=true; }
                catch(CancellationException e) { /* Cancellation already updated the UI. */ }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); fail("연결이 중단되었습니다"); }
                catch(ExecutionException e) { fail("연결 실패: "+e.getCause().getMessage()); }
            }
        };
        loginWorker.execute();
    }
    private void cancelAttempt() {
        revision++;
        if(activeAttempt!=null) activeAttempt.cancel();
        if(loginWorker!=null) loginWorker.cancel(true);
        activeAttempt=null; loginWorker=null;
        timeout.stop();
    }
    private void fail(String reason) { cancelAttempt(); connected=false; connection.close(); setBusy(false); status.setText(reason); }
    public JComponent view() { return panel; }
    public void onEnter() { cancelAttempt(); connected=false; setBusy(false); }
    public void onExit() { revision++; timeout.stop(); setBusy(false); }
    public void onMessage(Message message) {
        if(message.type()==MessageType.ERROR) {
            connected=true; timeout.stop(); setBusy(false); status.setText(message.field(0));
            // A rejected nickname keeps the socket; a disconnect must allow a fresh connect.
            if(message.field(0).equals("서버 연결이 끊겼습니다")) { cancelAttempt(); connected=false; setBusy(false); }
        }
    }
}
