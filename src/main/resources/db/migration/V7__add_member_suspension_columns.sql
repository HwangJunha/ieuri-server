-- 정지(suspension) 메타데이터 컬럼 추가. 탈퇴는 행 삭제(hard delete)라 별도 컬럼이 필요 없다.
ALTER TABLE member ADD COLUMN suspended_at      TIMESTAMPTZ;
ALTER TABLE member ADD COLUMN suspended_until   TIMESTAMPTZ;
ALTER TABLE member ADD COLUMN suspension_reason VARCHAR(500);

-- 탈퇴를 행 삭제로 처리하므로 WITHDRAWN 상태값은 사용하지 않는다. status를 ACTIVE/SUSPENDED로 조인다.
-- (V1은 immutable이므로 제약 변경은 여기서 DROP 후 재생성)
ALTER TABLE member DROP CONSTRAINT ck_member_status;
ALTER TABLE member ADD CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'SUSPENDED'));
