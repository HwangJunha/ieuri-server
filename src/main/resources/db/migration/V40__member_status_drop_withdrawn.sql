-- SPEC-0012 R12. 체크 제약을 도메인 MemberStatus(ACTIVE/SUSPENDED)와 일치시킨다.
-- 탈퇴는 hard delete라 'WITHDRAWN' 상태로 남는 행이 없고, 어떤 코드도 이 값을 쓰지 않는다.
ALTER TABLE member DROP CONSTRAINT ck_member_status;
ALTER TABLE member ADD CONSTRAINT ck_member_status
    CHECK (status IN ('ACTIVE', 'SUSPENDED'));
