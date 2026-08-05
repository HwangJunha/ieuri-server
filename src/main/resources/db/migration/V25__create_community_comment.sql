-- community 도메인: 댓글 애그리게이트(SPEC-0010 R13).
--
-- **부모 댓글 컬럼이 없다.** 1단계 댓글만이라는 결정을 구조로 고정한다(비범위: 대댓글).
-- 컬럼을 두고 null로 비워 두면 나중에 누군가 채우기 시작한다.
--
-- Post와 별도 애그리게이트다. 댓글 수가 늘어도 글을 통째로 적재할 이유가 없고,
-- 댓글 작성이 글의 버전을 건드릴 필요도 없다.
CREATE TABLE community_comment (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    BIGINT       NOT NULL,
    author_id  BIGINT       NOT NULL,
    -- TEXT인 이유는 community_post.body와 같다 — 길이 판정은 도메인이 소유한다.
    body       TEXT         NOT NULL,
    -- 신고 누적으로 숨겨졌는지(R20). 숨겨져도 작성자 본인에게는 보인다(R20-4).
    hidden     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_comment_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT ck_community_comment_body_not_blank CHECK (btrim(body) <> '')
);

-- 댓글 목록(R13-2): WHERE post_id = ? AND deleted_at IS NULL ORDER BY created_at DESC, id DESC.
-- FK 자식 인덱스 역할도 겸한다.
CREATE INDEX idx_community_comment_post
    ON community_comment (post_id, created_at DESC, id DESC);

CREATE INDEX idx_community_comment_author ON community_comment (author_id);
