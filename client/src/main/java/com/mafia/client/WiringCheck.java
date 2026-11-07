package com.mafia.client;

import com.mafia.client.stream.SceneId;
import com.mafia.client.stream.SceneRegistry;

import java.util.List;

/** 클라이언트 시작 검사: 모든 SceneId 에 씬이 등록됐는지. */
public final class WiringCheck {

    private WiringCheck() {
    }

    public static void orFail(SceneRegistry scenes) {
        List<SceneId> missing = scenes.missing();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("등록되지 않은 씬: " + missing);
        }
    }
}
