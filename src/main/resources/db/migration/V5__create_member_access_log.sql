-- 접속기록(「개인정보의 안전성 확보조치 기준」 제8조).
--
-- **V3의 주석 정정.** V3는 위치 기록의 보관 기간을 "신고 전이라 아직 확정할 근거가 없다"고
-- 적었으나, 위치정보법 제16조 제2항이 6개월로 정하고 있고 약관도 그렇게 고지한다. 파기는
-- auditRetentionJob이 집행한다(AuditRetention.LOCATION_ACCESS).
-- V3 파일 자체는 고치지 않는다 — 이미 적용된 마이그레이션의 본문을 바꾸면 체크섬이 달라져
-- Flyway validate가 부팅을 막는다.
--
-- 기준 제8조 제1항이 개인정보처리시스템에 대한 접속기록을 요구하며, 남겨야 하는 항목은
-- **계정 · 접속일시 · 접속지 정보 · 수행업무**다. member_audit_log가 "무엇을 했는가"를
-- 남기지만 **접속지 정보(IP)가 없어** 기준을 충족하지 못했고, 개인정보처리방침이 고지한
-- 접속기록 보관이 집행되지 않고 있었다.
--
-- 보관 기간은 **1년**이다(기준 제8조 제2항). 방침이 통신비밀보호법의 3개월을 근거로 들고
-- 있었으나, 접속기록의 근거는 이쪽이고 기간이 더 길다 — 짧은 쪽을 고지하면 실제 보관이
-- 고지를 넘는다.
--
-- member_audit_log와 나누는 이유는 member_location_access_log를 나눈 이유와 같다:
--   1. 보관 기간이 다르다(감사 1년 · 접속기록 1년이나 근거 법령이 다르다 — 기준이 바뀔 때
--      따로 움직여야 한다).
--   2. 쓰기 빈도가 다르다 — 인증된 모든 요청마다 쌓인다.
--
-- member_id에 FK를 두지 않는다 — 다른 두 로그와 같은 취급이다. 탈퇴해도 접속기록은 남아야
-- 하므로(기준이 요구하는 보관 기간이 계정 수명과 무관하다) FK가 오히려 방해가 된다.
CREATE TABLE auth.member_access_log (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id bigint NOT NULL,
    -- 수행업무. GraphQL 루트 필드명이 들어간다("무엇을 했는가").
    operation varchar(200) NOT NULL,
    -- 접속지 정보. **IPv6를 담을 수 있어야 한다**(45자가 IPv4-mapped IPv6의 최대 길이다).
    -- 프록시가 주지 않아 알 수 없는 경우가 있으므로 NULL을 허용한다 — 알 수 없는 것을
    -- 0.0.0.0 같은 값으로 채우면 기록이 거짓이 된다.
    ip_address varchar(45),
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- 보관 기간 정리 배치가 생성 시각으로 지운다 — 인덱스가 없으면 전체 스캔이 된다.
CREATE INDEX idx_access_log_created_at
    ON auth.member_access_log USING btree (created_at);

-- 회원별 조회(정보주체의 열람 요구 대응, 침해사고 조사).
CREATE INDEX idx_access_log_member
    ON auth.member_access_log USING btree (member_id);
