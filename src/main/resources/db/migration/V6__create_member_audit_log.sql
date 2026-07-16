-- 감사 이력 겸 outbox. 지금은 ApplicationEvent로 후처리하고,
-- 추후 이 테이블을 outbox로 활용해 릴레이가 Kafka로 발행할 수 있다.
CREATE TABLE member_audit_log (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  BIGINT      NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_member ON member_audit_log (member_id);
