package com.mafia.client.stream;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 등록부: SceneId → Scene. ClientMain 에서만 등록한다. */
public final class SceneRegistry {

    private final Map<SceneId, Scene> scenes = new EnumMap<>(SceneId.class);

    public void register(SceneId id, Scene scene) {
        if (scenes.putIfAbsent(id, scene) != null) {
            throw new IllegalStateException(id + " 씬이 이미 등록됨");
        }
    }

    public Scene get(SceneId id) {
        Scene scene = scenes.get(id);
        if (scene == null) {
            throw new IllegalStateException(id + " 씬이 등록되지 않음");
        }
        return scene;
    }

    public List<SceneId> missing() {
        List<SceneId> missing = new ArrayList<>();
        for (SceneId id : SceneId.values()) {
            if (!scenes.containsKey(id)) missing.add(id);
        }
        return missing;
    }

    Map<SceneId, Scene> all() {
        return scenes;
    }
}
