package StreamSystem;

import com.mafia.common.model.Phase;
import com.mafia.common.protocol.Message;

/**
 * 서버 응답을 앱 화면 상태와 게임 단계 표시로 옮기는 클라이언트 관리자.
 * 규칙/승패/시간 만료는 서버 GameSession이 판정한다.
 * 실제 Swing 화면 전환은 GameStream이 수행한다.
 * 화면 흐름(AppStream)과 게임 단계(gameStream)를 나눠 두고, 서버 응답에 따라 각각 바꾼다.
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

    /** 결과 화면에서 사용자가 직접 대기방 복귀를 눌렀을 때만 End → FrontDesk로 전환한다. */
    public void returnToRoom() {
        if (appState == AppStream.End) {
            appState = AppStream.FrontDesk;
            displayedGamePhase = gameStream.WAITING;
        }
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
