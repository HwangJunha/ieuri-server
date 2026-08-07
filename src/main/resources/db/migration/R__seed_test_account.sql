-- 개발용 테스트 계정 시드(반복 실행 안전 / idempotent).
--
-- **이 시드는 로컬·개발 환경 전용이다.** R__seed_community.sql과 같은 성격이며, R__ 파일은
-- 체크섬이 바뀔 때마다 재적용되고 환경 구분을 하지 않는다. 프로덕션 배포 전에 이 파일을
-- 지우거나 Flyway locations에서 제외해야 한다. 아래 pepper·암호화 키도 로컬 전용 값이라
-- 프로덕션 키로는 이 해시가 맞지 않는다(로그인이 실패할 뿐 데이터가 깨지지는 않는다).
--
-- 계정: tarot10@naver.com / !!tjdgk1415 (member.id = 14, R__seed_community.sql이 쓰는 그 14번)
-- 이메일 인증 완료·만 14세 확인 상태이며 시행 중인 약관 전부에 동의시킨다 —
-- 그래야 필수 약관 게이트(SPEC-0011 R11)에 걸리지 않고 바로 화면을 볼 수 있다.
--
-- **비밀번호는 SQL에서 만들고, 이메일 암호문은 상수로 박는다.**
--   - 비밀번호: 앱의 해시가 `bcrypt(base64(HMAC-SHA256(password, pepper)))`인데 pgcrypto가
--     두 함수를 다 갖고 있어 그대로 재현된다.
--   - 이메일: AES-256-GCM이고 pgcrypto의 encrypt_iv는 CBC/ECB만 지원해 **SQL로 만들 수 없다.**
--     그래서 로컬 키로 미리 암호화해 둔 값을 상수로 둔다. 복호화하면 tarot10@naver.com이다.
--
-- 두 값 모두 application-local.yaml의 로컬 전용 키에 묶여 있다. 그 파일의 pepper나
-- encryption.key를 바꾸면 **여기도 함께 바꿔야 한다** — 안 바꾸면 로그인이나 me 조회가
-- 조용히 깨진다(데이터가 상하지는 않는다).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) 회원. id를 못 박으려면 OVERRIDING SYSTEM VALUE가 필요하다(member.id는 IDENTITY다).
--    이미 있으면 인증 관련 필드만 되돌린다 — 탈퇴 테스트로 지웠다면 새로 생기고,
--    비밀번호를 바꿔 봤다면 원래 값으로 돌아온다.
INSERT INTO member (
    id, provider, provider_user_id, email_encrypted, email_verified,
    status, age_verified, password_hash, created_at, updated_at
) OVERRIDING SYSTEM VALUE
VALUES (
    14, 'EMAIL', 'tarot10@naver.com',
    -- 로컬 키로 미리 암호화한 tarot10@naver.com. base64(iv(12B) || ciphertext+tag) 포맷이다.
    '9n5nTpzMwmhdciQmpuGkVikRQT00I1SJCvCof3YvH40BnoFiAidQYWLVzxZf',
    TRUE, 'ACTIVE', TRUE,
    crypt(
        encode(hmac('!!tjdgk1415', 'IJPEbgPT4Bq9BO4/10GuzlDHpY2hHErWSuTaQHvwd8s=', 'sha256'), 'base64'),
        gen_salt('bf', 10)
    ),
    now(), now()
)
ON CONFLICT (id) DO UPDATE SET
    email_verified = TRUE,
    status         = 'ACTIVE',
    age_verified   = TRUE,
    -- 재적용 때마다 새 솔트로 다시 해시한다. 같은 비밀번호로 항상 로그인된다.
    password_hash  = crypt(
        encode(hmac('!!tjdgk1415', 'IJPEbgPT4Bq9BO4/10GuzlDHpY2hHErWSuTaQHvwd8s=', 'sha256'), 'base64'),
        gen_salt('bf', 10)
    ),
    updated_at     = now();

-- 시퀀스가 14를 지나치지 않았으면 앞으로 당긴다. 그러지 않으면 다음 가입이 14를 재발급해 충돌한다.
SELECT setval(
    pg_get_serial_sequence('member', 'id'),
    GREATEST((SELECT max(id) FROM member), 14)
);

-- 2) 프로필. 이미 있으면 닉네임을 건드리지 않는다 — 닉네임 변경(SPEC-0011 R5)을 시험한 뒤
--    재적용해도 바꿔 둔 값이 살아남는다.
--
--    **다른 회원이 'tarot10'을 쓰고 있으면 이 INSERT는 유니크 제약(V37)에 걸려 실패하고,
--    시드 전체가 롤백된다.** 자동으로 다른 이름을 고르지 않는 이유는, 조용히 다른 닉네임이
--    붙으면 어느 계정이 시드인지 알 수 없어지기 때문이다. 그 경우 상대 닉네임을 바꿔야 한다.
INSERT INTO member_profile (member_id, nickname, profile_image_url, created_at, updated_at)
VALUES (14, 'tarot10', NULL, now(), now())
ON CONFLICT (member_id) DO NOTHING;

-- 3) 기본 역할(USER). RBAC 시드가 먼저 돌아 role 행이 있어야 한다.
INSERT INTO member_role (member_id, role_id)
SELECT 14, r.id FROM role r WHERE r.code = 'USER'
ON CONFLICT DO NOTHING;

-- 4) 시행 중인 약관 전부에 동의. 코드별 최신 버전만 고른다 —
--    버전이 올라가면 이전 동의는 이월되지 않아 게이트가 걸리기 때문이다(R11).
--
--    동의 이력은 append-only라 유니크 제약이 없다. 중복 누적을 막기 위해
--    이 계정의 이력을 지우고 다시 넣는다(테스트 계정이라 법적 증빙 대상이 아니다).
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
-- 비밀번호가 맞는지 — TRUE여야 한다.
--   SELECT password_hash = crypt(
--       encode(hmac('!!tjdgk1415', 'IJPEbgPT4Bq9BO4/10GuzlDHpY2hHErWSuTaQHvwd8s=', 'sha256'), 'base64'),
--       password_hash) AS password_ok
--   FROM member WHERE id = 14;
--
-- 계정 상태 — email_verified·age_verified가 TRUE, status가 ACTIVE여야 한다.
--   SELECT id, provider, provider_user_id, email_verified, status, age_verified,
--          left(email_encrypted, 12) AS email_cipher_head
--   FROM member WHERE id = 14;
--
-- 약관 게이트 — 필수 약관이 전부 agreed=TRUE여야 홈 화면에 들어간다.
--   SELECT a.terms_code, t.required, a.agreed, a.terms_version
--   FROM member_terms_agreement a
--   JOIN terms t ON t.id = a.terms_id
--   WHERE a.member_id = 14 ORDER BY t.required DESC, a.terms_code;
--
-- 역할 — USER 한 건이어야 한다.
--   SELECT r.code FROM member_role mr JOIN role r ON r.id = mr.role_id WHERE mr.member_id = 14;
-- ============================================================
