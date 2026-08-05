-- community 도메인: 사용자 차단(SPEC-0010 R15).
--
-- **단방향 레코드로 저장하고 조회 시점에 양방향으로 해석한다.** A가 B를 차단하면
-- (blocker_id=A, blocked_id=B) 한 건만 쓰고, 피드 필터가 "내가 차단했거나 나를 차단한"
-- 양쪽을 모두 제외한다(R14-2).
--
-- 양방향 레코드 두 건을 쓰지 않는 이유는 해제 시 정합성이다. 두 건을 쓰면 A가 해제할 때
-- B가 건 차단까지 지워질 위험이 있고, "누가 먼저 차단했는가"라는 정보도 잃는다.
--
-- member(id) 참조이나 도메인 간 FK는 두지 않는다(V11 선례).
CREATE TABLE community_block (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    blocker_id BIGINT      NOT NULL,
    blocked_id BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 중복 차단은 오류가 아니라 멱등 no-op이다(R15-2). 목록에 중복 항목이 생기지 않음을
    -- DB가 보장한다.
    CONSTRAINT uq_community_block UNIQUE (blocker_id, blocked_id),
    -- 자기 차단 거부(R15-3). 도메인 모델이 먼저 막지만 DB에서도 불변식으로 못박는다.
    CONSTRAINT ck_community_block_not_self CHECK (blocker_id <> blocked_id)
);

-- 차단 목록(blockedUsers): WHERE blocker_id = ? ORDER BY created_at DESC, id DESC.
-- 정렬 키까지 담아 keyset 페이징이 인덱스만으로 전진한다.
CREATE INDEX idx_community_block_blocker
    ON community_block (blocker_id, created_at DESC, id DESC);

-- 양방향 해석의 반대편. "나를 차단한 사람"을 찾는 경로이며, 위 인덱스는
-- blocker_id 선행이라 이 조회를 타지 못한다. 피드 필터가 매 요청 두 방향을 모두 조회한다.
CREATE INDEX idx_community_block_blocked ON community_block (blocked_id, blocker_id);
