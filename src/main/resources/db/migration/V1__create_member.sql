-- auth 도메인: 회원 애그리거트 루트.
-- 이메일 등 개인정보는 암호문(email_encrypted)으로만 저장한다.
-- (provider, provider_user_id) 유니크: 같은 SNS 계정 중복가입 불가, 다른 provider면 별개 회원(다계정 허용).
CREATE TABLE member (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider         VARCHAR(20)  NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    email_encrypted  VARCHAR(512) NOT NULL,
    email_verified   BOOLEAN      NOT NULL DEFAULT FALSE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    age_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_member_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT ck_member_provider CHECK (provider IN ('APPLE', 'GOOGLE', 'KAKAO')),
    CONSTRAINT ck_member_status   CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN'))
);
