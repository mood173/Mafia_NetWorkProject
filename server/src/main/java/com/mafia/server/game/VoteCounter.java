package com.mafia.server.game;
import java.util.LinkedHashMap;
import java.util.Map;
public final class VoteCounter {
    public record Result(String eliminated, Map<String,Integer> totals) { }
    public Result count(Map<String,String> votes) {
        Map<String,Integer> totals=new LinkedHashMap<>();
        votes.values().stream().filter(v -> !v.isEmpty()).forEach(v -> totals.merge(v,1,Integer::sum));
        int max=totals.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var winners=totals.entrySet().stream().filter(e -> e.getValue()==max).map(Map.Entry::getKey).toList();
        return new Result(winners.size()==1 ? winners.get(0) : "",Map.copyOf(totals));
    }
}
