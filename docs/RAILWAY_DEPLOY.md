# 단짝 Railway 데모 배포

## 구조

브라우저 → Railway HTTPS → 앱 컨테이너 하나(React 정적 파일 + Spring Boot API/WebSocket/Push) → Railway 별도 MySQL.

Dockerfile은 Node 24에서 React를 빌드하고, 결과를 Spring의 static 리소스로 복사한 뒤 Java 21 JAR을 만듭니다. Railway에는 앱과 MySQL 두 서비스만 필요합니다. Vite 개발 서버나 별도 프론트엔드 도메인은 배포하지 않습니다.

현재 HTTP 세션, STOMP 브로커, Push 작업 큐는 메모리이므로 앱 인스턴스는 1개로 운영하세요. 여러 인스턴스 확장은 이번 범위에 없습니다. 재배포 시 재로그인·방 재진입이 필요할 수 있습니다. 지속적인 채팅/알림 데모에는 앱 절전 기능을 끄세요. 서버 종료 중에는 이벤트를 생성할 수 없으며 영속 Push 재시도 큐는 없습니다.

## 로컬 개발 유지

기존처럼 프로젝트 루트에서 scripts/start-backend.ps1, frontend에서 npm.cmd run dev를 사용합니다. demo 프로필을 로컬 기본 환경에 설정하지 마세요. Vite는 localhost:8080 프록시를 유지하지만 빌드 결과의 API는 상대 주소이며 WebSocket도 현재 도메인과 HTTPS 여부를 사용합니다.

demo에서는 취약한 비밀번호 재설정 API와 화면 링크가 비활성화됩니다. 로컬 기본 프로필의 기존 재설정은 유지됩니다. 공개 서비스에서 로컬 프로필을 사용하거나 app.password-reset.enabled를 다시 켜지 마세요. 분실한 데모 비밀번호는 운영자가 본인을 확인한 뒤 별도 안전한 절차로 변경해야 합니다.

사용자 단건 조회는 본인 로그인만 허용하며, 방 참여자 등 중첩 사용자 응답에서 전화번호·비밀번호는 제공하지 않습니다. 로그인한 본인 정보 응답에는 본인 전화번호가 포함됩니다.

## 1. 배포 전 준비

1. 로컬 테스트와 Docker 빌드를 완료합니다. GitHub에 올릴 파일을 확인하고 반드시 사용자 승인 후 커밋/푸시합니다.
2. 로컬 MySQL 덤프나 기존 실제 사용자 정보를 업로드하지 않습니다. Railway 데모 DB는 빈 별도 DB로 시작합니다.
3. secrets/, .env, node_modules, target, artifacts는 Docker context에서 제외됩니다. 개인키를 Docker ARG, ENV 리터럴, frontend VITE_ 변수에 넣지 않습니다.
4. 기존 테스트 페이지는 제거되었으며 패키징 제외 및 404 필터를 유지합니다.

## 2. 별도 데모 VAPID 키 준비

프로젝트 루트 PowerShell에서 다음을 한 번 실행합니다.

```powershell
node scripts/generate-demo-vapid.mjs
git check-ignore secrets/demo-vapid.json
```

기존 로컬 VAPID 키는 변경하지 않습니다. 값은 터미널에 출력하지 않습니다. 생성된 파일을 로컬 편집기로 열어 Railway Variables의 해당 항목에 직접 입력하세요. 화면 공유/스크린샷/로그/채팅에 값을 남기지 마세요. 재배포할 때 같은 데모 키를 재사용합니다. 키를 교체하면 브라우저 알림을 껐다가 다시 켜야 합니다.

## 3. Railway 프로젝트 생성

1. Railway에서 새 프로젝트를 만들고 MySQL 서비스를 추가합니다. 예시는 서비스 이름이 MySQL인 경우입니다.
2. MySQL의 영속 볼륨 연결과 백업 정책을 확인합니다. 공개 DB 접속은 켜지 않고 같은 프로젝트의 사설 연결을 사용합니다.
3. GitHub 저장소로 앱 서비스를 추가하고 Root Directory를 저장소 최상위로 둡니다. frontend 폴더를 Root로 지정하지 마세요.
4. 루트 Dockerfile과 railway.json을 사용합니다. 별도 Build/Start Command는 비워 Docker ENTRYPOINT가 적용되게 합니다.
5. 앱 Settings → Networking → Generate Domain으로 HTTPS 도메인을 생성합니다.

## 4. 앱 서비스 Variables

아래 값은 앱 서비스에 설정합니다. MySQL 서비스 이름이 다르면 참조 이름도 바꿉니다. 실제 비밀번호나 개인키는 이 문서에 적지 않습니다.

|변수|설정|
|---|---|
|SPRING_PROFILES_ACTIVE|demo (Docker 기본값이지만 Railway에도 명시 권장)|
|DEMO_DB_URL|jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useUnicode=true&characterEncoding=UTF-8|
|DEMO_DB_USERNAME|${{MySQL.MYSQLUSER}}|
|DEMO_DB_PASSWORD|${{MySQL.MYSQLPASSWORD}}|
|APP_ALLOWED_ORIGINS|생성된 HTTPS 출처 하나. 예: https://앱이름.up.railway.app (경로·끝 슬래시 제외)|
|WEB_PUSH_ENABLED|true|
|VAPID_PUBLIC_KEY|demo-vapid.json의 공개키|
|VAPID_PRIVATE_KEY|demo-vapid.json의 개인키. 비밀 변수로 관리|
|VAPID_SUBJECT|mailto:운영자이메일 또는 유효한 HTTPS 연락처 URL|
|PORT|Railway 제공 값 사용. 특별한 이유가 없으면 직접 지정하지 않음|

DEMO_DB_URL은 JDBC 형식입니다. mysql:// 형태를 그대로 넣지 말고 비밀번호를 URL 안에 포함하지 마세요. DB_PASSWORD는 로컬 개발용이며 demo에서는 DEMO_DB_PASSWORD를 사용합니다. Railway Reference Variables로 연결값을 참조하므로 복사한 비밀번호를 소스에 저장할 필요가 없습니다.

비밀 변수는 가능하면 Railway의 sealed variable 기능을 사용합니다. 배포 로그에서 변수 전체를 출력하는 명령은 실행하지 마세요.

## 5. 배포

1. Variables 변경을 적용하고 Deploy/Redeploy합니다. 최초 소스 연결 시 변수가 없어서 실패했다면 설정 후 다시 배포합니다.
2. Build 로그에서 frontend 및 Java 테스트와 JAR 생성 성공을 확인합니다.
3. 앱은 0.0.0.0:${PORT}로 바인딩합니다. HTTPS 인증서는 Railway가 담당하며 Java에 인증서 파일을 넣지 않습니다.
4. healthcheck /healthz가 성공하는지 확인합니다. 이 경로는 비밀정보를 반환하지 않는 앱 생존 확인이며 별도의 지속 DB 상태 검사는 아닙니다.
5. Runtime 로그의 Web Push 초기화 완료 [READY]를 확인합니다. 실패해도 서비스는 시작할 수 있으므로 Push 설정을 별도로 확인해야 합니다.

demo 프로필의 ddl-auto=update는 이 별도 데모 DB의 테이블을 생성·갱신합니다. create/create-drop은 사용하지 않습니다. 이미 데이터가 있는 DB를 재사용한다면 스키마 변경 전에 백업하세요. 로컬 DB 주소를 설정하지 마세요.

## 6. HTTPS 동작 확인

Railway가 TLS를 종료하고 앱에는 HTTP로 전달하므로 demo에서 Forwarded 헤더를 처리합니다. 이 설정은 신뢰할 수 있는 Railway 프록시 뒤에서 사용하고 앱 내부 포트를 별도로 외부 공개하지 마세요.

- 세션 쿠키: Secure + HttpOnly + SameSite=Lax. 쿠키 Domain은 지정하지 않아 현재 호스트로 제한합니다.
- push_device 쿠키: request.isSecure()가 Forwarded 헤더로 HTTPS를 인식해 Secure로 생성됩니다.
- WebSocket: 브라우저가 wss://현재호스트/ws로 연결합니다. APP_ALLOWED_ORIGINS에는 같은 HTTPS 출처를 넣습니다.
- Service Worker: /sw.js를 같은 HTTPS 호스트에서 제공합니다. 캐시 프록시를 추가한다면 이 파일은 과도하게 캐시하지 마세요.

## 7. 친구 두 명과 확인

1. 서로 다른 일반 브라우저 프로필에서 같은 HTTPS 주소로 접속합니다.
2. 가상 데모 정보로 회원가입하고 같은 기숙사를 선택합니다. 다른 곳에서 사용하는 실제 비밀번호를 재사용하지 마세요.
3. 로그인 후 새로고침해 세션 유지와 로그아웃을 확인합니다.
4. A가 방 생성, B가 참여, 둘 다 READY, A가 주문 시작합니다.
5. 양방향 실시간 채팅과 새로고침 후 기록을 확인합니다.
6. 양쪽에서 정산 완료, A가 최종 완료합니다.
7. 각 브라우저에서 알림 켜기 → 허용을 누릅니다. 방 참여/채팅/주문 시작 알림이 올바른 수신자에게 가는지 확인합니다. 채팅 작성자에게는 보내지 않습니다.
8. 사이트 탭을 닫은 프로필에서도 수신하는지 확인합니다. 브라우저 완전 종료·OS 알림 제한·네트워크 단절에서는 전달이 제한될 수 있습니다.
9. /chat-test.html은 404, 로그인 없이 /users/사용자ID는 401, 다른 사용자 ID는 403, /users/reset-password는 demo에서 403인지 확인합니다.
10. 개발자 도구에서 HTTPS 쿠키와 WebSocket 101 업그레이드를 확인하되 구독 endpoint·토큰·쿠키 값을 공유하지 마세요.

## 장애 구분

- DB 연결 실패: 서비스 참조 이름/사설 호스트/포트/JDBC URL을 확인합니다.
- 403 또는 채팅 연결 거부: APP_ALLOWED_ORIGINS에 실제 https://도메인 출처만 있는지 확인합니다.
- 로그인 유지 실패: demo 프로필·Secure 쿠키·Forwarded 설정과 동일 호스트 접속을 확인합니다.
- 화면만 안 열림: Dockerfile로 빌드했는지 확인합니다. 일반 mvnw package만으로는 React가 자동 포함되지 않습니다.
- 알림 실패: READY, 브라우저 권한, OS 알림, VAPID 키 일치, 새 도메인에서 구독 등록 여부를 확인합니다.

## 공식 참고

- https://docs.railway.com/builds/dockerfiles
- https://docs.railway.com/databases/mysql
- https://docs.railway.com/networking/public-networking
- https://docs.railway.com/variables

이번 변경은 로컬 구현입니다. Railway 서비스 생성·변수 등록·원격 배포·Git 커밋/푸시는 사용자 승인 없이 수행하지 않습니다.
