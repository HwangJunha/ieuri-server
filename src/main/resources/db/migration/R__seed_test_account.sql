-- 개발용 테스트 계정 시드(반복 실행 안전 / idempotent).
--
-- **이 시드는 로컬·개발 환경 전용이다.** R__seed_community.sql과 같은 성격이며, R__ 파일은
-- 체크섬이 바뀔 때마다 재적용되고 환경 구분을 하지 않는다. 프로덕션 배포 전에 이 파일을
-- 지우거나 Flyway locations에서 제외해야 한다.
--
-- 계정: tarot10@naver.com / !!tjdgk1415
-- 이메일 인증 완료·연령 확인 완료·약관 전체 동의 상태로 **기동할 때마다 자동 생성된다.**
-- 탈퇴(SPEC-0011 R17)를 시험해 지웠더라도 다음 기동에서 그대로 돌아온다.
--
-- **해시와 암호문은 앱이 만든 값을 그대로 박았다.** 계산하지 않는 이유:
--
--  - 비밀번호 해시는 `bcrypt(base64(HMAC-SHA256(pw, pepper)))`인데 pepper가
--    `${IEURI_PASSWORD_PEPPER:...}`라 **셸 환경변수가 기본값을 덮을 수 있다.** SQL에서
--    pgcrypto로 다시 계산하면 그 셸에서만 맞고 다른 셸에서는 로그인이 조용히 실패한다.
--    실제로 그렇게 만들었다가 INVALID_CREDENTIALS를 봤다.
--  - 이메일은 AES-256-GCM(랜덤 IV)이라 pgcrypto의 encrypt_iv(CBC/ECB)로는 만들 수 없다.
--
-- 아래 두 값은 이 프로젝트의 로컬 키로 앱이 가입 시점에 직접 쓴 것이다. 그래서 계산이
-- 필요 없고, 대신 **키가 바뀌면 이 값들도 다시 떠 와야 한다**:
--   - IEURI_PASSWORD_PEPPER를 바꾸면 → 로그인이 실패한다(password_hash 교체 필요)
--   - ieuri.encryption.key를 바꾸면 → me 조회의 email 복호화가 실패한다(email_encrypted 교체 필요)
-- 새 값은 앱으로 한 번 가입한 뒤 그 회원의 두 컬럼을 그대로 복사해 오면 된다.

-- 1) 회원. 이메일 인증(email_verified)이 켜진 채로 들어가는 것이 이 시드의 핵심이다 —
--    앱으로 가입하면 false로 시작해 메일의 6자리 코드를 넣어야 로그인이 된다.
--
--    id는 못 박지 않는다. IDENTITY가 알아서 발급하며, 탈퇴 후 재생성되면 값이 달라진다 —
--    시드가 참조하는 것은 id가 아니라 (provider, provider_user_id)다.
--
--    이미 있으면 인증 관련 컬럼만 되돌린다. 정지를 시험했거나 인증을 껐더라도 복구되고,
--    비밀번호는 항상 !!tjdgk1415로 되돌아온다.
INSERT INTO member (
    provider, provider_user_id, email_encrypted, email_verified,
    status, age_verified, password_hash, created_at, updated_at
)
VALUES (
    'EMAIL', 'tarot10@naver.com',
    -- 로컬 encryption.key로 암호화된 tarot10@naver.com. base64(iv(12B) || ciphertext+tag).
    'cdliqSVt2uerTs5PiOfL4N7YqzFtFJ7e4kmcaYLq680v/Uxcf7DZ2h1tL9pV',
    TRUE, 'ACTIVE', TRUE,
    -- 로컬 pepper로 해시된 !!tjdgk1415.
    '$2a$10$B83.I4/zu2lO9P9QzCHayOQIcOww2ceW60XN6N4hwis6BiuECKC9.',
    now(), now()
)
ON CONFLICT (provider, provider_user_id) DO UPDATE SET
    email_encrypted   = EXCLUDED.email_encrypted,
    email_verified    = TRUE,
    status            = 'ACTIVE',
    age_verified      = TRUE,
    suspended_at      = NULL,
    suspended_until   = NULL,
    suspension_reason = NULL,
    password_hash     = EXCLUDED.password_hash,
    updated_at        = now();

-- 2) 미소진 인증 챌린지 정리. 이미 인증된 상태로 넣었으므로 남겨 둘 이유가 없고,
--    재발송 쿨다운(60초)이 걸린 채로 남으면 인증 화면을 시험하기 번거롭다.
DELETE FROM email_verification
WHERE member_id IN (
    SELECT id FROM member WHERE provider = 'EMAIL' AND provider_user_id = 'tarot10@naver.com'
);

-- 3) 프로필. 닉네임은 회원 간 고유해야 하므로(SPEC-0011 R5) 이 계정 전용 값을 쓴다.
--    이미 있으면 건드리지 않는다 — 닉네임 변경을 시험한 값이 재적용 후에도 살아남는다.
INSERT INTO member_profile (member_id, nickname, profile_image_url, created_at, updated_at)
SELECT m.id, '테스트계정', NULL, now(), now()
FROM member m
WHERE m.provider = 'EMAIL' AND m.provider_user_id = 'tarot10@naver.com'
ON CONFLICT (member_id) DO NOTHING;

-- 4) 기본 역할(USER). 가입 핸들러가 하는 일을 대신한다.
INSERT INTO member_role (member_id, role_id)
SELECT m.id, r.id
FROM member m
CROSS JOIN role r
WHERE m.provider = 'EMAIL' AND m.provider_user_id = 'tarot10@naver.com'
  AND r.code = 'USER'
ON CONFLICT DO NOTHING;

-- 5) 시행 중인 약관 전부에 동의. 코드별 최신 버전만 고른다 —
--    버전이 올라가면 이전 동의는 이월되지 않아 게이트가 걸리기 때문이다(R11).
--
--    동의 이력은 append-only라 유니크 제약이 없다. 중복 누적을 막기 위해 이 계정의
--    이력을 지우고 다시 넣는다(테스트 계정이라 법적 증빙 대상이 아니다).
DELETE FROM member_terms_agreement
WHERE member_id IN (
    SELECT id FROM member WHERE provider = 'EMAIL' AND provider_user_id = 'tarot10@naver.com'
);

INSERT INTO member_terms_agreement (member_id, terms_id, terms_code, terms_version, agreed, agreed_at)
SELECT m.id, t.id, t.code, t.version, TRUE, now()
FROM member m
CROSS JOIN terms t
JOIN (
    SELECT code, max(version) AS version
    FROM terms
    WHERE effective_at <= now()
    GROUP BY code
) latest ON latest.code = t.code AND latest.version = t.version
WHERE m.provider = 'EMAIL' AND m.provider_user_id = 'tarot10@naver.com';

-- ============================================================
-- 확인용 쿼리 (실행되지 않는 주석이다. 필요할 때 복사해서 쓴다.)
--
-- 계정 상태 — email_verified·age_verified가 TRUE, status가 ACTIVE여야 로그인된다.
--   SELECT id, provider_user_id, email_verified, status, age_verified
--   FROM member WHERE provider_user_id = 'tarot10@naver.com';
--
-- 로그인 — 성공하면 accessToken이 내려온다.
--   mutation { loginEmail(input: {email: "tarot10@naver.com", password: "!!tjdgk1415"}) {
--     success accessToken userErrors { code message } } }
--
-- 약관 게이트 — 필수 약관이 전부 agreed=TRUE여야 홈 화면에 들어간다.
--   SELECT a.terms_code, t.required, a.agreed, a.terms_version
--   FROM member_terms_agreement a
--   JOIN terms t ON t.id = a.terms_id
--   JOIN member m ON m.id = a.member_id
--   WHERE m.provider_user_id = 'tarot10@naver.com'
--   ORDER BY t.required DESC, a.terms_code;
-- ============================================================
