-- SPEC-0032 R19~R22: 위치 조회 감사 기록(POL-057).
--
-- **좌표를 어떤 형태로도 담지 않는다.** 위도·경도는 물론 시군구·행정구역 어느 단위로도
-- 남기지 않는다 — 위치의 *내용*이 빠지므로 이동 경로가 재구성되지 않고, 그것이 POL-045와
-- 양립하는 유일한 근거다. 컬럼 형태가 막아 주지 않으므로 테스트가 막는다.
--
-- member_audit_log와 나눈 이유는 둘이다:
--   1. 보관 기간이 다르다. 회원 감사 로그는 1년(POL-014)이고 위치 기록은 위치정보법이 정하는
--      기간을 따라야 하는데 신고 전이라 아직 확정할 근거가 없다.
--   2. 쓰기 빈도가 한 자릿수 이상 차이 난다 — 지도를 움직일 때마다 쌓인다.
--
-- member_id에 FK를 두지 않는다 — member_audit_log와 같은 취급이다. 탈퇴해도 기록이 남아야
-- 하므로 FK가 오히려 방해가 된다.
CREATE TABLE auth.member_location_access_log (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id bigint NOT NULL,
    -- 연산명 등 맥락. 항목이 늘 때 마이그레이션 없이 확장한다(member_audit_log.payload와 같다).
    -- **확장 대상은 연산의 맥락이지 좌표가 아니다.**
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

-- 보관 기간 정리 배치가 생성 시각으로 지우므로 인덱스가 없으면 전체 스캔이 된다(R22).
CREATE INDEX idx_location_access_created_at
    ON auth.member_location_access_log USING btree (created_at);

-- 회원별 조회(정보주체의 열람 요구 대응).
CREATE INDEX idx_location_access_member
    ON auth.member_location_access_log USING btree (member_id);

-- R22: member_audit_log의 보관 정리도 생성 시각으로 지운다. POL-014가 1년을 정했으나 삭제
-- 경로가 아예 없어 정책이 집행되지 않고 있었다 — 인덱스가 없으면 그 정리가 전체 스캔이 된다.
CREATE INDEX idx_audit_created_at
    ON auth.member_audit_log USING btree (created_at);
