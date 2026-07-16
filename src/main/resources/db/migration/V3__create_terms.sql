-- 약관 본문을 DB로 관리(버전 포함). 동의 이력이 특정 약관 버전을 가리킨다.
-- (code, version) 유니크: 같은 약관의 같은 버전은 하나만.
CREATE TABLE terms (
    id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         TEXT         NOT NULL,
    version      VARCHAR(20)  NOT NULL,
    required     BOOLEAN      NOT NULL,
    effective_at TIMESTAMPTZ  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_terms_code_version UNIQUE (code, version)
);
