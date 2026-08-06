-- 사용자가 개별 글을 숨긴 기록(SPEC-0010 추가 요구).
--
-- **community_post.hidden과 다른 개념이다.** 그쪽은 신고가 5건 쌓여 서버가 **모두에게** 감춘
-- 것이고, 여기는 한 사용자가 **자기 화면에서만** 감춘 것이다. 둘을 한 컬럼으로 합칠 수 없다 —
-- 전자는 글의 속성이고 후자는 (글, 사용자) 쌍의 속성이라 카디널리티부터 다르다.
--
-- 개별 콘텐츠를 감출 수단이 없으면 마음에 안 드는 글 하나 때문에 작성자를 통째로 차단해야
-- 한다. 그건 과한 조치라 유저가 쓰지 않고, 스토어 심사에서도 콘텐츠 단위 통제 수단을 본다
-- (Apple 1.2 / Google UGC 정책).
--
-- 차단(community_block)과 구조가 같지만 대상이 사람이 아니라 글이다. 둘 다 필요하다 —
-- "이 사람 글을 다 안 볼래"와 "이 글 하나만 안 볼래"는 다른 요구다.
CREATE TABLE community_post_hide (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  BIGINT      NOT NULL,
    post_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_post_hide_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    -- 같은 글을 두 번 숨겨도 한 번이다. 멱등 판정을 이 제약이 맡는다.
    CONSTRAINT uq_community_post_hide UNIQUE (member_id, post_id)
);

-- 피드 필터가 "이 회원이 숨긴 글 id"를 읽는 경로. 유일 제약의 인덱스가 (member_id, post_id)
-- 순서라 이 조회를 그대로 커버하므로 별도 인덱스를 두지 않는다.
--
-- 숨긴 글 목록(posts(hiddenByViewer: true))은 최신순이라 created_at이 정렬 키다.
CREATE INDEX idx_community_post_hide_member
    ON community_post_hide (member_id, created_at DESC, post_id DESC);
