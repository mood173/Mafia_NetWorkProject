package com.mafia.server.game;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;
import com.mafia.common.protocol.MessageType;
import com.mafia.server.character.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 한 판의 서버 측 상태 소유자이자 진행자.
 * 입력: 사용자 커맨드, 시간 만료, 전원 제출, 접속 끊김.
 * 규칙 조회: PhaseTable / VoteCounter / WinChecker / CharacterService.
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
    private final Map<String, String> votes = new LinkedHashMap<>();

    private Phase phase = Phase.WAITING;
    private PhaseTimer.Ticket phaseTimeout;
    private long phaseRevision;
    private long phaseDeadlineNanos;
    private boolean started;

    public GameSession(RoomChannel channel, Map<String, RoleAbility> roles,
                       CharacterService characters, PhaseTimer timer,
                       PhaseTable phaseRules, VoteCounter voteCounter, WinChecker winChecker) {
        this.channel = channel;
        this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        this.alive = new LinkedHashSet<>(roles.keySet());
        this.characters = characters;
        this.timer = timer;
        this.phaseRules = phaseRules;
        this.voteCounter = voteCounter;
        this.winChecker = winChecker;
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
            reject(actor, "살아 있는 참가자만 행동 가능");
            return;
        }
        if (!phaseRules.get(phase).accepts(message.type())) {
            reject(actor, "현재 단계에서 할 수 없는 행동");
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
            reject(actor, "별도 채팅 처리 필요");
            return;
        }
        if (!alive.contains(message.field(0))) {
            reject(actor, "살아 있는 대상만 선택 가능");
            return;
        }
        // 판은 단계·생존을 검사한다. 직업/대상 규칙은 캐릭터가 판단한다.
        SubmitResult result = characters.submit(actor, new NightAction(action, message.field(0)));
        if (!result.accepted()) reject(actor, result.reason());
    }

    private void submitVote(String actor, String target) {
        if (votes.containsKey(actor)) {
            reject(actor, "이미 투표함");
            return;
        }
        if (!target.isEmpty() && (!alive.contains(target) || target.equals(actor))) {
            reject(actor, "투표 대상 오류");
            return;
        }
        votes.put(actor, target);
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
        characters.onPlayerLeft(actor);
        if (finishGameIfWon()) return;
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
        if (!finishGameIfWon()) transitionTo(Phase.DAY_RESULT);
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
        int seconds = phaseRules.get(next).seconds();
        phaseDeadlineNanos = timer.nowNanos() + seconds * 1_000_000_000L;
        if (next == Phase.VOTE) votes.clear();
        channel.broadcast(Message.of(MessageType.PHASE_CHANGE, next.name(), Integer.toString(seconds)));
        broadcastState();

        if (next != Phase.GAME_END && next != Phase.WAITING) {
            phaseTimeout = timer.schedule(seconds,
                    () -> completePhase(enteredRevision, CompletionReason.DEADLINE));
        }
        if (next == Phase.NIGHT) {
            characters.beginNight(Set.copyOf(alive), roles,
                    () -> completePhase(enteredRevision, CompletionReason.ALL_NIGHT_ACTIONS_SUBMITTED));
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

    private void reject(String name, String reason) {
        channel.send(name, Message.of(MessageType.ERROR, reason));
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

    public synchronized int remainingSeconds() {
        if (phase == Phase.GAME_END) return 0;
        return (int) Math.max(0, (phaseDeadlineNanos - timer.nowNanos() + 999_999_999L) / 1_000_000_000L);
    }
}
