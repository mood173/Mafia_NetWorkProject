package com.mafia.client.ui;
import javax.swing.JComponent;
import com.mafia.client.stream.ClientState;
import com.mafia.common.protocol.Message;
public interface ChatView {
    JComponent view();
    void accept(Message message);
    void render(ClientState state);
    void reset();
}
