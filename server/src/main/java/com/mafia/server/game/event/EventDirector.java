package com.mafia.server.game.event;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * 사건 관리자. 남은 사건 목록(덱)과 작동 중인 사건 하나만 가진다.
 * 판(GameSession)이 묻는 것을 작동 중인 사건에게 그대로 넘기고, 없으면 평소 값을 돌려준다.
 * GameSession의 락 안에서만 불린다.
 */
public final class EventDirector {
    /** 방장이 고를 수 있는 한 판의 사건 횟수 상한. */
    public static final int MAX_EVENTS = 3;

    private final Deque<DayEvent> deck;
    private final Random random;
    private DayEvent active;

    private EventDirector(List<DayEvent> order, Random random) {
        this.deck = new ArrayDeque<>(order);
        this.random = random;
    }

    /** 사건 없는 판. */
    public static EventDirector none() {
        return new EventDirector(List.of(), new Random(0));
    }

    /** 주어진 순서 그대로 하루에 하나씩 꺼낸다. */
    public static EventDirector inOrder(List<DayEvent> order, Random random) {
        return new EventDirector(order, random);
    }

    /** 종류를 섞어 앞에서 count개를 덱으로 삼는다. 같은 사건은 한 판에 한 번만 나온다. */
    public static EventDirector shuffled(List<DayEvent> kinds, int count, Random random) {
        if (count < 0 || count > MAX_EVENTS) {
            throw new IllegalArgumentException("사건 횟수는 0~" + MAX_EVENTS + "입니다");
        }
        if (count == 0) return new EventDirector(List.of(), random); // 사건 없는 판은 무작위 값을 쓰지 않는다
        List<DayEvent> order = new ArrayList<>(kinds);
        Collections.shuffle(order, random);
        return new EventDirector(order.subList(0, Math.min(count, order.size())), random);
    }

    /** 아침. 어제 사건을 풀고 덱에서 하나를 꺼내 작동시킨다. 덱이 비었으면 사건 없는 날이다. */
    public Optional<DayEvent> newDay() {
        active = deck.poll();
        return Optional.ofNullable(active);
    }

    /** 밤에 들어가거나 판이 끝날 때. 작동 중인 사건을 푼다. */
    public void endDay() {
        active = null;
    }

    public Optional<DayEvent> active() {
        return Optional.ofNullable(active);
    }

    public int remaining() {
        return deck.size();
    }

    public Random random() {
        return random;
    }

    public int seconds(Phase phase, int base) {
        return active == null ? base : active.seconds(phase, base);
    }

    public Message chat(String sender, String text) {
        return active == null ? Message.of(MessageType.CHAT, sender, text) : active.chat(sender, text);
    }

    public List<Message> onVote(String voter, String target) {
        return active == null ? List.of() : active.onVote(voter, target);
    }
}
