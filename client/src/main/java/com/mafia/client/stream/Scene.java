package com.mafia.client.stream;

import com.mafia.common.protocol.Message;

import javax.swing.JComponent;

/** 화면 하나. 씬끼리는 서로 모르고, 전환은 GameStream 에게만 요청한다. */
public interface Scene {

    JComponent view();

    /** 이 씬이 보일 때 */
    default void onEnter() {
    }

    /** 다른 씬으로 넘어가기 직전 */
    default void onExit() {
    }

    /** 이 씬이 보이는 동안 온 서버 메시지. 필요한 것만 처리한다. */
    void onMessage(Message message);
}
