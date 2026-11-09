package com.mafia.server.character;
import java.util.List;
import java.util.Map;
public record NightResult(List<String> deaths, Map<String,PoliceFinding> findings) {
    public NightResult { deaths=List.copyOf(deaths); findings=Map.copyOf(findings); }
    public record PoliceFinding(String target, boolean mafia) { }
}
