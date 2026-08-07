-- 개발용 테스트 계정 시드(반복 실행 안전 / idempotent).
--
-- **이 시드는 로컬·개발 환경 전용이다.** R__seed_community.sql과 같은 성격이며, R__ 파일은
-- 체크섬이 바뀔 때마다 재적용되고 환경 구분을 하지 않는다. 프로덕션 배포 전에 이 파일을
-- 지우거나 Flyway locations에서 제외해야 한다.
--
-- 계정: tarot10@naver.com / !!tjdgk1415 (member.id = 14로 고정)
-- 이메일 인증 완료·연령 확인 완료·약관 전체 동의 상태로 **기동할 때마다 자동 생성된다.**
-- 탈퇴(SPEC-0011 R17)를 시험해 지웠더라도 다음 기동에서 그대로 돌아온다.
--
-- **id를 14로 못 박는 이유**는 R__seed_community.sql이 author_id=14로 글·댓글을 넣기
-- 때문이다. id가 달라지면 그 글들이 작성자 없는 상태로 남는다.
--
-- **해시와 암호문은 앱이 만든 값을 그대로 박았다.** 계산하지 않는 이유:
--
--  - 비밀번호 해시는 bcrypt(base64(HMAC-SHA256(pw, pepper)))인데, pepper는 환경변수로
--    덮어쓸 수 있는 값이라 **SQL에서 다시 계산하면 특정 셸에서만 맞는다.** 다른 곳에서는
--    로그인이 조용히 실패한다(실제로 INVALID_CREDENTIALS를 겪었다).
--  - 이메일은 AES-256-GCM(랜덤 IV)이라 pgcrypto의 encrypt_iv(CBC/ECB)로는 만들 수 없다.
--
-- 아래 두 값은 이 프로젝트의 로컬 키로 앱이 가입 시점에 직접 쓴 것이다. 그래서 계산이
-- 필요 없고, 대신 **키가 바뀌면 이 값들도 다시 떠 와야 한다**:
--   - 비밀번호 pepper 설정을 바꾸면 → 로그인이 실패한다(password_hash 교체 필요)
--   - ieuri.encryption.key를 바꾸면 → me 조회의 email 복호화가 실패한다(email_encrypted 교체)
-- 새 값은 앱으로 한 번 가입한 뒤 그 회원의 두 컬럼을 그대로 복사해 오면 된다.
--
-- 주의: 이 파일에 `달러표시{...}` 형태를 쓰지 말 것. **주석 안이어도** Flyway가 자리표시자로
-- 해석해 치환을 시도하고, 값이 없으면 기동이 실패한다(No value provided for placeholder).

-- 1) 회원. 이메일 인증(email_verified)이 켜진 채로 들어가는 것이 이 시드의 핵심이다 —
--    앱으로 가입하면 false로 시작해 메일의 6자리 코드를 넣어야 로그인이 된다.
--
--    id를 지정하려면 OVERRIDING SYSTEM VALUE가 필요하다(member.id는 IDENTITY다).
--    이미 있으면 인증 관련 컬럼만 되돌린다 — 정지를 시험했거나 인증을 껐더라도 복구되고,
--    비밀번호는 항상 !!tjdgk1415로 되돌아온다.
INSERT INTO member (
    id, provider, provider_user_id, email_encrypted, email_verified,
    status, age_verified, password_hash, created_at, updated_at
) OVERRIDING SYSTEM VALUE
VALUES (
    14, 'EMAIL', 'tarot10@naver.com',
    -- 로컬 encryption.key로 암호화된 tarot10@naver.com. base64(iv(12B) || ciphertext+tag).
    'cdliqSVt2uerTs5PiOfL4N7YqzFtFJ7e4kmcaYLq680v/Uxcf7DZ2h1tL9pV',
    TRUE, 'ACTIVE', TRUE,
    -- 로컬 pepper로 해시된 !!tjdgk1415.
    '$2a$10$B83.I4/zu2lO9P9QzCHayOQIcOww2ceW60XN6N4hwis6BiuECKC9.',
    now(), now()
)
ON CONFLICT (id) DO UPDATE SET
    provider          = EXCLUDED.provider,
    provider_user_id  = EXCLUDED.provider_user_id,
    email_encrypted   = EXCLUDED.email_encrypted,
    email_verified    = TRUE,
    status            = 'ACTIVE',
    age_verified      = TRUE,
    suspended_at      = NULL,
    suspended_until   = NULL,
    suspension_reason = NULL,
    password_hash     = EXCLUDED.password_hash,
    updated_at        = now();

-- 시퀀스가 14를 지나치지 않았으면 앞으로 당긴다. 그러지 않으면 다음 가입이 14를 재발급해
-- PK 충돌이 난다(IDENTITY는 이 INSERT로 시퀀스가 움직이지 않는다).
SELECT setval(
    pg_get_serial_sequence('member', 'id'),
    GREATEST((SELECT max(id) FROM member), 14)
);

-- 2) 미소진 인증 챌린지 정리. 이미 인증된 상태로 넣었으므로 남겨 둘 이유가 없고,
--    재발송 쿨다운(60초)이 걸린 채로 남으면 인증 화면을 시험하기 번거롭다.
DELETE FROM email_verification WHERE member_id = 14;

-- 3) 프로필. 닉네임은 회원 간 고유해야 하므로(SPEC-0011 R5) 이 계정 전용 값을 쓴다.
--    이미 있으면 건드리지 않는다 — 닉네임 변경을 시험한 값이 재적용 후에도 살아남는다.
INSERT INTO member_profile (member_id, nickname, profile_image_url, created_at, updated_at)
VALUES (14, '테스트계정', NULL, now(), now())
ON CONFLICT (member_id) DO NOTHING;

-- 4) 기본 역할(USER). 가입 핸들러가 하는 일을 대신한다.
INSERT INTO member_role (member_id, role_id)
SELECT 14, r.id FROM role r WHERE r.code = 'USER'
ON CONFLICT DO NOTHING;

-- 5) 시행 중인 약관 전부에 동의. 코드별 최신 버전만 고른다 —
--    버전이 올라가면 이전 동의는 이월되지 않아 게이트가 걸리기 때문이다(R11).
--
--    동의 이력은 append-only라 유니크 제약이 없다. 중복 누적을 막기 위해 이 계정의
--    이력을 지우고 다시 넣는다(테스트 계정이라 법적 증빙 대상이 아니다).
DELETE FROM member_terms_agreement WHERE member_id = 14;

INSERT INTO member_terms_agreement (member_id, terms_id, terms_code, terms_version, agreed, agreed_at)
SELECT 14, t.id, t.code, t.version, TRUE, now()
FROM terms t
JOIN (
    SELECT code, max(version) AS version
    FROM terms
    WHERE effective_at <= now()
    GROUP BY code
) latest ON latest.code = t.code AND latest.version = t.version;

-- ============================================================
-- 확인용 쿼리 (실행되지 않는 주석이다. 필요할 때 복사해서 쓴다.)
--
-- 계정 상태 — email_verified·age_verified가 TRUE, status가 ACTIVE여야 로그인된다.
--   SELECT id, provider_user_id, email_verified, status, age_verified
--   FROM member WHERE id = 14;
--
-- 로그인 — 성공하면 accessToken이 내려온다.
--   mutation { loginEmail(input: {email: "tarot10@naver.com", password: "!!tjdgk1415"}) {
--     success accessToken userErrors { code message } } }
--
-- 커뮤니티 시드와의 연결 — 14번이 쓴 글·댓글이 작성자를 찾을 수 있어야 한다.
--   SELECT (SELECT count(*) FROM community_post WHERE author_id = 14) AS posts,
--          (SELECT count(*) FROM community_comment WHERE author_id = 14) AS comments;
--
-- 약관 게이트 — 필수 약관이 전부 agreed=TRUE여야 홈 화면에 들어간다.
--   SELECT a.terms_code, t.required, a.agreed, a.terms_version
--   FROM member_terms_agreement a
--   JOIN terms t ON t.id = a.terms_id
--   WHERE a.member_id = 14 ORDER BY t.required DESC, a.terms_code;
-- ============================================================
