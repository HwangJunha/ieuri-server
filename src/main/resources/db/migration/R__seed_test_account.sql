-- 개발용 테스트 계정 활성화 시드(반복 실행 안전 / idempotent).
--
-- **이 시드는 로컬·개발 환경 전용이다.** R__seed_community.sql과 같은 성격이며, R__ 파일은
-- 체크섬이 바뀔 때마다 재적용되고 환경 구분을 하지 않는다. 프로덕션 배포 전에 이 파일을
-- 지우거나 Flyway locations에서 제외해야 한다.
--
-- 대상: tarot10@naver.com / !!tjdgk1415
--
-- **계정을 만들지 않고, 있는 계정을 켜기만 한다.** 앱으로 가입한 뒤 이메일 인증만
-- 건너뛰게 하는 것이 목적이다. 이유는 두 가지다:
--
--  1. 비밀번호 해시가 `bcrypt(base64(HMAC-SHA256(pw, pepper)))`인데 pepper는
--     `${IEURI_PASSWORD_PEPPER:...}`라 **셸 환경변수가 기본값을 덮을 수 있다.** SQL에
--     해시를 박아 두면 그 셸에서만 맞고 다른 셸에서는 로그인이 조용히 실패한다.
--     비밀번호는 앱이 가입 시점에 쓴 값을 그대로 둔다.
--  2. 이메일은 AES-256-GCM(랜덤 IV)이라 pgcrypto의 encrypt_iv(CBC/ECB)로는 만들 수 없다.
--
-- 그래서 가입은 앱으로 한 번 하고(회원가입 화면 또는 registerEmailMember 뮤테이션),
-- 이 시드는 인증 관문만 열어 준다. 계정이 없으면 아무 일도 하지 않는다.
--
-- 켜는 것: email_verified(이메일 인증), status=ACTIVE(정지 해제), age_verified(연령 확인),
--          그리고 시행 중인 필수 약관 동의 — 미동의면 게이트에 걸린다(SPEC-0011 R11).

-- 1) 인증 관문 열기. 이메일 인증을 건너뛰게 하는 것이 이 시드의 핵심이다.
--    정지 관련 컬럼도 비워 정지 테스트 뒤에 되돌아오게 한다.
UPDATE member
SET email_verified    = TRUE,
    age_verified      = TRUE,
    status            = 'ACTIVE',
    suspended_at      = NULL,
    suspended_until   = NULL,
    suspension_reason = NULL,
    updated_at        = now()
WHERE provider_user_id = 'tarot10@naver.com'
  AND provider = 'EMAIL';

-- 2) 미소진 인증 챌린지 정리. 인증을 이미 통과시켰으므로 남겨 둘 이유가 없고,
--    재발송 쿨다운(60초)이 걸린 채로 남으면 인증 관련 화면을 시험하기 번거롭다.
DELETE FROM email_verification
WHERE member_id IN (
    SELECT id FROM member WHERE provider_user_id = 'tarot10@naver.com' AND provider = 'EMAIL'
);

-- 3) 기본 역할(USER). 가입 핸들러가 이미 부여하지만, 역할 회수를 시험한 뒤에도 돌아오게 한다.
INSERT INTO member_role (member_id, role_id)
SELECT m.id, r.id
FROM member m
CROSS JOIN role r
WHERE m.provider_user_id = 'tarot10@naver.com' AND m.provider = 'EMAIL'
  AND r.code = 'USER'
ON CONFLICT DO NOTHING;

-- 4) 시행 중인 약관 전부에 동의. 코드별 최신 버전만 고른다 —
--    버전이 올라가면 이전 동의는 이월되지 않아 게이트가 걸리기 때문이다(R11).
--
--    동의 이력은 append-only라 유니크 제약이 없다. 중복 누적을 막기 위해 이 계정의
--    이력을 지우고 다시 넣는다(테스트 계정이라 법적 증빙 대상이 아니다).
--    선택 약관 철회(R10)를 시험했다면 재적용 때 다시 동의 상태로 돌아온다.
DELETE FROM member_terms_agreement
WHERE member_id IN (
    SELECT id FROM member WHERE provider_user_id = 'tarot10@naver.com' AND provider = 'EMAIL'
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
WHERE m.provider_user_id = 'tarot10@naver.com' AND m.provider = 'EMAIL';

-- ============================================================
-- 확인용 쿼리 (실행되지 않는 주석이다. 필요할 때 복사해서 쓴다.)
--
-- 계정 상태 — email_verified·age_verified가 TRUE, status가 ACTIVE여야 로그인된다.
--   SELECT id, provider_user_id, email_verified, status, age_verified
--   FROM member WHERE provider_user_id = 'tarot10@naver.com';
--
-- 약관 게이트 — 필수 약관이 전부 agreed=TRUE여야 홈 화면에 들어간다.
--   SELECT a.terms_code, t.required, a.agreed, a.terms_version
--   FROM member_terms_agreement a
--   JOIN terms t ON t.id = a.terms_id
--   JOIN member m ON m.id = a.member_id
--   WHERE m.provider_user_id = 'tarot10@naver.com'
--   ORDER BY t.required DESC, a.terms_code;
--
-- 계정이 아직 없다면(시드가 아무 일도 하지 않은 경우) 앱으로 먼저 가입한다.
--   mutation { registerEmailMember(input: {
--     email: "tarot10@naver.com", password: "!!tjdgk1415", ageVerified: true,
--     agreements: [{termsCode: SERVICE, agreed: true}, {termsCode: PRIVACY, agreed: true},
--                  {termsCode: LOCATION, agreed: true}, {termsCode: MARKETING, agreed: true}]
--   }) { success userErrors { code message } } }
-- ============================================================
