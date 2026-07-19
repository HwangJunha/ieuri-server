-- 비밀번호 재설정 6자리 코드. 이메일 검증과 동일한 보안 규칙:
--   ① 짧은 만료(expires_at) ② 시도 횟수 제한(attempts/max_attempts) ③ 단회용(consumed).
-- 코드는 평문이 아닌 해시(code_hash)로 저장한다. 재설정 성공 시 회원의 refresh 토큰을 전량 폐기한다(앱 레이어).
CREATE TABLE password_reset (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id    UUID         NOT NULL,
    member_id    BIGINT       NOT NULL,
    code_hash    VARCHAR(128) NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    max_attempts INT          NOT NULL DEFAULT 5,
    consumed     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_public UNIQUE (public_id),
    CONSTRAINT fk_pr_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE INDEX idx_pr_member ON password_reset (member_id);
