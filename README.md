# 마피넷: 로그인·네트워크 작업 범위

Java 17 이상. 이 브랜치의 추가 구현은 닉네임 로그인·실제 Socket 전송·프로토콜 검사에 한정한다.

## 실행
```powershell
.\gradlew.bat :server:run --args="127.0.0.1 5000"
.\gradlew.bat :client:run
```
로그인 화면에 localhost / 5000 / 서로 다른 닉네임을 입력한다.
LOGIN_OK를 받으면 MinJun의 기존 로비 화면으로 이동한다.
현재 ServerMain은 LOGIN만 등록한다. 로비의 자동 ROOM_LIST 요청과 방·채팅·게임 요청은
기존 라우터의 ERROR 응답을 받는다. 이는 해당 서비스를 아직 연결하지 않은 상태이며
방 생성·게임 시작까지 구현됐다는 뜻이 아니다. 시작 시 WiringCheck는 미연결 항목을 보고한다.

다른 PC에서 연결하려면 서버 인수를 `0.0.0.0 5000`으로 바꾸고 클라이언트에 서버 LAN 주소를 입력한다.
서버 방화벽에서 해당 포트 접근 허용이 필요하다. 다른 PC 간 통신은 별도 확인 대상이다.
닉네임 등록이며 계정·비밀번호 인증이나 암호화 전송을 제공하지 않는다.

## 유지한 구현
- MainScene: 비동기 연결, 입력 검사, 오류 안내와 닉네임 재시도.
- SocketConnection / TcpChannel: 실제 연결, UTF-8 송수신, FIFO 큐, 종료와 재접속.
- GameServer / ClientHandler / SessionRegistry / LoginHandler: 접속 수락과 닉네임 관리.
- MessageCodec의 방향별 encode 검사와 클라이언트 common 복사본 제거.

제가 추가했던 RoomService(방 관리·채팅), StandardRole, DefaultCharacterService는 삭제했다.
그에 따른 방·직업·게임 조립 및 게임 시나리오 테스트도 제거했다.
GameServer의 종료 콜백은 이후 팀원 서비스 연결을 위한 일반 인터페이스로 남겨 두었으며,
현재 ServerMain은 방 정리나 사망 처리를 수행하지 않는다.

## 기존 화면의 출처
아래 파일은 origin/feat/MinJun의 d2ddea3f63bfa36d5a763102416c7ff68c972da6과
Git blob 해시가 일치한다. 이번 로그인·네트워크 작업에서 새로 작성한 화면이 아니다.
- client/scene: LobbyScene, RoomScene, GameScene, ResultScene
- client/game: PhaseBanner, VotePanel
- client/demo: DemoAbilityPanel, DemoChatPanel
실제 경로는 client/src/main/java/com/mafia/client 아래다.
StreamManager, GameStream, ClientState, server.game과 기존 demo 구현도 보존했다.
MainScene(로그인 UI)과 ClientMain의 연결 객체 생성·창 제목은 이번 작업에서 수정한 부분이다.

## 프로토콜
단일 기준은 common의 MessageType / MessageCodec이다. MinJun이 이미 추가한
ROOM_STATE / LEAVE_ROOM / ROOM_LEFT와 기존 필드 순서는 보존한다.
이번 작업의 추가는 encode(message, direction)의 방향·필드 수 검사다.
UTF-8 한 줄 한 메시지, 송신 LF / 수신 LF 또는 CRLF, 최대 8192 Java 문자.
빈 마지막 필드를 보존하고 마지막 CHAT 필드만 |를 허용한다.
닉네임은 유니코드 문자·숫자·_- 1~16자다.

## 검증
```powershell
.\gradlew.bat check build
```
JUnit 5로 로그인 화면의 비동기 처리, 프로토콜과 실제 Socket 6개 연결을 검사한다.
네트워크 테스트의 CHAT 핸들러는 송신 순서·개인 응답을 검사하는 테스트 전용 echo이며
운영 서버의 채팅 기능이 아니다. 방·직업·게임 시나리오는 이 작업 범위에 포함하지 않는다.


## 푸시 전 경계 조건 보완
- 연결이 닫힌 뒤 늦게 처리되는 LOGIN은 닉네임을 등록하지 않는다.
- 로그인 시간 초과 시 작업과 요청을 취소한다. 실행 대기 중인 요청은 접속하지 않고,
  진행 중인 연결이 늦게 끝나도 LOGIN을 전송하지 않는다.
- 연결 시도를 직렬화하여 이전 요청의 정리가 새 재시도 연결을 닫지 않도록 한다.
- 해당 두 문제는 회귀 테스트가 수정 전 실패하고 수정 후 통과하는 것으로 확인한다.
