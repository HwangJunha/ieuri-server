-- 약관 동의 이력. append-only: 동의/비동의 모두 기록하고, 재동의 시 새 로우를 추가한다.
-- terms_code/terms_version를 함께 비정규화 저장해 이력이 약관 로우 변경과 무관하게 안정적으로 남는다.
CREATE TABLE member_terms_agreement (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id     BIGINT      NOT NULL,
    terms_id      BIGINT      NOT NULL,
    terms_code    VARCHAR(50) NOT NULL,
    terms_version VARCHAR(20) NOT NULL,
    agreed        BOOLEAN     NOT NULL,
    agreed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_mta_member FOREIGN KEY (member_id) REFERENCES member (id),
    CONSTRAINT fk_mta_terms  FOREIGN KEY (terms_id)  REFERENCES terms (id)
);

CREATE INDEX idx_mta_member ON member_terms_agreement (member_id);
