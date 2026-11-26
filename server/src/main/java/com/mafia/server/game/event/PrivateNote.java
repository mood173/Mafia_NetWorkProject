package com.mafia.server.game.event;

/** 한 사람에게만 보내는 안내. */
public record PrivateNote(String to, String text) {
}
