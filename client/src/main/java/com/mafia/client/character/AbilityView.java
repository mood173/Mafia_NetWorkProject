package com.mafia.client.character;
import javax.swing.JComponent;
/** 팀원 능력 패널을 붙이는 뷰 계약. GameScene은 구체 구현을 모른다. */
public interface AbilityView { JComponent view(); void render(); }
