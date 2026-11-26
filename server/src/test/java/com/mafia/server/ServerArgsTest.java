package com.mafia.server;

import com.mafia.server.engine.GameConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 서버 실행 인자: [주소] [포트] [--demo]. 시연용으로 단계 시간만 짧게 바꾼다. */
final class ServerArgsTest {
    private final GameConfig defaults = GameConfig.defaults();

    @Test
    void noArgumentsListenOnEveryInterfaceWithDefaultTimes() {
        ServerArgs args = ServerArgs.parse(new String[0]);
        assertEquals("0.0.0.0", args.host());
        assertEquals(defaults.port(), args.port());
        assertEquals(defaults, args.config());
        assertFalse(args.demo());
    }

    @Test
    void hostAndPortArePositional() {
        ServerArgs args = ServerArgs.parse(new String[] {"127.0.0.1", "6000"});
        assertEquals("127.0.0.1", args.host());
        assertEquals(6000, args.port());
        assertEquals(defaults, args.config());
    }

    @Test
    void demoFlagShortensOnlyThePhaseTimes() {
        ServerArgs args = ServerArgs.parse(new String[] {"127.0.0.1", "5000", "--demo"});
        GameConfig demo = args.config();
        assertTrue(args.demo());
        assertEquals(ServerArgs.DEMO_NIGHT, demo.nightSeconds());
        assertEquals(ServerArgs.DEMO_DAY_RESULT, demo.dayResultSeconds());
        assertEquals(ServerArgs.DEMO_TALK, demo.talkSeconds());
        assertEquals(ServerArgs.DEMO_VOTE, demo.voteSeconds());
        assertTrue(demo.nightSeconds() < defaults.nightSeconds() && demo.talkSeconds() < defaults.talkSeconds());
        assertEquals(defaults.roomCapacity(), demo.roomCapacity(), "정원·직업 구성은 그대로");
        assertEquals(defaults.roleCounts(), demo.roleCounts());
    }

    @Test
    void demoFlagMayComeAlone() {
        ServerArgs args = ServerArgs.parse(new String[] {"--demo"});
        assertEquals("0.0.0.0", args.host());
        assertEquals(defaults.port(), args.port());
        assertTrue(args.demo());
    }

    @Test
    void badPortOrUnknownFlagIsRejectedWithUsage() {
        IllegalArgumentException port = assertThrows(IllegalArgumentException.class,
                () -> ServerArgs.parse(new String[] {"0.0.0.0", "abc"}));
        assertTrue(port.getMessage().contains("사용법"), port.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ServerArgs.parse(new String[] {"0.0.0.0", "70000"}));
        assertThrows(IllegalArgumentException.class, () -> ServerArgs.parse(new String[] {"--fast"}));
        assertThrows(IllegalArgumentException.class, () -> ServerArgs.parse(new String[] {"a", "1", "b"}));
    }
}
