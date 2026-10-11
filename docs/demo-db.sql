-- SOOM 중간 시연 DB 확인용 / MySQL / 2026-10-12
-- 현재 소스의 SQL·컬럼을 기준으로 작성. 실제 DB에서 실행 검증하지 않음.
-- DB 프로그램에서 SOOM이 사용하는 데이터베이스를 먼저 선택하세요.
-- 아래는 SELECT, SHOW, 연결별 변수 SET만 사용합니다. 저장 데이터를 변경하지 않습니다.
-- 전체를 한 번 실행하는 대신, 0번 설정 후 시연하는 기능의 블록만 실행하세요.
-- 같은 SQL 탭/연결에서 실행하세요. 재접속하거나 계정을 바꾸면 0번을 다시 실행하세요.

-- 0. 준비: 아래 이메일을 실제 시연 계정 이메일로 변경하세요.
SET @demo_email = '여기에_시연계정_이메일';
SET @demo_title = '중간 시연 DB 연결 확인';
SELECT DATABASE() AS selected_database, NOW() AS db_time,
       @@session.time_zone AS db_session_timezone;
SET @demo_user_id = (SELECT user_id FROM `user` WHERE email = @demo_email LIMIT 1);
SET @demo_record_id = (
  SELECT no_smoking_record_id FROM no_smoking_record
  WHERE user_id = @demo_user_id AND end_date IS NULL
  ORDER BY start_date DESC, no_smoking_record_id DESC LIMIT 1
);
SELECT @demo_user_id AS user_id, @demo_record_id AS current_record_id;
-- user_id가 NULL: 이메일 또는 선택한 DB 확인. 가입 전이면 정상.
-- current_record_id가 NULL: 현재 진행 기록 없음. 화면에서 생성/가져오기 후 2번 실행.

-- 1. 회원가입 / 로그인에 사용되는 계정 확인
-- 가입 전후 같은 SELECT를 실행. 비밀번호·해시·토큰은 출력하지 않습니다.
SELECT user_id, email, nickname, role, status, email_verified_at, created_at
FROM `user` WHERE email = @demo_email;
-- 가입 직후에는 ID 변수를 다시 갱신합니다.
SET @demo_user_id = (SELECT user_id FROM `user` WHERE email = @demo_email LIMIT 1);
-- 로그인은 새 회원 행을 만드는 동작이 아닙니다.
-- Network의 POST /api/auth/login 및 새로고침 후 GET /api/auth/me 200으로 확인.

-- 2. 금연 기록 생성 / 수정 / 종료 / 삭제 및 비회원 기록 가져오기 후 확인
SELECT no_smoking_record_id, user_id, start_date, end_date, status,
       daily_cigarettes, cigarette_price, cigarettes_per_pack, created_at, updated_at
FROM no_smoking_record WHERE user_id = @demo_user_id
ORDER BY start_date DESC, no_smoking_record_id DESC;
-- 진행 기록을 만들거나 변경한 뒤 이 SET도 실행하세요.
SET @demo_record_id = (
  SELECT no_smoking_record_id FROM no_smoking_record
  WHERE user_id = @demo_user_id AND end_date IS NULL
  ORDER BY start_date DESC, no_smoking_record_id DESC LIMIT 1
);
SELECT @demo_record_id AS current_record_id;
-- 종료하면 end_date가 생깁니다. 삭제하면 해당 행이 없어집니다.

-- 3. 기록 생성/가져오기 중복 방지 이력 (보조 확인)
-- 이 테이블은 일반 생성에도 쓰이므로, 행 존재만으로 비회원 이전을 증명하지 않습니다.
-- Network의 /api/quit-records/import 요청과 함께 확인하세요.
SELECT user_id, client_id, record_id FROM quit_record_import
WHERE user_id = @demo_user_id ORDER BY record_id DESC;

-- 4. 출석: 버튼 클릭 전후 같은 SELECT 실행. 클릭 후 오늘 날짜와 50P 확인
SELECT no_smoking_record_id, attendance_date, reward_points, created_at
FROM no_smoking_attendance WHERE no_smoking_record_id = @demo_record_id
ORDER BY attendance_date DESC;
-- 날짜별 중복 여부: 각 행의 attendance_rows가 1인지 확인
SELECT attendance_date, COUNT(*) AS attendance_rows, SUM(reward_points) AS reward_points
FROM no_smoking_attendance WHERE no_smoking_record_id = @demo_record_id
GROUP BY attendance_date ORDER BY attendance_date DESC;

-- 5. 포인트 잔액 및 내역: 출석 +50 / 물 주기 -30 / 비료 -80
SELECT COALESCE(SUM(amount), 0) AS total_points
FROM `point` WHERE user_id = @demo_user_id;
SELECT point_id, amount, point_type, description, created_at
FROM `point` WHERE user_id = @demo_user_id ORDER BY point_id DESC LIMIT 30;
-- 여정 보상이 함께 들어올 수 있으므로 총액뿐 아니라 description과 amount를 확인.

-- 6. 나무: 물 주기 전후 성장률 +5, water_count +1 확인 (최대 성장률 100)
SELECT no_smoking_record_id, growth_rate, water_count, fertilizer_count,
       is_fully_grown, created_at, updated_at
FROM no_smoking_tree WHERE no_smoking_record_id = @demo_record_id;
-- 아직 돌보지 않은 나무는 DB 행이 없을 수 있습니다.

-- 7. 금연 여정 기준표: 공개 catalog API의 기준 데이터
SELECT no_smoking_travel_id, title, required_minutes, reward_points,
       travel_order, description
FROM no_smoking_travel WHERE travel_order > 0
ORDER BY travel_order, no_smoking_travel_id;
-- 현재 금연 기록에서 달성한 여정
SELECT p.no_smoking_record_id, t.title, t.required_minutes,
       t.reward_points, p.achieved_at
FROM no_smoking_travel_progress p
JOIN no_smoking_travel t ON t.no_smoking_travel_id = p.no_smoking_travel_id
WHERE p.no_smoking_record_id = @demo_record_id
ORDER BY t.required_minutes;

-- 8. 오늘의 응원 문구: 활성화된 DB 문구
SELECT fighting_id, content, is_active FROM fighting
WHERE is_active = TRUE ORDER BY fighting_id;

-- 9. 위시리스트: 화면에서 목표 등록/수정/삭제 후 재실행
SELECT w.wishlist_id, w.no_smoking_record_id, w.item_name, w.target_price,
       w.status, w.achieved_at, w.created_at, w.updated_at
FROM wishlist w JOIN no_smoking_record r
  ON r.no_smoking_record_id = w.no_smoking_record_id
WHERE r.user_id = @demo_user_id ORDER BY w.wishlist_id DESC;

-- 10. 커뮤니티 게시글: 같은 제목으로 작성 전/후 비교
-- 제목은 @demo_title에 설정한 '중간 시연 DB 연결 확인'을 그대로 사용하세요.
SELECT p.post_id, p.user_id, u.nickname, p.title, p.content,
       c.category, p.view_count, p.created_at, p.updated_at, p.deleted_at
FROM post p JOIN `user` u ON u.user_id = p.user_id
LEFT JOIN post_category c ON c.post_id = p.post_id
WHERE p.user_id = @demo_user_id AND p.title = @demo_title
ORDER BY p.post_id DESC;
-- 글 작성 후 아래 SET 실행. 삭제된 글도 확인할 수 있도록 deleted_at 조건은 생략.
SET @demo_post_id = (
  SELECT post_id FROM post WHERE user_id = @demo_user_id AND title = @demo_title
  ORDER BY post_id DESC LIMIT 1
);
SELECT @demo_post_id AS demo_post_id;
-- 이후 제목을 수정해도 같은 연결에서는 이 ID로 계속 조회할 수 있습니다.
SELECT post_id, title, content, view_count, updated_at, deleted_at
FROM post WHERE post_id = @demo_post_id;
-- 글 삭제는 행 삭제가 아니라 deleted_at 기록. 공개 목록에서는 제외됩니다.

-- 11. 댓글/답글: 댓글 작성·수정·삭제 후 확인
SELECT c.comment_id, c.post_id, c.user_id, u.nickname, c.content,
       c.parent_comment_id, t.reply_to_id, c.created_at, c.updated_at, c.deleted_at
FROM `comment` c JOIN `user` u ON u.user_id = c.user_id
LEFT JOIN comment_reply_target t ON t.comment_id = c.comment_id
WHERE c.post_id = @demo_post_id ORDER BY c.created_at, c.comment_id;
-- 최신 소스에서 댓글 삭제는 해당 댓글과 하위 답글의 실제 행을 삭제합니다.
-- 삭제 전 ID를 기록해두면 삭제 후 목록에서 사라진 것을 확인할 수 있습니다.

-- 12. 좋아요: 누르기 전후 행 추가, 취소 후 행 제거 확인
SELECT post_id, user_id, created_at FROM post_like
WHERE post_id = @demo_post_id ORDER BY created_at;
SELECT COUNT(*) AS like_count FROM post_like WHERE post_id = @demo_post_id;

-- 13. 첨부 이미지: 파일 경로와 DB 바이너리 크기 확인 (이미지 원문은 출력하지 않음)
SELECT i.image_id, i.post_id, i.image_order, i.image_url,
       b.mime_type, OCTET_LENGTH(b.image_data) AS image_bytes
FROM post_image i LEFT JOIN post_image_binary b ON b.image_id = i.image_id
WHERE i.post_id = @demo_post_id ORDER BY i.image_order, i.image_id;

-- 14. 신고: 시연 계정이 접수한 신고 확인
SELECT reporter_id, target_type, target_id, reason, status, created_at
FROM report WHERE reporter_id = @demo_user_id ORDER BY created_at DESC LIMIT 30;
-- 관리자 데모 화면의 처리 결과를 실제 DB 처리라고 소개하지 마세요.

-- 15. 알림: 읽기 전후 is_read / read_at 비교. 삭제하면 해당 행이 없어집니다.
SELECT notification_id, notification_type, content, is_read, created_at, read_at
FROM notification WHERE user_id = @demo_user_id ORDER BY notification_id DESC;
SELECT COUNT(*) AS unread_count FROM notification
WHERE user_id = @demo_user_id AND is_read = FALSE;

-- 16. 활동 기록: 포인트 지급/사용 등 처리 내역
SELECT user_id, activity_type, description, created_at
FROM activity_record WHERE user_id = @demo_user_id ORDER BY created_at DESC LIMIT 30;

-- 17. 복구 질문 등록 여부 (답변과 해시는 노출하지 않음)
SELECT user_id, question_number,
       CASE WHEN CHAR_LENGTH(answer_hash) > 0 THEN '등록됨' ELSE '없음' END AS answer_status
FROM email_recovery_hint WHERE user_id = @demo_user_id ORDER BY question_number;

-- 18. 비밀번호 재설정 요청/사용 상태 (토큰과 비밀번호는 노출하지 않음)
SELECT user_id, sent_at, expires_at, used_at FROM password_reset_token
WHERE user_id = @demo_user_id;
-- 요청 후 sent_at/expires_at, 링크로 변경 완료 후 used_at을 확인.
-- 가입 이메일 인증번호는 서버 메모리에서 관리하므로 인증번호 조회 SQL은 없습니다.

-- 19. 요청 중복 처리 방지 보조 이력
SELECT user_id, request_id, created_at FROM dashboard_request
WHERE user_id = @demo_user_id ORDER BY created_at DESC LIMIT 20;

-- 20. 테이블/컬럼 오류가 날 때 현재 DB 구조 확인 (필요한 것만 실행)
-- SHOW COLUMNS FROM no_smoking_record;
-- SHOW COLUMNS FROM no_smoking_attendance;
-- SHOW COLUMNS FROM post;
-- SHOW COLUMNS FROM `comment`;
-- SQL 결과가 비어 있으면 먼저 @demo_user_id / @demo_record_id / @demo_post_id 확인.
-- 비회원 작업은 Local Storage를 확인합니다. 이 SQL로 비회원 저장을 증명하지 않습니다.
