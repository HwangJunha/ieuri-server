-- member_terms_agreement를 **이력 테이블에서 현재 상태 테이블로** 바꾼다(SPEC-0011).
--
-- 원래 이 테이블은 append-only 이력이었다. 그런데 회원 삭제 시 FK 자식이라 함께 지워져
-- (MemberPersistenceAdapter.deleteMember), **철회·탈퇴라는 가장 중요한 증빙이 사라졌다.**
-- 이력을 법적 증빙이라 부르면서 정작 그 사건만 남지 않는 모순이었다.
--
-- 그래서 역할을 나눈다:
--   - member_audit_log      → 이력(append-only). FK가 없어 탈퇴 후에도 남는다. 증빙은 이쪽이다.
--   - member_terms_agreement → 현재 상태. (member_id, terms_id)당 한 행이며 재동의는 갱신이다.
--
-- 상태 테이블이 되었으므로 유일 제약을 건다. 제약이 없으면 같은 약관에 여러 행이 쌓여
-- "현재 동의 상태"가 정렬 순서에 의존하게 된다 — 조회가 조용히 틀릴 수 있는 구조다.

-- 1) 같은 (member_id, terms_id)에 여러 행이 있으면 **가장 최근 것만 남긴다.**
--    이력은 이미 audit 로그로 옮겼으므로 여기서 지워도 증빙이 사라지지 않는다.
DELETE FROM member_terms_agreement a
WHERE EXISTS (
    SELECT 1 FROM member_terms_agreement b
    WHERE b.member_id = a.member_id
      AND b.terms_id  = a.terms_id
      AND (b.agreed_at, b.id) > (a.agreed_at, a.id)
);

-- 2) 회원당 약관 하나에 한 행. upsert(ON CONFLICT)의 판정 근거이기도 하다.
ALTER TABLE member_terms_agreement
    ADD CONSTRAINT uq_mta_member_terms UNIQUE (member_id, terms_id);
