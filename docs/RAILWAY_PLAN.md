# Railway 데모 구현 계획

사용자 승인 범위: 로컬 코드 수정과 격리 DB 테스트. 실제 DB 변경, 커밋/푸시/이력 변경, 클라우드 배포 금지.

1. demo 프로필로 포트/DB/HTTPS 프록시/쿠키/Origin을 분리한다. Docker에서 React를 빌드하고 Spring JAR에 정적 리소스로 포함한다.
2. 공개 demo에서 인증 없는 비밀번호 재설정을 차단한다. 로컬의 기존 재설정은 유지하고, capabilities로 화면에 가용 여부만 알린다. 사용자 조회는 본인만 허용하며 중첩 사용자 응답의 전화번호를 숨긴다.
3. Railway 설정·healthz·Docker context 비밀정보 제외 규칙을 추가한다. 테스트 페이지 제거/차단 변경을 보존한다.
4. H2 자동 테스트, frontend 테스트/빌드, JAR 정적 자원/금지 파일 검사, 가능한 경우 Docker 빌드 및 격리 프로필 실행을 확인한다.

규모·위험: 세션/STOMP 브로커/푸시 큐는 메모리이므로 데모 앱 인스턴스는 1개로 운영한다. 재배포 시 재로그인/재접속 필요. 실제 HTTPS 및 외부 Push 수신은 Railway 배포 후 수동 확인한다.

## 검증 결과

- 로컬 백엔드 전체 28개 테스트 및 package 성공. DB는 H2만 사용.
- frontend 테스트 4개, build 성공. lint 종료코드 0, 기존 App.jsx 패턴 관련 경고 8개 유지.
- Linux Docker 전체 빌드 성공. Node 24 / Java 21 이미지에서 테스트 실행 포함.
- 최종 이미지의 JAR에 React index/assets, sw.js, demo 프로필 포함 확인. 테스트 페이지·로컬 키 파일·H2 런타임 의존성 미포함 확인.
- 일회성 H2를 외부 마운트한 demo 이미지에서 루트 화면·SW, Forwarded HTTPS Secure/HttpOnly/SameSite 쿠키, 회원가입/로그인, 본인 조회 권한, 재설정 차단, 방 생성/참여/READY/주문, 실제 WebSocket/STOMP 교환과 기록, 정산/최종 완료 확인.
- 테스트 컨테이너만 종료·제거. 실제 MySQL/사용자 데이터·기존 VAPID 파일 변경 없음.
- 별도 읽기 전용 코드 검토: 중요 지적 없음.
- Railway 원격 배포, 실제 HTTPS 인증서, 외부 Web Push 수신은 수행하지 않음. 커밋/푸시/이력 재작성 없음.
