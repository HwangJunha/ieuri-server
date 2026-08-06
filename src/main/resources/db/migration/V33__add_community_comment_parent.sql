-- 대댓글(SPEC-0010 추가 요구). **깊이는 2단계까지다.**
--
-- 컬럼 하나로 표현한다: parent_comment_id가 null이면 최상위 댓글, 있으면 대댓글이다.
-- 별도 테이블로 나누지 않는 이유는 본문·작성자·숨김·삭제 규칙이 최상위와 완전히 같아서다 —
-- 나누면 같은 로직이 두 벌이 된다.
--
-- **깊이 제한은 DB가 강제하지 못한다.** "부모가 이미 대댓글이면 안 된다"는 부모 행을 읽어야
-- 알 수 있어 CHECK로 표현되지 않는다. 도메인이 판정하며, 대댓글에 답글을 달면 거부하는 대신
-- **그 부모의 부모(최상위)에 붙인다** — 사용자에게는 답글이 정상적으로 달린 것으로 보이고,
-- 트리는 2단계로 유지된다.
--
-- 자기 참조 FK라 부모가 지워지면 자식도 함께 지워진다. 다만 삭제는 소프트 삭제(deleted_at)이므로
-- 실제로 이 CASCADE가 도는 것은 글이 물리 삭제될 때뿐이다 — 부모 댓글을 지워도 대댓글은 남는다.
ALTER TABLE community_comment
    ADD COLUMN parent_comment_id BIGINT;

ALTER TABLE community_comment
    ADD CONSTRAINT fk_community_comment_parent
        FOREIGN KEY (parent_comment_id) REFERENCES community_comment (id) ON DELETE CASCADE;

-- 자기 자신을 부모로 삼는 것만은 DB가 막을 수 있다. 깊이는 못 막아도 이건 막는다.
ALTER TABLE community_comment
    ADD CONSTRAINT ck_community_comment_parent_not_self
        CHECK (parent_comment_id IS NULL OR parent_comment_id <> id);

-- 한 최상위 댓글의 대댓글 목록. 대댓글은 **오래된 순**으로 읽는다 — 대화의 흐름이라
-- 최신순으로 뒤집으면 문맥이 끊긴다(최상위 목록은 여전히 최신순이다).
CREATE INDEX idx_community_comment_parent
    ON community_comment (parent_comment_id, created_at, id)
    WHERE parent_comment_id IS NOT NULL;
