package com.mafia.server.character;
import java.util.List;
import java.util.Map;
/** 밤 결과. savedByDoctor는 마피아의 공격을 의사가 막았는지 (대상 이름은 공개하지 않는다). */
public record NightResult(List<String> deaths, Map<String,PoliceFinding> findings, boolean savedByDoctor) {
    public NightResult { deaths=List.copyOf(deaths); findings=Map.copyOf(findings); }
    /** 보호 여부를 따지지 않는 구현(데모·검사용)은 기존처럼 두 값만 준다. */
    public NightResult(List<String> deaths, Map<String,PoliceFinding> findings) { this(deaths, findings, false); }
    public record PoliceFinding(String target, boolean mafia) { }
}
