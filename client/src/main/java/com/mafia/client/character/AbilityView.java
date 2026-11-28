package com.mafia.client.character;
import javax.swing.JComponent;
/** 게임 화면에 붙는 능력 패널. GameScene은 이것만 보고, 실제로 어떤 패널이 붙었는지는 모른다. */
public interface AbilityView { JComponent view(); void render(); default void unlockOnError() { } }
