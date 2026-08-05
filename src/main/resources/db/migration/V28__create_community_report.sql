-- community 도메인: 신고(SPEC-0010 R16, R20, R22).
--
-- 대상은 글·댓글·사용자 세 종류이며, target_id의 의미가 target_type에 따라 달라진다.
-- 종류별 테이블로 쪼개지 않는 이유는 "같은 대상을 두 번 신고할 수 없다"(R16-2)와
-- "서로 다른 신고자 수"(R20) 판정이 세 종류에서 완전히 같은 형태이기 때문이다.
--
-- target_id에 FK를 걸 수 없다 — 가리키는 테이블이 행마다 다르고, MEMBER는 아예
-- 다른 도메인이다. 대상 존재 여부는 애플리케이션이 신고 시점에 확인한다(R16-4).
--
-- **자동 숨김은 POST·COMMENT에만 적용된다**(R22). MEMBER 신고는 기록만 하고 어떤 자동
-- 조치도 하지 않는다 — 신고 몇 건으로 한 사람의 글이 일괄로 사라지면 조직적 신고가 곧
-- 침묵 수단이 된다. 이 테이블은 나중에 만들 검토 도구의 입력이다.
CREATE TABLE community_report (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id BIGINT      NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id   BIGINT      NOT NULL,
    reason      VARCHAR(30) NOT NULL,
    -- 자유 서술. reason이 OTHER면 필수다(R16-3). **로그·에러 메시지에 싣지 않는다**(POL-013).
    -- 길이 판정은 도메인이 소유한다(community_post.body와 같은 이유).
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 중복 신고 거부(R16-2)이자 "서로 다른 신고자 5명"(R20) 집계의 근거.
    -- 같은 사람이 5번 신고해도 신고자 수는 1이다(R20-5).
    CONSTRAINT uq_community_report UNIQUE (reporter_id, target_type, target_id),
    CONSTRAINT ck_community_report_target_type
        CHECK (target_type IN ('POST', 'COMMENT', 'MEMBER')),
    CONSTRAINT ck_community_report_reason
        CHECK (reason IN ('SPAM', 'ABUSE', 'INAPPROPRIATE_CONTENT', 'OTHER')),
    -- OTHER면 상세 사유가 있어야 한다(R16-3). 애플리케이션의 REPORT_DETAIL_REQUIRED와 이중 방어.
    CONSTRAINT ck_community_report_detail_for_other
        CHECK (reason <> 'OTHER' OR btrim(coalesce(detail, '')) <> '')
);

-- 임계값 판정: SELECT count(*) FROM community_report WHERE target_type = ? AND target_id = ?.
-- 신고 기록 직후 매번 도는 경로다.
CREATE INDEX idx_community_report_target ON community_report (target_type, target_id);
