package com.mafia.client.scene;
import com.mafia.client.net.Connection;
import com.mafia.common.protocol.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MainSceneTest {
    @Test void loginDoesNotBlockEdtAndNicknameRetryReusesConnection() throws Exception {
        CountDownLatch connecting=new CountDownLatch(1), release=new CountDownLatch(1), sent=new CountDownLatch(1);
        AtomicInteger connects=new AtomicInteger(), sends=new AtomicInteger();
        Connection fake=new Connection() {
            public void connect(String h,int p) {
                assertFalse(SwingUtilities.isEventDispatchThread()); connects.incrementAndGet(); connecting.countDown();
                try { assertTrue(release.await(3,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new RuntimeException(e); }
            }
            public void send(Message m) { assertEquals(MessageType.LOGIN,m.type()); sends.incrementAndGet(); sent.countDown(); }
            public void close() { }
            public void setListener(com.mafia.client.net.MessageListener l) { }
        };
        AtomicReference<MainScene> scene=new AtomicReference<>();
        AtomicReference<JButton> button=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            MainScene s=new MainScene(fake); scene.set(s); s.onEnter();
            java.util.List<JTextField> inputs=new java.util.ArrayList<>();
            for(Component c:s.view().getComponents()) {
                if(c instanceof JTextField f) inputs.add(f);
                if(c instanceof JButton b) button.set(b);
                if(c instanceof JLabel label && label.getLabelFor()!=null) assertNotNull(label.getLabelFor().getAccessibleContext().getAccessibleName());
            }
            inputs.get(2).setText("경탁"); button.get().doClick();
        });
        try {
            assertTrue(connecting.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->assertFalse(button.get().isEnabled()));
            release.countDown(); assertTrue(sent.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{ scene.get().onMessage(Message.of(MessageType.ERROR,"닉네임 중복")); button.get().doClick(); });
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            while(sends.get()<2&&System.nanoTime()<end) Thread.sleep(10);
            assertEquals(1,connects.get()); assertEquals(2,sends.get());
            SwingUtilities.invokeAndWait(()->{
                scene.get().onMessage(Message.of(MessageType.ERROR,"닉네임 중복"));
                JComponent view=scene.get().view(); view.setSize(960,720); view.doLayout();
                BufferedImage image=new BufferedImage(960,720,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=image.createGraphics(); view.paint(g); g.dispose();
                try { javax.imageio.ImageIO.write(image,"png",new java.io.File(System.getProperty("java.io.tmpdir"),"mafia-login-preview.png")); }
                catch(java.io.IOException e) { throw new RuntimeException(e); }
                scene.get().onExit();
            });
        } finally { release.countDown(); SwingUtilities.invokeAndWait(()->scene.get().onExit()); }
    }

    @Test void timedOutQueuedLoginMustNotConnectOrSend() throws Exception {
        CountDownLatch occupied=new CountDownLatch(10),release=new CountDownLatch(1),attempted=new CountDownLatch(1);
        java.util.List<SwingWorker<Void,Void>> blockers=new java.util.ArrayList<>();
        AtomicReference<MainScene> scene=new AtomicReference<>();
        AtomicInteger connects=new AtomicInteger(),sends=new AtomicInteger();
        for(int i=0;i<10;i++) {
            SwingWorker<Void,Void> worker=new SwingWorker<>() {
                protected Void doInBackground() throws Exception {
                    occupied.countDown(); release.await(); return null;
                }
            };
            blockers.add(worker);worker.execute();
        }
        Connection fake=new Connection() {
            public void connect(String host,int port) { connects.incrementAndGet();attempted.countDown(); }
            public void send(Message message) { sends.incrementAndGet();attempted.countDown(); }
            public void close() { }
            public void setListener(com.mafia.client.net.MessageListener listener) { }
        };
        try {
            assertTrue(occupied.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                MainScene view=new MainScene(fake);scene.set(view);view.onEnter();
                JTextField name=null;JButton button=null;
                for(Component c:view.view().getComponents()) {
                    if(c instanceof JTextField f)name=f;
                    if(c instanceof JButton b)button=b;
                }
                name.setText("expired");button.doClick();
                try {
                    var field=MainScene.class.getDeclaredField("timeout");field.setAccessible(true);
                    Timer timer=(Timer)field.get(view);
                    for(var listener:timer.getActionListeners())
                        listener.actionPerformed(new java.awt.event.ActionEvent(timer,0,"timeout"));
                } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
            });
            release.countDown();
            for(var blocker:blockers)blocker.get(3,TimeUnit.SECONDS);
            assertFalse(attempted.await(500,TimeUnit.MILLISECONDS),"expired queued request executed");
            assertEquals(0,connects.get());assertEquals(0,sends.get());
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(()->{ if(scene.get()!=null)scene.get().onExit(); });
        }
    }

    @Test void cancelledRunningConnectMustNotSendOrCloseRetry() throws Exception {
        CountDownLatch firstConnect=new CountDownLatch(1),release=new CountDownLatch(1),retrySent=new CountDownLatch(1);
        AtomicInteger connects=new AtomicInteger();
        java.util.List<String> logins=new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean open=new java.util.concurrent.atomic.AtomicBoolean();
        Connection fake=new Connection() {
            public void connect(String host,int port) {
                if(connects.incrementAndGet()==1) {
                    firstConnect.countDown();
                    // Model a slow connect/DNS operation that does not stop immediately on interruption.
                    boolean released=false;
                    while(!released)try { released=release.await(3,TimeUnit.SECONDS); }
                    catch(InterruptedException ignored) { }
                }
                open.set(true);
            }
            public void send(Message message) {
                if(!open.get())throw new IllegalStateException("retry connection was closed");
                logins.add(message.field(0));retrySent.countDown();
            }
            public void close() { open.set(false); }
            public void setListener(com.mafia.client.net.MessageListener listener) { }
        };
        AtomicReference<MainScene> scene=new AtomicReference<>();
        AtomicReference<JTextField> name=new AtomicReference<>();
        AtomicReference<JButton> button=new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(()->{
                MainScene view=new MainScene(fake);scene.set(view);view.onEnter();
                for(Component c:view.view().getComponents()) {
                    if(c instanceof JTextField f)name.set(f);
                    if(c instanceof JButton b)button.set(b);
                }
                name.get().setText("expired");button.get().doClick();
            });
            assertTrue(firstConnect.await(3,TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(()->{
                try {
                    var field=MainScene.class.getDeclaredField("timeout");field.setAccessible(true);
                    Timer timer=(Timer)field.get(scene.get());
                    for(var listener:timer.getActionListeners())
                        listener.actionPerformed(new java.awt.event.ActionEvent(timer,0,"timeout"));
                } catch(ReflectiveOperationException e) { throw new RuntimeException(e); }
                name.get().setText("retry");button.get().doClick();
            });
            release.countDown();
            assertTrue(retrySent.await(3,TimeUnit.SECONDS));
            assertEquals(java.util.List.of("retry"),logins);
            assertEquals(2,connects.get());assertTrue(open.get());
        } finally {
            release.countDown();
            SwingUtilities.invokeAndWait(()->{ if(scene.get()!=null)scene.get().onExit(); });
        }
    }
}
