# 단짝 (DAN-JJAK)

단국대학교 죽전캠퍼스 생활관 학생들을 위한 **공동배달 매칭 서비스**입니다.

같은 기숙사에 거주하는 학생들이 배달 주문을 함께할 사람을 모집하고, 주문 진행 상황과 채팅, 정산 과정을 하나의 서비스에서 관리할 수 있도록 구현했습니다.

## 서비스 대상

단국대학교 죽전캠퍼스 생활관 거주 학생

- 웅비홀
- 집현재
- 진리관

가입한 사용자의 생활관을 기준으로 공동배달 방이 구분되며, 다른 생활관의 방은 조회하거나 참여할 수 없습니다.

## 주요 기능

### 회원 기능

- 회원가입
- 로그인 / 로그아웃
- 로그인 정보 저장
    - 비밀번호가 아닌 전화번호만 브라우저에 저장
- 비밀번호 재설정
- 비밀번호 BCrypt 암호화
- 로그인 세션 유지
- 생활관 선택

### 공동배달 방

- 공동배달 방 생성
- 같은 생활관의 방만 조회
- 방 참여
- 방 나가기
- 방장이 모집 중인 방 삭제
- 최대 참여 인원 설정
- 배달 메뉴 및 수령 장소 설정
- 주문 예정 시간 설정
- 내가 참여한 방 표시
- 전체 / 모집중 / 참여중 필터

### 공동배달 진행 과정

방의 진행 상태는 다음 순서로 관리됩니다.

`모집중 → 모집완료 → 주문중 → 정산완료 → 완료`

참여자가 모두 READY 상태가 되면 모집이 완료되며, 방장이 주문을 시작할 수 있습니다.

### 실시간 채팅

- Spring WebSocket 기반 실시간 채팅
- 같은 공동배달 방 참여자끼리 메시지 전송
- 채팅 내용 DB 저장
- 기존 채팅 내역 조회

### 정산

- 참여자별 정산 완료 여부 확인
- 모든 참여자의 정산이 끝나면 정산완료 상태로 변경
- 방장이 최종 공동배달 완료 처리

## 서비스 흐름

```text
회원가입 / 로그인
        ↓
생활관 확인
        ↓
공동배달 방 조회
        ↓
방 생성 또는 참여
        ↓
참여자 READY
        ↓
모집 완료
        ↓
방장 주문 시작
        ↓
실시간 채팅
        ↓
참여자 정산
        ↓
공동배달 완료
```

## 기술 스택

### Backend

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring WebSocket
- Spring Security Crypto
- BCrypt

### Frontend

- React
- Vite
- JavaScript
- CSS

### Database

- MySQL 8

### Development Environment

- IntelliJ IDEA
- Maven
- Git / GitHub
- MySQL Workbench

## 프로젝트 구조

```text
dorm-delivery
│
├── src
│   ├── main
│   │   ├── java
│   │   │   ├── chat
│   │   │   ├── config
│   │   │   ├── room
│   │   │   └── user
│   │   │
│   │   └── resources
│   │       └── application.properties
│   │
│   └── test
│
├── frontend
│   ├── src
│   │   ├── App.jsx
│   │   └── App.css
│   └── package.json
│
├── pom.xml
└── README.md
```

## 데이터베이스

주요 테이블은 다음과 같습니다.

### users

사용자의 이름, 전화번호, 암호화된 비밀번호, 생활관 정보를 저장합니다.

### rooms

공동배달 방의 제목, 메뉴, 최대 인원, 현재 인원, 수령 장소, 주문 시간, 생활관 및 진행 상태를 저장합니다.

### room_members

공동배달 방과 사용자 간 참여 관계를 관리합니다.

READY 여부, 방장 여부, 정산 여부를 저장합니다.

### chat_messages

방별 채팅 메시지와 발신자, 전송 시간을 저장합니다.

## 실행 방법

### MySQL 데이터베이스 생성

```sql
CREATE DATABASE dorm_delivery
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

### DB 비밀번호 환경변수 설정

Windows PowerShell에서 자신의 MySQL 비밀번호를 설정합니다.

```powershell
setx DB_PASSWORD "본인의 MySQL 비밀번호"
```

환경변수를 설정한 후 새로운 PowerShell 창을 실행합니다.

### Backend 실행

프로젝트 최상위 폴더에서 실행합니다.

```powershell
.\mvnw.cmd spring-boot:run
```

정상 실행 시:

```text
Tomcat started on port 8080
Started DormDeliveryApplication
```

이 출력됩니다.

### Frontend 설치

```powershell
cd frontend
npm install
```

### Frontend 실행

```powershell
npm run dev
```

브라우저에서 다음 주소로 접속합니다.

```text
http://localhost:5173
```

## 환경변수

`application.properties`에서는 DB 비밀번호를 코드에 직접 저장하지 않습니다.

```properties
spring.datasource.password=${DB_PASSWORD}
```

각 개발자는 자신의 컴퓨터에서 `DB_PASSWORD` 환경변수를 설정해야 합니다.

## 보안

- 사용자 비밀번호 BCrypt 암호화
- 로그인 세션 기반 사용자 확인
- 비밀번호 JSON 응답 제외
- 생활관별 공동배달 방 접근 제한
- 방장 권한 확인
- 실제 데이터베이스 비밀번호 GitHub 저장소 제외

## 향후 개선 사항

현재 프로젝트는 학교 프로젝트용 MVP를 목표로 구현했습니다.

향후 실제 서비스로 확장할 경우 다음 기능을 추가할 수 있습니다.

- 단국대학교 학생 인증
- 휴대전화 SMS 본인 인증
- SMS 기반 비밀번호 재설정
- 주문 및 모집 알림
- 배달비 자동 분할 계산
- 계좌 및 간편송금 연동
- 사용자 평가 및 신고
- 푸시 알림
- 천안캠퍼스 지원
- 실제 서버 배포 및 모바일 웹 서비스

## 프로젝트 목적

배달음식을 주문할 때 최소 주문금액과 배달비 때문에 한 명이 주문하기 부담스러운 문제를 해결하고, 같은 생활관 학생들이 쉽고 빠르게 공동 주문할 수 있도록 하는 것을 목표로 합니다.

생활관 단위로 사용자를 연결하여 실제 배달 수령이 가능한 학생끼리 매칭하고, 모집부터 주문, 채팅, 정산까지 하나의 흐름으로 제공하는 것이 '단짝'의 핵심입니다.