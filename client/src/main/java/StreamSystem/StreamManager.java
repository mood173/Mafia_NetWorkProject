package StreamSystem;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;

/**
 * 서버 응답을 앱 화면 상태와 게임 단계 표시로 옮기는 클라이언트 관리자.
 * 규칙/승패/시간 만료는 서버 GameSession이 판정한다.
 * 실제 Swing 화면 전환은 GameStream이 수행한다.
 * 여기서 게임 단계별로 잡앋누 구조대로 게임 흐름ㄱ ㅘㄴ리
 */
public final class StreamManager {
    private AppStream appState = AppStream.main;
    private gameStream displayedGamePhase = gameStream.WAITING;

    public AppStream app() { return appState; }
    public gameStream game() { return displayedGamePhase; }

    /** 성공 응답으로 화면을 결정한다. 요청 전송 자체나 ERROR로는 전환하지 않는다. */
    public AppStream accept(Message serverMessage) {
        switch (serverMessage.type()) {
            case LOGIN_OK -> showLobby();
            case ROOM_STATE -> {
                // 다른 참가자의 방 갱신이 내 게임/결과 화면을 되돌리지 않게 한다.
                if (appState == AppStream.Lobby || appState == AppStream.FrontDesk) {
                    appState = AppStream.FrontDesk;
                }
            }
            case GAME_START -> {
                appState = AppStream.GameScene;
                displayedGamePhase = gameStream.WAITING;
            }
            case PHASE_CHANGE, GAME_STATE -> {
                Phase serverPhase = Phase.valueOf(serverMessage.field(0));
                displayedGamePhase = gameStream.from(serverPhase);
            }
            case GAME_END -> {
                appState = AppStream.End;
                displayedGamePhase = gameStream.GAME_END;
            }
            case ROOM_LEFT -> showLobby();
            default -> { }
        }
        return appState;
    }

    public void disconnected() {
        appState = AppStream.main;
        displayedGamePhase = gameStream.WAITING;
    }

    private void showLobby() {
        appState = AppStream.Lobby;
        displayedGamePhase = gameStream.WAITING;
    }
}
