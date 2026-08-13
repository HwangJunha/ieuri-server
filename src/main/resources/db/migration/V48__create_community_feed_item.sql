-- community 도메인: 피드(SPEC-0017 R1~R6).
--
-- **Post와 별개 테이블이다.** 피드는 spot 정확히 1곳과 사진 1~5장을 요구하는데
-- community_post.spot은 선택이고 사진은 0장을 허용한다. 같은 테이블에 이 제약을 얹으면
-- 기존 글이 깨지거나, 제약이 "선택이지만 되도록"으로 형해화된다.
--
-- spot_id는 tourism 소유라 물리 FK가 없다(도메인 간 FK 금지). 좌표를 복사해 두지 않는 것은
-- 피드가 거리순 조회를 하지 않기 때문이다 — 최신순 고정이라 좌표로 거를 일이 없다.
CREATE TABLE community_feed_item (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id  BIGINT      NOT NULL,
    -- **NOT NULL이다.** 피드에서 spot 연결은 장식이 아니라 이 화면의 존재 이유다(R1).
    spot_id    BIGINT      NOT NULL,
    -- 길이 판정은 도메인이 소유한다 — 컬럼은 넉넉히 잡는다(이모지의 코드포인트 수 문제).
    body       TEXT        NOT NULL,
    like_count INTEGER     NOT NULL DEFAULT 0,
    -- 신고 누적 자동 숨김(POL-041 3항). 피드는 공개 콘텐츠라 이 규칙이 그대로 적용된다.
    hidden     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_feed_item_like_count CHECK (like_count >= 0)
);

-- 피드 조회의 정렬 키다. 최신순 고정이라 (created_at, id) 하나면 충분하다 —
-- 정렬 인자가 없으므로 다른 조합의 인덱스가 필요해지지 않는다(R4).
CREATE INDEX idx_community_feed_item_created
    ON community_feed_item (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

-- 차단 필터가 작성자로 거르고, 탈퇴 처리가 작성자로 지운다.
CREATE INDEX idx_community_feed_item_author
    ON community_feed_item (author_id);

-- 피드 사진. 1~5장이며 position이 표시 순서다.
--
-- **object_key가 전역 유일하다.** 이미 다른 피드에 실린 키를 다시 제출하면 거부해야 하는데
-- (AC3-3), 확정 키를 추적하는 저장소가 따로 없어 이 제약이 그 판정을 맡는다.
CREATE TABLE community_feed_item_photo (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feed_item_id BIGINT      NOT NULL,
    object_key   VARCHAR(512) NOT NULL,
    position     SMALLINT    NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_feed_item_photo_item
        FOREIGN KEY (feed_item_id) REFERENCES community_feed_item (id) ON DELETE CASCADE,
    -- 한 키는 한 피드에만 실린다. 재사용 판정을 이 제약이 맡는다(AC3-3).
    CONSTRAINT uq_community_feed_item_photo_key UNIQUE (object_key)
);

CREATE INDEX idx_community_feed_item_photo_item
    ON community_feed_item_photo (feed_item_id, position);

-- 피드 좋아요. 멱등 판정을 유일 제약이 맡는다(community_post_like와 같은 구조).
CREATE TABLE community_feed_item_like (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    feed_item_id BIGINT      NOT NULL,
    member_id    BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_feed_item_like_item
        FOREIGN KEY (feed_item_id) REFERENCES community_feed_item (id) ON DELETE CASCADE,
    CONSTRAINT uq_community_feed_item_like UNIQUE (feed_item_id, member_id)
);

-- 호출자의 좋아요 여부를 피드 한 페이지에 대해 한 번에 읽는 경로.
CREATE INDEX idx_community_feed_item_like_member
    ON community_feed_item_like (member_id, feed_item_id);
