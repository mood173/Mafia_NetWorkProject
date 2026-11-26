package com.mafia.server.game;

import com.mafia.common.model.Phase;
import com.mafia.common.model.Team;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.common.protocol.RejectCode;
import com.mafia.common.protocol.Rejection;
import com.mafia.server.character.*;
import com.mafia.server.game.event.DayContext;
import com.mafia.server.game.event.DayEvent;
import com.mafia.server.game.event.EventDirector;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 한 판의 서버 측 상태 소유자이자 진행자.
 * 입력: 사용자 커맨드, 시간 만료, 전원 제출, 접속 끊김.
 * 규칙 조회: PhaseTable / VoteCounter / WinChecker / CharacterService.
 * 그날의 사건(EventDirector)은 단계 시간, 채팅 전달 모양, 표 접수 알림에만 끼어든다.
 * 상태 변경: 생존자는 markDead(), 단계는 transitionTo()에서만 변경한다.
 * 결과는 RoomChannel로 알린다. Socket과 Swing은 모른다.
 */
public final class GameSession implements GameEventHandler {
    private enum CompletionReason {
        DEADLINE, ALL_NIGHT_ACTIONS_SUBMITTED, ALL_VOTES_CAST
    }

    private final RoomChannel channel;
    private final Map<String, RoleAbility> roles;
    private final LinkedHashSet<String> alive;
    private final CharacterService characters;
    private final PhaseTimer timer;
    private final PhaseTable phaseRules;
    private final VoteCounter voteCounter;
    private final WinChecker winChecker;
    private final EventDirector events;
    private final Map<String, String> votes = new LinkedHashMap<>();

    private Phase phase = Phase.WAITING;
    private PhaseTimer.Ticket phaseTimeout;
    private long phaseRevision;
    private long phaseDeadlineNanos;
    private boolean started;
    private boolean deferringNightCompletion;
    private long deferredNightCompletion = -1;

    public GameSession(RoomChannel channel, Map<String, RoleAbility> roles,
                       CharacterService characters, PhaseTimer timer,
                       PhaseTable phaseRules, VoteCounter voteCounter, WinChecker winChecker) {
        this(channel, roles, characters, timer, phaseRules, voteCounter, winChecker, EventDirector.none());
    }

    public GameSession(RoomChannel channel, Map<String, RoleAbility> roles,
                       CharacterService characters, PhaseTimer timer,
                       PhaseTable phaseRules, VoteCounter voteCounter, WinChecker winChecker,
                       EventDirector events) {
        this.channel = channel;
        this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        this.alive = new LinkedHashSet<>(roles.keySet());
        this.characters = characters;
        this.timer = timer;
        this.phaseRules = phaseRules;
        this.voteCounter = voteCounter;
        this.winChecker = winChecker;
        this.events = events;
    }

    @Override
    public synchronized void start() {
        if (started) throw new IllegalStateException("이미 시작한 판");
        started = true;
        channel.broadcast(Message.of(MessageType.GAME_START, String.join(",", roles.keySet())));
        roles.forEach((name, role) -> channel.send(name, Message.of(MessageType.ROLE, role.name().name())));
        transitionTo(Phase.NIGHT);
    }

    @Override
    public synchronized void onPlayerMessage(String actor, Message message) {
        if (!started || !alive.contains(actor)) {
            reject(actor, message.type(), RejectCode.NOT_ALLOWED_NOW, "살아 있는 참가자만 행동 가능");
            return;
        }
        if (!phaseRules.get(phase).accepts(message.type())) {
            reject(actor, message.type(), RejectCode.NOT_ALLOWED_NOW, "현재 단계에서 할 수 없는 행동");
            return;
        }
        if (message.type() == MessageType.VOTE) submitVote(actor, message.field(0));
        else submitNightAction(actor, message);
    }

    private void submitNightAction(String actor, Message message) {
        ActionType action = switch (message.type()) {
            case MAFIA_ATTACK -> ActionType.ATTACK;
            case DOCTOR_HEAL -> ActionType.HEAL;
            case POLICE_CHECK -> ActionType.CHECK;
            default -> null;
        };
        if (action == null) {
            reject(actor, message.type(), RejectCode.UNKNOWN, "별도 채팅 처리 필요");
            return;
        }
        if (!alive.contains(message.field(0))) {
            reject(actor, message.type(), RejectCode.INVALID_TARGET, "살아 있는 대상만 선택 가능");
            return;
        }
        // 판은 단계·생존을 검사한다. 직업/대상 규칙은 캐릭터가 판단한다.
        // 마지막 제출이면 캐릭터가 submit 안에서 바로 밤 마감을 요청하므로,
        // 수락 안내가 밤 결과보다 먼저 가도록 제출하는 동안에는 마감을 미뤘다가 안내 뒤에 실행한다.
        SubmitResult result;
        deferringNightCompletion = true;
        try {
            result = characters.submit(actor, new NightAction(action, message.field(0)));
        } finally {
            deferringNightCompletion = false;
        }
        if (!result.accepted()) reject(actor, message.type(), result.code(), result.reason());
        else notifyActor(actor, message.field(0) + "에게 능력을 사용했습니다.");
        runDeferredNightCompletion();
    }

    /** 미뤄 둔 밤 마감이 있으면 지금 실행한다. 그 사이 판이 끝났으면 completePhase가 무시한다. */
    private void runDeferredNightCompletion() {
        if (deferredNightCompletion < 0) return;
        long revision = deferredNightCompletion;
        deferredNightCompletion = -1;
        completePhase(revision, CompletionReason.ALL_NIGHT_ACTIONS_SUBMITTED);
    }

    /** 캐릭터의 "전원 제출" 콜백. 제출·이탈 처리 중이면 안내·승패 확인 뒤로 미룬다. */
    private synchronized void requestNightCompletion(long revision) {
        if (deferringNightCompletion) deferredNightCompletion = revision;
        else completePhase(revision, CompletionReason.ALL_NIGHT_ACTIONS_SUBMITTED);
    }

    private void submitVote(String actor, String target) {
        if (votes.containsKey(actor)) {
            reject(actor, MessageType.VOTE, RejectCode.ALREADY_DONE, "이미 투표함");
            return;
        }
        if (!target.isEmpty() && (!alive.contains(target) || target.equals(actor))) {
            reject(actor, MessageType.VOTE, RejectCode.INVALID_TARGET, "투표 대상 오류");
            return;
        }
        votes.put(actor, target);
        notifyActor(actor, target.isEmpty() ? "기권하셨습니다." : target + "님을 투표하셨습니다.");
        events.onVote(actor, target).forEach(channel::broadcast);
        if (hasEveryoneVoted()) completePhase(phaseRevision, CompletionReason.ALL_VOTES_CAST);
    }

    /** 조회: 조건만 반환하며 단계 전환/송신을 하지 않는다. */
    private boolean hasEveryoneVoted() {
        return votes.keySet().containsAll(alive);
    }

    @Override
    public synchronized void onPlayerLeft(String actor) {
        if (!started || phase == Phase.GAME_END || !alive.contains(actor)) return;
        markDead(actor, "DISCONNECT");
        votes.remove(actor);
        votes.entrySet().removeIf(vote -> vote.getValue().equals(actor));
        // 안 낸 마지막 능력자가 나가면 캐릭터가 여기서 밤 마감을 요청한다.
        // 승패를 먼저 보고(마피아가 나갔으면 그대로 종료), 이어지는 판일 때만 밤을 마감한다.
        deferringNightCompletion = true;
        try {
            characters.onPlayerLeft(actor);
        } finally {
            deferringNightCompletion = false;
        }
        if (finishGameIfWon()) {
            deferredNightCompletion = -1;
            return;
        }
        runDeferredNightCompletion();
        if (phase == Phase.VOTE && hasEveryoneVoted()) {
            completePhase(phaseRevision, CompletionReason.ALL_VOTES_CAST);
        }
    }

    /** 시간 만료/전원 제출 이벤트가 들어오는 단계 마감의 단일 진입점. */
    private synchronized void completePhase(long expectedRevision, CompletionReason reason) {
        if (!started || phase == Phase.GAME_END || expectedRevision != phaseRevision) return;
        if (!isCompletionReasonValid(reason)) return;

        cancelPhaseTimeout();
        // 마감 처리 중에 이전 콜백이 다시 와도 같은 단계를 두 번 완료하지 않는다.
        phaseRevision++;
        switch (phase) {
            case NIGHT -> finishNight();
            case VOTE -> finishVoting();
            case DAY_RESULT, DAY_TALK -> transitionTo(phaseRules.get(phase).next());
            default -> throw new IllegalStateException("마감할 수 없는 단계: " + phase);
        }
    }

    private boolean isCompletionReasonValid(CompletionReason reason) {
        return switch (reason) {
            case DEADLINE -> phase != Phase.WAITING && phase != Phase.GAME_END;
            case ALL_NIGHT_ACTIONS_SUBMITTED -> phase == Phase.NIGHT;
            case ALL_VOTES_CAST -> phase == Phase.VOTE;
        };
    }

    private void finishNight() {
        NightResult result = characters.endNight();
        result.deaths().forEach(name -> markDead(name, "ATTACK"));
        result.findings().forEach((officer, finding) -> channel.send(officer,
                Message.of(MessageType.POLICE_RESULT, finding.target(), Boolean.toString(finding.mafia()))));
        channel.broadcast(Message.of(MessageType.NIGHT_RESULT, String.join(",", result.deaths())));
        if (finishGameIfWon()) return;
        transitionTo(Phase.DAY_RESULT);
        // "아침이 밝았습니다" 뒤에 방 전체로 알린다. 누구를 살렸는지는 밝히지 않는다.
        if (result.savedByDoctor())
            channel.broadcast(Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 의사가 시민을 살렸습니다!"));
    }

    private void finishVoting() {
        VoteCounter.Result result = voteCounter.count(votes);
        String tally = result.totals().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(","));
        channel.broadcast(Message.of(MessageType.VOTE_RESULT, result.eliminated(), tally));
        if (!result.eliminated().isEmpty()) markDead(result.eliminated(), "VOTE");
        if (!finishGameIfWon()) transitionTo(phaseRules.get(Phase.VOTE).next());
    }

    /** 명령: 실제 단계 변경, 공지, 이전 타이머 정리와 다음 타이머 예약. */
    private void transitionTo(Phase next) {
        cancelPhaseTimeout();
        phase = next;
        long enteredRevision = ++phaseRevision;
        // 사건은 아침에 작동하고 밤에 들어가거나 판이 끝나면 풀린다. 시간은 작동 중인 사건을 한 번 거친다.
        if (next == Phase.NIGHT || next == Phase.GAME_END) events.endDay();
        Optional<DayEvent> todaysEvent = next == Phase.DAY_RESULT ? events.newDay() : Optional.empty();
        int seconds = events.seconds(next, phaseRules.get(next).seconds());
        phaseDeadlineNanos = timer.nowNanos() + seconds * 1_000_000_000L;
        if (next == Phase.VOTE) votes.clear();
        channel.broadcast(Message.of(MessageType.PHASE_CHANGE, next.name(), Integer.toString(seconds)));
        broadcastState();
        // 단계 안내는 단계를 바꾸는 이곳에서만 보낸다. 밤이 즉시 끝나는 경우에도 순서가 맞도록 타이머·밤 시작보다 먼저.
        announce(next);
        todaysEvent.ifPresent(this::announceEvent);

        if (next != Phase.GAME_END && next != Phase.WAITING) {
            phaseTimeout = timer.schedule(seconds,
                    () -> completePhase(enteredRevision, CompletionReason.DEADLINE));
        }
        if (next == Phase.NIGHT) {
            characters.beginNight(Set.copyOf(alive), roles, () -> requestNightCompletion(enteredRevision));
        }
    }

    /** 명령: 승자가 있으면 실제로 종료하며, 반환값은 '이미 종료했는지'다. */
    private boolean finishGameIfWon() {
        // 승리 조건 계산만 하는 조회 책임은 WinChecker.winner()에 있다.
        var winner = winChecker.winner(alive.stream().map(name -> roles.get(name).team()).toList());
        if (winner.isEmpty()) return false;
        transitionTo(Phase.GAME_END);
        String reveal = roles.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue().name())
                .collect(Collectors.joining(","));
        channel.broadcast(Message.of(MessageType.GAME_END, winner.get().name(), reveal));
        return true;
    }

    private void markDead(String name, String cause) {
        if (alive.remove(name)) channel.broadcast(Message.of(MessageType.PLAYER_DIED, name, cause));
    }

    /** 거절은 요청한 사람에게만 보낸다. 어떤 요청이 왜 거절됐는지를 함께 담아 화면이 구별하게 한다. */
    private void reject(String name, MessageType request, RejectCode code, String reason) {
        channel.send(name, Rejection.of(reason, request, code).toMessage());
    }

    /** 밤 시작(첫 밤 포함)과 아침(밤 → 낮 결과)에 방 전체로 보내는 시스템 안내. */
    private void announce(Phase next) {
        switch (next) {
            case NIGHT -> channel.broadcast(Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 밤이 찾아왔습니다."));
            case DAY_RESULT -> channel.broadcast(Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: 아침이 밝았습니다."));
            default -> { }
        }
    }

    /** 오늘의 사건을 방 전체에 알리고, 사건이 정한 사람에게만 가는 안내가 있으면 보낸다. */
    private void announceEvent(DayEvent event) {
        channel.broadcast(Message.of(MessageType.EVENT, event.code(), event.description()));
        Map<String, Team> teams = new LinkedHashMap<>();
        alive.forEach(name -> teams.put(name, roles.get(name).team()));
        event.onStart(new DayContext(List.copyOf(alive), teams, events.random()))
                .forEach(note -> notifyActor(note.to(), note.text()));
    }

    /** 행동을 받아들였다는 개인 안내. 본인에게만 보낸다. */
    private void notifyActor(String name, String text) {
        channel.send(name, Message.of(MessageType.SYSTEM_CHAT, "[SYSTEM]: " + text));
    }

    private void cancelPhaseTimeout() {
        if (phaseTimeout == null) return;
        phaseTimeout.cancel();
        phaseTimeout = null;
    }

    private void broadcastState() {
        channel.broadcast(Message.of(MessageType.GAME_STATE, phase.name(),
                String.join(",", alive), Integer.toString(remainingSeconds())));
    }

    public synchronized Phase phase() { return phase; }
    public synchronized Set<String> alive() { return Set.copyOf(alive); }

    public synchronized boolean canChat(String name) {
        return alive.contains(name) && (phase == Phase.DAY_TALK || phase == Phase.VOTE);
    }

    /** 게임 중 채팅 한 줄을 방에 전달할 메시지. 채팅을 중계하는 쪽이 canChat을 통과한 뒤 부른다. */
    public synchronized Message chatMessage(String name, String text) {
        return events.chat(name, text);
    }

    public synchronized int remainingSeconds() {
        if (phase == Phase.GAME_END) return 0;
        return (int) Math.max(0, (phaseDeadlineNanos - timer.nowNanos() + 999_999_999L) / 1_000_000_000L);
    }
}
