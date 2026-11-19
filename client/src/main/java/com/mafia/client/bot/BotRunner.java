package com.mafia.client.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트 봇 실행기. 지정한 공개방에 봇을 넣는다.
 * 실행: gradlew :client:runBots --args="서버주소 포트 방번호 [봇 수, 기본 4]"
 */
public final class BotRunner {
    private static final int DEFAULT_COUNT = 4;
    private static final int MAX_COUNT = 5;

    private BotRunner() {
    }

    /** 봇 count개를 접속시킨다. 이름은 방번호-1 … 방번호-count. */
    public static List<BotClient> start(String host, int port, int roomId, int count) throws IOException {
        if (count < 1 || count > MAX_COUNT) throw new IllegalArgumentException("봇 수는 1~" + MAX_COUNT + "입니다");
        List<BotClient> bots = new ArrayList<>();
        try {
            for (int i = 1; i <= count; i++) {
                BotClient bot = new BotClient(roomId, i);
                bots.add(bot);
                bot.connect(host, port);
            }
        } catch (IOException | RuntimeException e) {
            bots.forEach(BotClient::close);
            throw e;
        }
        return bots;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("사용법: 서버주소 포트 방번호 [봇 수, 기본 " + DEFAULT_COUNT + "]");
            System.exit(1);
        }
        int count = args.length > 3 ? Integer.parseInt(args[3]) : DEFAULT_COUNT;
        List<BotClient> bots = start(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), count);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> bots.forEach(BotClient::close), "bot-shutdown"));
        System.out.println("테스트 봇 " + bots.size() + "개 실행 중. 종료하려면 Ctrl+C");
        for (BotClient bot : bots) bot.awaitStopped(Long.MAX_VALUE);
    }
}
