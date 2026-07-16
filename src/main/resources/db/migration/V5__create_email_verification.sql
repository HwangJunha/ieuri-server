-- 이메일 검증 6자리 코드. 보안 필수사항:
--   ① 짧은 만료(expires_at) ② 시도 횟수 제한(attempts/max_attempts)
--   ③ 단회용(consumed) ④ 재발송 rate limit(created_at 기준, 앱 레이어)
-- public_id(UUID)가 가입 응답으로 나가는 검증 핸들 — member_id는 직접 노출하지 않는다.
-- 코드는 평문이 아닌 해시(code_hash)로 저장한다.
CREATE TABLE email_verification (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL,
    member_id    BIGINT       NOT NULL,
    code_hash    VARCHAR(128) NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    max_attempts INT          NOT NULL DEFAULT 5,
    consumed     BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_verification_public UNIQUE (public_id),
    CONSTRAINT fk_ev_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE INDEX idx_ev_member ON email_verification (member_id);
