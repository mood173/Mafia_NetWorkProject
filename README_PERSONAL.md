# 민준 개인 담당 AI 보험 브랜치

현재 체크포인트: 10주차 — 앱 흐름과 서버 응답 기반 화면 전환

## 보관과 공개 범위

로컬 브랜치: `local/minjun-weekly-insurance`. 원본 `feat/MinJun`에서 분리한 별도 작업 폴더다. 원격 푸시와 PR은 하지 않는다. 기본 pushRemote는 존재하지 않는 `LOCAL_ONLY_DO_NOT_PUSH`로 설정한다. 명시적으로 다른 원격을 지정해 푸시하면 공개될 수 있으므로 Fork에서는 푸시하지 말고 커밋을 열어 비교한다. 저장소의 팀원은 로컬 브랜치를 볼 수 없다.

이 커밋들은 2026-11-09에 미리 작성한 주차별 구현 단계다. 실제 수업 주차마다 개발한 이력으로 오해하지 않도록 현재 날짜로 커밋한다.

## 담당 범위

- 내 구현: server.game의 판 진행·타이머·배정·투표·승패·이탈, client의 흐름·씬·배너·투표 UI.
- 연결 계약: common 메시지 제안, server.character의 인터페이스/값 객체, AbilityView와 ChatView. 능력 구현체는 없다.
- 제외: demo 실행 서버, SocketConnection 구현, 방 레지스트리, 채팅 서비스, 직업별 능력 구현. 테스트 모형은 verification에만 둔다.
- 원본의 기존 파일과 뼈대는 유지하며, 별도 전체 데모 보험본과 이 개인 브랜치를 혼동하지 않는다.

## 주차별 문서

- [10주차 설계](docs/personal/10주차_설계.md) · [구현](docs/personal/10주차_구현.md): 앱 흐름과 서버 응답 기반 화면 전환

## 팀원과 조립하는 방법

클라이언트의 `ClientMain.assemble(frame, connection, abilityFactory, chatFactory)`는 13주차부터 제공한다. EDT에서 팀원 부품을 주입한다. Connection은 프레임 생성 후 연결하고 수신 메시지는 등록한 리스너로 전달한다.

서버 Room이 RoomChannel을 구현하고 GameSessionFactory에 RoleAbility 목록과 판마다 새 CharacterService를 만드는 supplier를 제공한다. GameMessageHandler에 사용자→GameEventHandler 조회 함수를 주입하고, 접속 해제 시 현재 판의 onPlayerLeft를 호출한다. 콜백은 오래된 밤의 완료를 새 밤으로 잘못 통지하지 않아야 한다. RoomChannel은 게임 잠금 안에서 불리므로 송신을 비차단 큐로 넘기는 구현을 권장한다.

공유 프로토콜 ROOM_STATE/LEAVE_ROOM/ROOM_LEFT 및 캐릭터 계약은 팀원과 맞춰야 한다. 실제 팀원 최종 구현과의 컴파일/6인 접속/밤/투표/이탈/종료 시나리오는 아직 확인하지 않았다.

## 검증과 실행

`gradlew.bat build`가 이 단계의 개인 검사를 실행한다. 15주차는 `gradlew.bat :verification:uiCheck`로 Swing 흐름도 검사한다. 현재 개인 검사 수는 7개다. main은 기존 OfflineConnection 기반 미리보기이며 실제 게임 서비스를 띄우지 않는다.
