package com.mafia.client.net;

import com.mafia.common.protocol.Message;

/** 서버에서 메시지가 오면 불린다. 구현은 GameStream (강민준). */
public interface MessageListener {

    void onMessage(Message message);

    void onDisconnected();
}
