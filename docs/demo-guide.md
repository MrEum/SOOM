# SOOM 중간 시연 대본

**2026년 10월 12일 · 리팩토링 후 코드 기준**

이 문서는 발표할 말과 조작 순서를 앞에, 전체 코드 매핑표를 뒤에 배치했습니다. 본문을 순서대로 따라가고, 추가 질문이 나오면 뒤의 참고표를 사용하세요.

프로젝트: SOOM 저장소 루트  
SQL 파일: [DB 확인 SQL](../docs/demo-db.sql)

---

## 1. 시연 순서 한눈에 보기

| 순서 | 보여줄 것 | 핵심 설명 | 확인할 SQL |
|---|---|---|---|
| 1 | 서비스와 구조 | 화면 → API → 서버 → DB | — |
| 2 | 로그인 | 로그인 전 401, 로그인 후 200 | 1번 |
| 3 | 회원 금연 기록 저장 | 입력한 흡연량·가격이 DB에 저장됨 | 2번 |
| 4 | 출석 체크 | 출석 행과 50P 내역이 함께 반영됨 | 4·5번 |
| 5 | 나무에 물 주기 | 30P 사용, 성장률 5 증가 | 5·6번 |
| 6 | 커뮤니티 글 작성 | 제목·내용이 post 테이블에 저장됨 | 10번 |
| 선택 | 댓글·좋아요 | 게시글과 연결된 데이터 확인 | 11·12번 |

**약 5~6분 구성입니다.** 시간이 부족하면 로그인 → 출석 → 게시글 작성만 보여주세요. 코드 매핑은 출석 기능 하나로 자세히 설명하고, 다른 기능은 결과 확인에 집중하면 됩니다.

## 2. 시작 전에 준비하기

1. 프런트엔드·백엔드·MySQL이 실행 중인지 확인합니다.
2. 회원 시연 계정에 진행 중인 금연 기록을 준비합니다. 새 기록 등록을 시연한다면 먼저 등록한 뒤 SQL 2번을 실행합니다.
3. 출석 시연은 **오늘 아직 출석하지 않은 계정**, 나무는 **성장률 100 미만**으로 준비합니다.
4. 브라우저, 개발자 도구의 Network, DB 프로그램의 SQL 창을 열어둡니다.
5. [DB 확인 SQL](../docs/demo-db.sql)의 **0번 블록**에서 이메일을 바꾸고 실행합니다.

~~~sql
SET @demo_email = '시연 계정의 실제 이메일';
~~~

0번 블록 전체를 실행하면 회원 ID와 현재 금연 기록 ID가 자동 설정됩니다. SQL은 같은 탭·같은 연결에서 실행하세요. 새로 가입했으면 1번, 새 기록을 만들었으면 2번의 SET 문도 실행해 ID를 갱신합니다.

| 결과 | 확인할 내용 |
|---|---|
| user_id가 NULL | 이메일·선택한 DB·가입 여부 |
| current_record_id가 NULL | 진행 중인 기록이 있는지 |
| SQL 결과가 비어 있음 | 아직 해당 동작을 하지 않았거나 ID 설정이 잘못됐는지 |

---

## 3. 발표 시작 — 서비스와 구조

**보여줄 화면:** 숨 대시보드

**발표 멘트**

> “숨은 금연 기록과 절약 금액을 확인하고, 출석 보상과 나무 성장으로 금연을 이어갈 수 있도록 돕는 서비스입니다. 지금부터 화면에서 한 동작이 API를 거쳐 DB에 저장되는 과정을 보여드리겠습니다.”

~~~text
React 화면 → API 요청 → Controller → Service → MySQL
                ↑                               ↓
                └──── 응답을 받아 화면 갱신 ──────┘
~~~

**구조 설명은 이 정도로 충분합니다.**

> “Controller는 요청 주소와 실행할 메서드를 연결하고, Service는 권한과 처리 조건을 확인합니다. DB는 JdbcTemplate으로 조회하거나 저장합니다.”

회원 기능은 UserRepository를 거칩니다. 금연 기록·대시보드·커뮤니티는 Service에서 JdbcTemplate을 직접 사용합니다.

주소 질문이 나오면 [vite.ui.config.ts](../frontend/vite.ui.config.ts)를 엽니다.

> “프런트는 5173 포트, 백엔드는 8080 포트입니다. 화면의 /api 요청은 Vite 프록시가 백엔드로 전달합니다.”

## 4. 로그인 — 인증 상태 확인

**조작 순서**

1. 비회원 상태에서 Network의 me 요청을 확인합니다. 401은 로그인하지 않은 상태입니다.
2. 회원 계정으로 로그인합니다.
3. 새로고침한 뒤 me 요청이 200이고 사용자 정보가 반환되는지 확인합니다.

**발표 멘트**

> “페이지가 열리면 로그인 상태를 확인합니다. 로그인 전에는 401이 반환되고, 로그인하면 서버가 계정을 검증해 인증 쿠키를 발급합니다. 이후 사용자 정보를 정상적으로 조회할 수 있습니다.”

| 열 파일 | 짚어줄 부분 |
|---|---|
| [AuthProvider.tsx](../frontend/features/auth/AuthProvider.tsx) | signIn, restore |
| [authClient.ts](../frontend/features/auth/authClient.ts) | 쿠키를 포함한 fetch 요청 |
| [LoginController.java](../backend/soom/src/main/java/com/example/demo/controller/LoginController.java) | /login, /me 매핑 |
| [LoginService.java](../backend/soom/src/main/java/com/example/demo/service/LoginService.java) | 계정·비밀번호 검증 |
| [UserRepository.java](../backend/soom/src/main/java/com/example/demo/repository/UserRepository.java) | 회원 DB 조회 |

**확인:** SQL 1번은 회원 존재를 보여줍니다. 로그인 성공 자체는 Network의 응답으로 확인합니다. 로그인할 때마다 회원 행이 추가되는 것은 아닙니다.

## 5. 금연 기록 — 입력값이 저장되는 과정

**조작 순서**

1. 회원 상태에서 금연 시작일·하루 흡연량·한 갑 가격·개비 수를 입력합니다.
2. 저장하고 Network의 quit-records 요청에서 Payload를 확인합니다.
3. SQL 2번을 실행해 입력값과 DB 행을 비교합니다.

**발표 멘트**

> “입력한 금연 시작일과 흡연 습관을 서버에 저장합니다. 화면에서 넣은 흡연량과 담배 가격이 DB에서도 동일하게 조회됩니다. 이 기록을 기준으로 금연 시간과 절약 금액을 계산합니다.”

**열 파일 순서**

[QuitRecordForm.tsx](../frontend/features/records/QuitRecordForm.tsx) → [useQuitRecords.ts](../frontend/features/records/useQuitRecords.ts) → [QuitRecordController.java](../backend/soom/src/main/java/com/example/demo/controller/QuitRecordController.java) → [QuitRecordService.java](../backend/soom/src/main/java/com/example/demo/service/QuitRecordService.java)

| 화면/API 필드 | DB 컬럼 |
|---|---|
| start / end | start_date / end_date |
| daily | daily_cigarettes |
| price | cigarette_price |
| packSize | cigarettes_per_pack |

테이블은 **no_smoking_record**입니다. 절약 금액은 **경과 일수 × 하루 개비 ÷ 한 갑 개비 × 한 갑 가격**으로 계산합니다. 경과 일수에는 하루 미만의 시간도 반영됩니다.

## 6. 출석 — 코드 매핑을 자세히 보여줄 구간

### 6-1. 버튼과 API 요청

**조작:** SQL 4·5번으로 출석 전 상태 확인 → 사이트에서 출석 체크 → Network의 attendance 클릭 → Payload 확인

[App.tsx · 출석 버튼 처리](../frontend/app/App.tsx#L249)에서 다음 코드를 보여줍니다.

~~~ts
void run("/attendance", { recordId: active.id }, "오늘의 출석을 저장했어요.");
~~~

> “버튼을 누르면 현재 금연 기록의 ID를 보냅니다. recordId는 회원 번호가 아니라 금연 기록 번호입니다.”

run은 [useDashboard.ts · saveChanges](../frontend/features/dashboard/useDashboard.ts#L152)를 호출합니다. 회원 출석은 **POST /api/dashboard/attendance**로 전달됩니다.

### 6-2. Controller의 주소 매핑

[DashboardController.java](../backend/soom/src/main/java/com/example/demo/controller/DashboardController.java#L47)를 엽니다.

~~~java
@PostMapping("/attendance")
public DashboardService.Snapshot attend(
    @AuthenticationPrincipal CurrentUserResponse user,
    @Valid @RequestBody RecordAction input
) {
    return service.attend(user.userId(), input.recordId());
}
~~~

**발표 멘트**

> “클래스의 공통 주소인 /api/dashboard와 메서드의 /attendance가 합쳐져 요청 주소가 됩니다. RequestBody로 금연 기록 ID를 받고, 사용자 ID는 로그인 인증 정보에서 가져옵니다.”

### 6-3. Service 처리와 DB 확인

[DashboardService.java](../backend/soom/src/main/java/com/example/demo/service/DashboardService.java#L334)의 attend를 엽니다.

**읽는 순서:** 사용자 잠금 → 기록 소유권·진행 상태 확인 → 오늘 출석 여부 확인 → 출석 저장 → 50P 반영

> “서버는 본인의 진행 중인 기록인지와 중복 출석 여부를 확인합니다. 출석과 포인트 지급은 하나의 트랜잭션에서 처리합니다.”

SQL **4번과 5번을 다시 실행**합니다.

| 확인 위치 | 기대 결과 |
|---|---|
| no_smoking_attendance | 오늘 날짜, reward_points 50 |
| point | 오늘의 출석, amount 50 |
| 화면 | 출석 완료, 이전보다 50P 증가 |

여정 보상이 이미 있을 수 있으므로 총 포인트가 50이라고 말하지 말고, **출석 전후 50P 증가**를 보여주세요.

## 7. 나무 — 포인트 사용과 성장 확인

**조작:** 물 주기 → SQL 5·6번 재실행

**발표 멘트**

> “출석으로 받은 포인트를 사용해 나무를 키웁니다. 물을 주면 30포인트가 차감되고 성장률이 5 증가합니다. 포인트 내역과 나무 상태가 DB에 함께 반영됩니다.”

| 연결 | 확인 위치 |
|---|---|
| 화면 동작 | [App.tsx](../frontend/app/App.tsx)의 nurture |
| API | POST /api/dashboard/tree |
| 서버 처리 | [DashboardService.java](../backend/soom/src/main/java/com/example/demo/service/DashboardService.java#L356)의 nurture |
| DB | point, no_smoking_tree |

성장률은 최대 100입니다. 비료는 80P를 사용해 성장률을 15 올립니다.

## 8. 커뮤니티 — 게시글 저장 확인

**조작 순서**

1. SQL 10번의 조회를 실행해 작성 전 상태를 보여줍니다.
2. 제목은 **중간 시연 DB 연결 확인**, 내용은 **화면에서 작성한 게시글을 DB에서 확인합니다.**로 등록합니다.
3. Network 필터를 posts로 바꾸고 새 요청을 클릭합니다.
4. Payload의 title·content를 보여줍니다.
5. SQL 10번을 다시 실행해 같은 제목·내용의 행을 확인합니다. 댓글·좋아요를 이어서 보려면 10번의 SET 문도 실행합니다.

**발표 멘트**

> “입력한 제목과 내용이 posts API로 전달됩니다. 서버는 로그인한 사용자를 작성자로 저장하고, DB에서 방금 작성한 글을 조회할 수 있습니다.”

| 열 파일 | 짚어줄 부분 |
|---|---|
| [AppDialogs.tsx · 게시글 등록](../frontend/app/AppDialogs.tsx#L121) | 입력값을 모아 등록 요청 |
| [useDashboard.ts](../frontend/features/dashboard/useDashboard.ts) | /api/community를 붙여 요청 |
| [CommunityController.java](../backend/soom/src/main/java/com/example/demo/controller/CommunityController.java#L77) | 요청을 save 메서드로 연결 |
| [CommunityService.java](../backend/soom/src/main/java/com/example/demo/service/CommunityService.java#L241) | ID가 없으면 등록, 있으면 수정 |

**API:** POST /api/community/posts  
**주요 테이블:** post, post_category

새로고침 후 글이 유지되는 모습까지 보여줍니다. 새로고침만으로는 Local Storage와 구분하기 어려우므로 **DB 조회 결과를 함께 보여주는 것**이 핵심입니다.

시간이 남으면 [PostDetail.tsx](../frontend/features/community/PostDetail.tsx)에서 댓글·좋아요를 조작하고 SQL 11·12번으로 확인합니다.

## 9. 비회원은 Local Storage로 설명

**보여줄 위치:** F12 → Application → Local Storage → 현재 사이트 주소

| 키 | 저장하는 것 |
|---|---|
| soom-guest-quit-records-v1 | 현재 진행 중인 금연 기록 하나 |
| soom-guest-dashboard-v1 | 비회원 출석·포인트·목표 |

**발표 멘트**

> “비회원은 현재 금연 기록 하나만 브라우저에 저장합니다. 새로고침해도 현재 기록은 유지되지만, 리셋하면 이전 기록과 보상·목표는 남지 않습니다. 회원은 DB에 기록을 저장하고 종료된 기록도 보관합니다.”

금연 기록 저장은 [guestRecordStorage.ts](../frontend/features/records/guestRecordStorage.ts), 보상·목표는 [guestDashboardStorage.ts](../frontend/features/dashboard/guestDashboardStorage.ts)입니다.

비회원도 공개 커뮤니티와 기준 정보는 API로 조회합니다. 비회원 개인 출석은 로컬 저장이므로 attendance 요청이 생기지 않습니다.

## 10. 마무리 멘트

> “지금까지 화면 입력, API 요청, 서버 처리, DB 저장 결과를 연결해서 확인했습니다. 회원 데이터는 계정별로 관리하고, 비회원은 브라우저에 현재 기록을 저장하도록 구분했습니다.”

---

## 참고 A. 추가 기능의 코드 매핑

발표 중 전부 읽을 필요는 없습니다. 해당 기능을 질문받았을 때만 파일을 여세요.

| 기능 | 프런트 파일 | 서버 파일 | DB / SQL 번호 |
|---|---|---|---|
| 회원가입 | [AuthScreen.tsx](../frontend/features/auth/AuthScreen.tsx), [signupClient.ts](../frontend/features/auth/signupClient.ts) | [SignupController.java](../backend/soom/src/main/java/com/example/demo/controller/SignupController.java), [SignupService.java](../backend/soom/src/main/java/com/example/demo/service/SignupService.java) | user / 1 |
| 이메일 인증 | AuthScreen.tsx, signupClient.ts | [EmailVerificationController.java](../backend/soom/src/main/java/com/example/demo/controller/EmailVerificationController.java), [EmailVerificationService.java](../backend/soom/src/main/java/com/example/demo/service/EmailVerificationService.java) | 인증번호는 서버 메모리, 가입 완료 시 user.email_verified_at / 1 |
| 비회원 기록 가져오기 | useQuitRecords.ts, App.tsx | QuitRecordController.java, QuitRecordService.java | no_smoking_record, quit_record_import / 2·3 |
| 여정·응원 문구 | useDashboard.ts | CommunityController.catalog, DashboardService.catalog | no_smoking_travel, fighting / 7·8 |
| 여정 보상 | useDashboard.ts | DashboardController.sync, DashboardService.sync | no_smoking_travel_progress, point / 5·7 |
| 위시리스트 | AppDialogs.tsx, FeaturePages.tsx | DashboardController, DashboardService.saveWish | wishlist / 9 |
| 댓글·답글·좋아요 | PostDetail.tsx | CommunityController, CommunityService | comment, comment_reply_target, post_like / 11·12 |
| 이미지 | AppDialogs.tsx | CommunityService | post_image, post_image_binary / 13 |
| 신고 | AppDialogs.tsx | CommunityService.report | report / 14 |
| 알림 | AppDialogs.tsx | DashboardController, DashboardService | notification / 15 |
| 이메일 찾기 | [RecoveryScreen.tsx](../frontend/features/auth/RecoveryScreen.tsx) | [AccountRecoveryController.java](../backend/soom/src/main/java/com/example/demo/controller/AccountRecoveryController.java), [EmailRecoveryService.java](../backend/soom/src/main/java/com/example/demo/service/EmailRecoveryService.java), [EmailRecoveryRepository.java](../backend/soom/src/main/java/com/example/demo/repository/EmailRecoveryRepository.java) | email_recovery_hint, email_recovery_attempt / 17은 질문 등록 확인 |
| 비밀번호 재설정 | RecoveryScreen.tsx | [AccountRecoveryService.java](../backend/soom/src/main/java/com/example/demo/service/AccountRecoveryService.java), [PasswordResetRepository.java](../backend/soom/src/main/java/com/example/demo/repository/PasswordResetRepository.java) | password_reset_token, user / 18 |
| 관리자 권한 | [AdminGate.tsx](../frontend/features/auth/AdminGate.tsx) | [AdminAccessController.java](../backend/soom/src/main/java/com/example/demo/controller/AdminAccessController.java) | 로그인 계정의 ADMIN 역할 |

관리자 화면은 [AdminDemo.tsx](../frontend/features/admin/AdminDemo.tsx)의 **데모**입니다. 관리자 인증과 데모 통계·관리 동작을 구분해 설명하세요.

## 참고 B. API 주소 빠른 찾기

| 기능 | 메서드·주소 |
|---|---|
| 이메일·닉네임 중복 | GET /api/auth/check-email, GET /api/auth/check-nickname |
| 이메일 인증 | POST /api/auth/email/send, POST /api/auth/email/verify |
| 회원가입 | POST /api/auth/signup |
| 로그인·로그아웃 | POST /api/auth/login, POST /api/auth/logout |
| 인증 상태·CSRF | GET /api/auth/me, GET /api/auth/csrf |
| 금연 기록 목록·생성 | GET /api/quit-records, POST /api/quit-records |
| 금연 기록 수정·종료 | POST /api/quit-records/{id}/update |
| 금연 기록 삭제·가져오기 | POST /api/quit-records/{id}/delete, POST /api/quit-records/import |
| 대시보드 조회·동기화 | GET /api/dashboard, POST /api/dashboard/sync |
| 출석·나무 | POST /api/dashboard/attendance, POST /api/dashboard/tree |
| 목표 등록·수정·삭제 | POST /api/dashboard/wishes, POST /api/dashboard/wishes/{id}/delete |
| 공개 게시글·기준 정보 | GET /api/community, GET /api/community/catalog |
| 게시글 등록·수정 | POST /api/community/posts |
| 게시글 상세 진입·삭제 | POST /api/community/{id}/view, POST /api/community/{id}/delete |
| 좋아요·댓글 | POST /api/community/{id}/like, POST /api/community/{id}/comments |
| 댓글 수정·삭제 | POST /api/community/comments/{id}/update, POST /api/community/comments/{id}/delete |
| 이미지 조회·신고 | GET /api/community/images/{id}, POST /api/community/reports |
| 알림 읽음·삭제 | POST /api/dashboard/notifications/{id}/read, POST /api/dashboard/notifications/{id}/delete |
| 알림 전체 삭제 | POST /api/dashboard/notifications/delete-all |
| 이메일 찾기 | POST /api/auth/find-id/questions, POST /api/auth/find-id |
| 비밀번호 복구 | POST /api/auth/password/request, POST /api/auth/password/validate, POST /api/auth/password/reset |
| 관리자 로그인·권한 | POST /api/auth/admin/login, GET /api/admin/access |

## 참고 C. 개발자 도구에서 막히면

| 상황 | 할 일 |
|---|---|
| Payload가 안 보임 | Network 목록에서 요청 이름을 먼저 클릭. 상세 창의 Payload 선택 |
| Network 탭이 안 보임 | 개발자 도구 상단의 »에서 찾기 |
| attendance가 안 나옴 | 로그인 여부 확인. 비회원 출석은 로컬 저장 |
| posts가 안 나옴 | 이전 Filter를 지우고, Network를 연 상태에서 글 등록 |
| 로그인 전 me 401 | 정상적인 비회원 상태 |
| 로그인 후에도 me 401 | 로그인한 주소와 현재 주소가 같은지 확인. localhost와 127.0.0.1 통일 |
| DB 값이 안 바뀜 | 버튼 동작 성공 여부 → SQL 변수 ID → 선택한 DB 순서로 확인 |

## 참고 D. 설명할 때 구분할 점

- 이메일 인증번호는 서버 메모리에 있습니다. DB에서는 가입 완료 후 인증 시각을 확인합니다.
- 금연 여정 보상은 회원 데이터 동기화 시 확인합니다. 항상 실행되는 백그라운드 지급으로 설명하지 않습니다.
- quit_record_import는 일반 기록 생성의 중복 방지에도 쓰입니다. 비회원 이전은 /import 요청과 함께 보여줍니다.
- 비회원에서 계정으로 가져오는 것은 현재 금연 기록입니다. 비회원 포인트 전체 이전으로 설명하지 않습니다.
- 게시글 삭제는 deleted_at을 기록합니다. 현재 댓글 삭제는 해당 댓글과 하위 답글의 행을 제거합니다.

## 참고 E. 리팩토링 후 파일 위치

| 역할 | 현재 파일 |
|---|---|
| 앱 본체 | [App.tsx](../frontend/app/App.tsx) |
| 페이지 선택·기능 화면 | [FeaturePages.tsx](../frontend/app/FeaturePages.tsx) |
| 등록·수정·알림 모달 | [AppDialogs.tsx](../frontend/app/AppDialogs.tsx) |
| 헤더·푸터 | [SiteLayout.tsx](../frontend/app/SiteLayout.tsx) |
| 게시글 상세 | PostDetail.tsx |
| 금연 기록 입력 폼 | QuitRecordForm.tsx |
| 관리자 데모 | AdminDemo.tsx |
| 공통 타입 | [appTypes.ts](../frontend/app/appTypes.ts), [appContext.ts](../frontend/app/appContext.ts) |
| 금액·날짜 계산 | [formatters.ts](../frontend/app/formatters.ts) |

기존 workspace.tsx는 역할별 파일로 분리됐습니다. app/page.tsx는 App.tsx를 연결하는 호환용 엔트리입니다.

---

검토 기준: 현재 소스의 파일명·함수·API 매핑과 SQL 번호를 대조했습니다. 이 대본 수정 과정에서 서버를 실행하거나 DB 조회를 수행하지 않았습니다. SQL은 시연 환경에서 실행 결과를 확인하세요.
