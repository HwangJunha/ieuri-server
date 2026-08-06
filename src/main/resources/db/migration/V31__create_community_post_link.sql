-- community 도메인: 글이 무엇에 붙었는지를 담는 연결 테이블 둘.
--
-- 앞서 (target_type, target_id) 다형 참조를 썼으나 걷어냈다. 종류마다 의미가 달라지는
-- 컬럼은 읽는 쪽에서 매번 분기해야 하고, 종류별 제약(개수 상한·유일성)을 DB가 강제하지
-- 못한다. 연결 테이블로 나누면 각자 자기 규칙을 자기 테이블에서 지킨다.
--
-- **둘 다 선택이다.** 글은 카테고리만 붙을 수도, spot까지 붙을 수도 있다 —
-- "이 글을 장소와 연결하시겠습니까?"가 선택지로 남는다.

-- 1) 글 ↔ spot. **글 하나에 spot 하나**이며, 없을 수도 있다.
--    관광지에 붙지 않는 글(예: 여행 일반 질문)이 존재할 수 있어야 한다.
CREATE TABLE community_post_spot (
    post_id BIGINT      PRIMARY KEY,
    -- tourism의 spot.id. 도메인 간이라 물리 FK를 두지 않는다.
    -- spot이 사라져도 글은 남으며, 조회 시 요약이 없으면 칩을 그리지 않는다.
    spot_id BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- PK가 post_id라 글당 spot은 최대 하나다. 여러 장소를 붙이는 것은 비범위다.
    CONSTRAINT fk_community_post_spot_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
);

-- spot 상세 화면의 피드: WHERE spot_id = ? → post_id로 조인.
CREATE INDEX idx_community_post_spot_spot ON community_post_spot (spot_id, post_id);

-- 2) 글 ↔ 카테고리(칩). **글 하나에 카테고리 하나**이며 필수에 가깝지만,
--    스키마로는 없을 수 있게 둔다 — 카테고리 없이 쓴 옛 글이 생길 여지를 남긴다.
--
--    카테고리를 spot에서 유도하지 않고 여기 저장하는 이유는 **spot이 선택이기 때문**이다.
--    유도하면 spot 없는 글은 카테고리가 없어 상단 칩을 눌렀을 때 전부 사라진다.
CREATE TABLE community_post_chip (
    post_id  BIGINT      PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_post_chip_post
        FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE,
    CONSTRAINT ck_community_post_chip_category
        CHECK (category IN ('TOURIST_SPOT', 'CULTURAL_FACILITY', 'FESTIVAL_EVENT',
                            'TRAVEL_COURSE', 'LEISURE_SPORTS', 'LODGING',
                            'SHOPPING', 'RESTAURANT', 'TRANSPORT'))
);

-- 상단 칩 필터: WHERE category = ? → post_id로 조인.
CREATE INDEX idx_community_post_chip_category ON community_post_chip (category, post_id);
