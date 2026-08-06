-- community 도메인: 글 좋아요(SPEC-0010 R12).
--
-- tourism의 spot_reaction을 재사용하지 않는다 — 그쪽은 spot에 대한 LIKE/DISLIKE 양방향
-- 취향 기록이고 이쪽은 글에 대한 단방향 좋아요다. 대상도 의미도 다르다(계약 §6).
--
-- 멱등성(R12-2·R12-4)의 근거가 UNIQUE (post_id, member_id)다. 이미 좋아요한 글에
-- 다시 눌러도 오류가 아니라 no-op이며, 중복 집계되지 않음을 DB가 보장한다.
CREATE TABLE community_post_like (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT      NOT NULL,
    member_id  BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_post_like_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT uq_community_post_like UNIQUE (post_id, member_id)
);

-- 피드에서 "내가 좋아요했는가"(likedByViewer)를 회원 기준으로 조회하는 경로.
-- uq_community_post_like는 post_id 선행이라 이 조회를 타지 못한다.
CREATE INDEX idx_community_post_like_member ON community_post_like (member_id, post_id);
