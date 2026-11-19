package com.mafia.server.game;
import com.mafia.common.protocol.Message;
public interface GameEventHandler {
    void start();
    void onPlayerMessage(String playerName, Message message);
    void onPlayerLeft(String playerName);
}
