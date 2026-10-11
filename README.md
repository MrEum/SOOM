# 숨 (SOOM)

금연 기록, 절약 금액, 출석 보상, 나무 성장과 커뮤니티를 제공하는 웹 프로젝트입니다.

## 프로젝트 구성

| 경로 | 내용 |
|---|---|
| frontend/ | React · TypeScript · Vite 프런트엔드 |
| backend/soom/ | Java 17 · Spring Boot · JdbcTemplate 백엔드 |

프런트엔드와 백엔드를 구분하고, 현재 구현을 기능별 커밋으로 나눠 등록했습니다. 과거 개발 시점을 복원한 이력은 아닙니다. 중간 커밋은 다른 기능의 파일을 참조할 수 있으므로 전체 실행에는 최신 main을 사용하세요.

## 실행

### 프런트엔드

Node.js 22.13 이상을 사용합니다.

~~~sh
cd frontend
npm ci
npm run dev
~~~

기본 주소는 http://127.0.0.1:5173 이며 /api 요청은 localhost:8080으로 전달됩니다.

### 백엔드

Java 17과 MySQL을 준비하고 아래 환경변수를 설정합니다.

| 변수 | 내용 |
|---|---|
| DB_URL | JDBC 접속 URL (기본값: 로컬 soom DB) |
| DB_USERNAME / DB_PASSWORD | DB 계정과 비밀번호 |
| SMTP_HOST / SMTP_PORT | 메일 서버 (기본값: Gmail / 587) |
| SMTP_USERNAME / SMTP_PASSWORD | 메일 발송 계정과 앱 비밀번호 |
| JWT_SECRET | 32바이트 이상 난수의 Base64 값. 미설정 시 개발용 임시 키 사용 |
| SOOM_FRONTEND_URL | 인증·복구 링크에 사용할 프런트 주소 |
| AUTH_COOKIE_SECURE | HTTPS 배포 시 true |

~~~sh
cd backend/soom
./gradlew bootRun
~~~

Windows PowerShell에서는 .\gradlew.bat bootRun을 사용합니다. 환경변수는 셸이나 IDE 실행 설정에 지정합니다. .env 파일을 자동으로 읽는 설정은 없습니다.

**DB 준비:** 현재 프로젝트는 기존 업무 테이블이 있는 MySQL DB를 전제로 합니다. signup-schema.sql은 회원 테이블용이며, src/main/resources의 SQL은 복구·요청 중복 방지 등의 보조 테이블과 여정 기준 데이터입니다. 이 파일들만으로 전체 운영 DB가 만들어지지는 않습니다. src/test/resources/dashboard-test-schema.sql은 H2 통합 테스트용입니다.

## 검증

~~~sh
cd frontend
npx tsc --noEmit --incremental false
npm run build
node --test features/records/guestRecordStorage.test.mjs features/dashboard/commentTree.test.mjs
~~~

백엔드에서는 ./gradlew test를 사용합니다. 대부분의 통합 테스트는 H2를 사용하지만 기본 컨텍스트 테스트는 설정된 데이터소스에 연결하므로 DB 실행 환경이 필요합니다. 메일 발송 검증에는 별도 SMTP 설정이 필요합니다.

## 기능

- 회원가입·이메일 인증, 로그인·계정 복구
- 회원 금연 기록과 비회원 Local Storage 기록
- 출석·포인트·금연 나무·여정·위시리스트·알림
- 게시글·댓글·답글·좋아요·이미지·신고
- 관리자 데모 (실제 운영 관리 기능과 구분)

로컬 인증값, 계정 데이터, 의존성 폴더, 빌드 결과물과 작업 백업은 포함하지 않습니다. 공개본의 application.properties는 환경변수 기반이며 기본 vite.config.ts는 현재 사용하는 vite.ui.config.ts를 연결합니다.
