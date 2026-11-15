# Web Push 실행 및 확인

## 준비 사항

- Java 21, Node.js 24, 실행 중인 MySQL 8. 기존 dorm_delivery DB와 DB_PASSWORD를 그대로 사용합니다.
- Chrome 또는 Edge의 일반 프로필 두 개를 권장합니다. 시크릿 모드에서는 Push 구독이 제한될 수 있습니다.
- 로컬에서는 반드시 http://localhost:5173 으로 접속합니다. http://192.168.x.x 는 보안 컨텍스트가 아니므로 지원되지 않습니다.
- 사이트 탭을 닫아도 수신할 수 있지만 브라우저 완전 종료, 절전, 네트워크 단절, Windows 방해 금지 설정에 따라 전달이 지연되거나 표시되지 않을 수 있습니다.

## 최초 설정

### iPhone/iPad 및 카카오톡에서 열었을 때

서버의 READY는 VAPID 발송 설정의 준비 상태입니다. 브라우저의 Push API 지원이나 권한 허용을 의미하지 않습니다.

1. 카카오톡 내부 화면이면 메뉴에서 외부 브라우저로 열기를 선택하고 Safari로 접속하세요.
2. iOS/iPadOS 16.4 이상에서 Safari 공유 메뉴 → 홈 화면에 추가를 선택하세요.
3. 홈 화면의 단짝 아이콘으로 실행하세요. 필요한 경우 다시 로그인합니다.
4. 알림 켜기를 직접 누르고 권한을 허용하세요. 자동으로 권한을 요청하지 않습니다.
5. 이전에 홈 화면에 추가한 항목이 일반 브라우저 탭으로 열린다면 최신 배포 페이지에서 다시 추가하세요.

manifest.webmanifest의 standalone 실행 설정과 Apple 웹앱 메타데이터를 제공합니다. 기존 화면과 Service Worker는 그대로 사용하며 오프라인 페이지 캐싱은 추가하지 않습니다.

지원 검사는 보안 컨텍스트, Service Worker, Notification.requestPermission, PushManager를 각각 검사합니다. 보안 컨텍스트가 아니면 계속 차단합니다. Safari 홈 화면 실행 필요, 앱 내 브라우저 제한, API 미지원은 HTTPS 오류와 구분해서 안내합니다.

참고: https://webkit.org/blog/13878/web-push-for-web-apps-on-ios-and-ipados/

VS Code에서 프로젝트 폴더를 열고 PowerShell 터미널을 엽니다.

```powershell
Set-Location -LiteralPath 'C:\Users\leejw\OneDrive\바탕 화면\dorm-delivery'
node scripts/generate-vapid.mjs
git check-ignore secrets/vapid.json
```

키는 출력되지 않으며 Git에서 제외되는 secrets/vapid.json에 생성됩니다. 같은 키를 계속 사용하고, 파일을 공유하거나 Git에 강제 추가하지 마세요. 기존 키 파일은 덮어쓰지 않습니다. 키를 바꾸면 기존 브라우저 구독을 해제하고 다시 등록해야 합니다.

## 백엔드 실행 — 터미널 1

```powershell
.\scripts\start-backend.ps1
```

연락처를 물으면 본인의 메일 주소를 mailto:접두사와 함께 입력합니다. DB_PASSWORD가 이미 있으면 다시 묻지 않습니다. 없다면 보안 입력창에서 MySQL root 비밀번호를 입력합니다. 키와 비밀번호 값은 출력하지 않습니다.

일반 이메일만 입력해도 mailto:로 정규화합니다. 앞뒤 공백도 제거하며 키 파일 자체는 변경하지 않습니다. 기존 환경변수의 연락처를 덮어쓰려면 다음처럼 지정하세요(예시 주소 대신 본인의 연락처 입력).

```powershell
.\scripts\start-backend.ps1 -Subject 'mailto:your-email@example.com'
```

서버나 DB를 시작하지 않고 기존 키 형식·일치 여부와 자식 프로세스로의 환경변수 전달만 검사할 수도 있습니다.

```powershell
.\scripts\start-backend.ps1 -CheckOnly -Subject 'mailto:your-email@example.com'
```

정상 시작 시 `Web Push 초기화 완료 [READY]`가 표시됩니다. 오류가 있으면 값 대신 `VAPID_SUBJECT_INVALID`, `VAPID_PUBLIC_KEY_MISSING`, `VAPID_KEY_PAIR_MISMATCH` 등의 원인 코드만 표시합니다. 기존 포괄적 경고만으로 과거 실행의 입력을 소급 확정할 수는 없습니다.

PowerShell 실행 정책으로 차단되면 다음처럼 이 스크립트 실행에만 적용합니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

Tomcat 8080 시작 로그를 확인하고 터미널을 유지합니다. 새 push_subscriptions 테이블이 추가됩니다. 기존 테이블·사용자 데이터를 삭제하거나 초기화하지 않습니다.

## 프론트엔드 실행 — 터미널 2

```powershell
Set-Location -LiteralPath 'C:\Users\leejw\OneDrive\바탕 화면\dorm-delivery\frontend'
npm.cmd install
npm.cmd run dev -- --port 5173 --strictPort
```

http://localhost:5173 으로 접속합니다. Vite가 API와 WebSocket을 8080으로 프록시하므로 백엔드도 실행 중이어야 합니다.

## 실제 알림 확인

1. 브라우저 프로필 A·B에서 서로 다른 계정으로 로그인하고 같은 기숙사를 선택합니다.
2. 두 프로필에서 각각 `알림 켜기`를 누르고 브라우저의 허용 버튼을 누릅니다. 자동 권한 요청은 하지 않습니다.
3. A가 방을 만들고 A의 사이트 탭을 닫습니다. 브라우저 자체는 실행 상태로 두세요.
4. B가 방에 참여하면 A에게 참여 알림이 옵니다. 클릭하면 사이트가 열리고 해당 방으로 이동합니다. 세션이 만료됐다면 로그인 후 이동합니다.
5. A·B 모두 READY한 뒤 A가 주문을 시작하면 B에게 주문 시작 알림이 옵니다.
6. B가 채팅을 보내면 A에게 알림이 옵니다. 작성자 B에게는 보내지 않습니다. 알림에는 채팅 본문을 넣지 않습니다.
7. 같은 방에 참여하지 않은 계정 C에는 알림이 오지 않는지 확인합니다.
8. `알림 끄기` 후에는 해당 브라우저로 새 알림이 발송되지 않습니다. 다른 기기의 구독은 유지합니다.
9. 로그아웃하면 서버에서 현재 브라우저의 구독을 지우고 브라우저 구독도 해제합니다. 로그인 세션 만료 후 다른 계정으로 로그인해도 이전 계정의 기기 연결은 제거합니다.
10. 채팅 기록·READY·정산 완료·방장 최종 완료가 기존과 같이 작동하는지 확인합니다.

이미 푸시 서비스로 넘어간 알림은 구독 해제 직후 잠깐 표시될 수 있습니다. 거부한 권한은 버튼만으로 재요청할 수 없으므로 주소창의 사이트 설정에서 알림을 허용하고 다시 켜세요.

## 문제 확인

- 알림 버튼 비활성: localhost 또는 HTTPS, 브라우저 Push API 지원 여부를 확인합니다.
- 서버 설정 안내: WEB_PUSH_ENABLED, VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT 설정을 확인합니다. 값을 로그나 채팅에 붙이지 마세요.
- 브라우저 개발자 도구 → Application → Service Workers에서 /sw.js 등록 여부를 확인합니다.
- Network에서 /push/subscriptions 응답 204가 성공입니다. endpoint와 구독 키는 공유하지 마세요.
- Windows 설정 → 시스템 → 알림에서 해당 브라우저 알림과 방해 금지 설정을 확인합니다.
- 404/410 응답 구독은 서버가 자동 정리합니다. 일시 오류는 기존 주문·채팅 처리를 실패시키지 않습니다.
- 발송은 커밋 후 메모리 대기열에서 실행됩니다. 영속 재시도 큐가 없어 서버 종료나 대기열 초과 시 일부 알림이 유실될 수 있습니다.

## 테스트와 빌드

프로젝트 최상위:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

frontend 폴더:

```powershell
npm.cmd test
npm.cmd run build
npm.cmd run lint
```

백엔드 테스트는 src/test/resources/application.properties의 H2 메모리 DB만 사용합니다. 테스트의 create-drop 설정은 H2에만 적용되며 MySQL에는 적용하지 않습니다.

현재 Windows/OneDrive 환경에서 Vite의 기존 출력 폴더 정리 중 네이티브 프로세스 종료가 재현되어 emptyOutDir=false로 기존 빌드 파일을 보존합니다. 새 index.html은 최신 해시의 파일을 참조합니다. 배포할 때는 새 배포 디렉터리에 빌드 산출물을 올리는 방식을 권장합니다.

## 배포

HTTPS 도메인에서 frontend/dist를 제공하고 /users, /rooms, /room-members, /chats, /push, /ws를 같은 출처의 백엔드로 프록시하세요. /ws에는 WebSocket 업그레이드 설정이 필요합니다. Vite 개발 프록시는 배포에 포함되지 않습니다. 신뢰하는 리버스 프록시에서 전달하는 Host/Origin을 유지하세요.

서버 환경변수로 WEB_PUSH_ENABLED=true, VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY, VAPID_SUBJECT와 DB_PASSWORD를 설정하세요. 프론트엔드 VITE_ 변수에 개인키를 넣지 마세요. APP_ALLOWED_ORIGINS에 실제 HTTPS 출처를 쉼표로 구분해 지정합니다. 이 목록은 REST·WebSocket에 공통 적용됩니다. 기존 .env.example은 설명용이며 Spring이 자동으로 읽지 않습니다.
