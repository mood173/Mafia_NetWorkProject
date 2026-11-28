package com.mafia.server.game;
import java.util.LinkedHashMap;
import java.util.Map;
/**
 * 투표 집계. 기권(빈 값)은 세지 않고, 가장 많이 받은 사람이 한 명일 때만 처형한다.
 * 동점이거나 모두 기권하면 처형하지 않는다. 판 진행과 떨어져 있어서 규칙만 따로 검사할 수 있다.
 */
public final class VoteCounter {
    /** eliminated: 처형할 사람(없으면 빈 값), totals: 사람별 득표 */
    public record Result(String eliminated, Map<String,Integer> totals) { }
    public Result count(Map<String,String> votes) {
        Map<String,Integer> totals=new LinkedHashMap<>();
        votes.values().stream().filter(v -> !v.isEmpty()).forEach(v -> totals.merge(v,1,Integer::sum));
        int max=totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var winners=totals.entrySet().stream().filter(e -> e.getValue()==max).map(Map.Entry::getKey).toList();
        // 최다 득표자가 두 명 이상이면 동점이라 처형하지 않는다
        return new Result(winners.size()==1 ? winners.get(0) : "",Map.copyOf(totals));
    }
}
