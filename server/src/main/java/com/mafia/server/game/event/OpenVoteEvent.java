package com.mafia.server.game.event;

import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import java.util.List;

/** 공개 투표: 표가 받아들여질 때마다 누가 누구를 찍었는지 방 전체에 알린다. */
public final class OpenVoteEvent implements DayEvent {
    @Override public String code() { return "OPEN_VOTE"; }

    @Override public String description() {
        return "공개 투표: 오늘은 누가 누구에게 투표했는지 바로 공개됩니다.";
    }

    @Override public List<Message> onVote(String voter, String target) {
        return List.of(Message.of(MessageType.VOTE_CAST, voter, target));
    }
}
