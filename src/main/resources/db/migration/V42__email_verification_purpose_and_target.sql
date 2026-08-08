-- 이메일 변경 검증(SPEC-0011 R7·R8)을 기존 검증 테이블에 얹는다.
--
-- **별도 테이블을 만들지 않는다.** 코드 발급·만료·시도 상한·쿨다운 로직이 가입 검증과 완전히
-- 같아 VerificationPolicy와 EmailVerification 애그리거트를 그대로 쓴다. 테이블을 나누면
-- 같은 규칙을 두 번 구현하게 된다.
--
-- 두 컬럼을 더한다:
--   purpose               — 가입(REGISTRATION) / 이메일 변경(EMAIL_CHANGE) 구분.
--                           재발송 시 코드를 어디로 보낼지가 갈린다(현재 이메일 vs 새 이메일).
--   target_email_encrypted — 확정 시 적용할 새 이메일. **암호문으로만 저장한다**(POL-012) —
--                           검증 통과 전이라도 개인정보다. 가입 검증에는 없어 NULL이다.

ALTER TABLE email_verification
    ADD COLUMN purpose VARCHAR(20) NOT NULL DEFAULT 'REGISTRATION',
    ADD COLUMN target_email_encrypted VARCHAR(512);

-- 기본값은 기존 행을 메우기 위한 것이다. 앞으로는 애플리케이션이 항상 값을 넣으므로 떼어낸다 —
-- 남겨 두면 purpose를 빠뜨린 코드가 조용히 가입 검증으로 처리된다.
ALTER TABLE email_verification ALTER COLUMN purpose DROP DEFAULT;

ALTER TABLE email_verification ADD CONSTRAINT ck_ev_purpose
    CHECK (purpose IN ('REGISTRATION', 'EMAIL_CHANGE'));

-- 이메일 변경은 대상 주소가 반드시 있어야 하고, 가입 검증에는 있어서는 안 된다.
-- 이 제약이 "purpose와 대상의 짝이 맞는다"를 DB에서 보증한다.
ALTER TABLE email_verification ADD CONSTRAINT ck_ev_target_email
    CHECK (
        (purpose = 'EMAIL_CHANGE' AND target_email_encrypted IS NOT NULL)
        OR (purpose = 'REGISTRATION' AND target_email_encrypted IS NULL)
    );

-- 재발송 쿨다운은 용도별로 판정한다 — 가입 검증과 이메일 변경이 쿨다운을 공유하면
-- 한쪽을 보낸 직후 다른 쪽이 막힌다.
CREATE INDEX idx_ev_member_purpose ON email_verification (member_id, purpose, created_at DESC);
