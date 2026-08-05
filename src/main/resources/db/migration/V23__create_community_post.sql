-- community 도메인: 글 애그리게이트 루트(SPEC-0010 R1~R4).
--
-- **글이 붙는 대상은 관광지에 한정되지 않는다.** `community`는 특정 도메인의 부속물이 아니라
-- 커뮤니티 플랫폼이며, 지금은 spot에만 붙지만 앞으로 상품·경매 등 다른 도메인의 대상에도
-- 붙는다. 그래서 `spot_id`가 아니라 (target_type, target_id) 다형 참조다.
-- 새 대상 종류가 생겨도 enum 값과 조회 어댑터만 늘고 스키마는 그대로다.
--
-- target_id에 물리 FK를 걸 수 없다 — 가리키는 테이블이 행마다 다르고, 어느 쪽이든 다른
-- 도메인이라 도메인 간 FK 금지 규칙에도 걸린다(V11의 member_id 선례). 대상 존재 확인은
-- 애플리케이션이 글 작성 시점에 한다. 대상이 삭제돼도 글은 자동으로 정리되지 않는다.
--
-- author_id도 같은 이유로 plain 컬럼이다(auth의 member.id).
--
-- like_count·comment_count는 파생값이지만 컬럼으로 적재한다. 매 조회마다 세면 피드 한 페이지에
-- 카운트 쿼리가 20번 붙는다. 갱신은 좋아요·댓글 커맨드가 같은 트랜잭션에서 한다.
--
-- hidden은 신고 누적(서로 다른 신고자 5명)의 결과로 갱신되는 저장값이다(R20).
-- 조회 시점에 신고 수를 세지 않는다 — 피드 필터가 인덱스를 타야 하기 때문이다.
CREATE TABLE community_post (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 글이 붙는 대상의 종류. 지금은 SPOT뿐이고 값이 늘어난다.
    target_type   VARCHAR(20)  NOT NULL,
    -- 그 종류 안에서의 식별자. 종류가 다르면 같은 숫자라도 다른 대상이다.
    target_id     BIGINT       NOT NULL,
    author_id     BIGINT       NOT NULL,
    topic         VARCHAR(20)  NOT NULL,
    -- 카드에서 볼드로 표시되는 제목. 길이 상한(50자)은 도메인이 소유하고 컬럼은 넉넉히 잡는다 —
    -- body와 같은 이유로 이모지의 코드포인트 수가 층마다 달라지는 것을 막는다.
    title         VARCHAR(100) NOT NULL,
    -- **TEXT이며 길이 상한을 DB에 두지 않는다.** 본문 500자 판정은 도메인이 소유한다.
    -- 이모지가 섞이면 두 층의 "글자" 개념이 어긋나기 때문이다 — Postgres의 length()는
    -- 코드포인트를 세므로 👨‍👩‍👧‍👦(ZWJ 결합) 하나가 7자로 잡힌다. VARCHAR(500)을 두면
    -- 도메인을 통과한 본문이 DB에서 거부되는 일이 생기고, 그건 사용자 오류가 아니라 500이 된다.
    -- 인코딩은 UTF8이라 이모지는 그대로 저장된다(별도 설정 불요 — utf8mb4는 MySQL 개념이다).
    body          TEXT         NOT NULL,
    like_count    INTEGER      NOT NULL DEFAULT 0,
    -- 숨겨진 댓글은 제외한 수(R23). 목록과 숫자가 어긋나면 유저는 버그로 판단한다.
    comment_count INTEGER      NOT NULL DEFAULT 0,
    hidden        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 수정된 적이 있는지. 클라이언트의 "수정됨" 표시에 쓰며 이력은 보관하지 않는다.
    -- 숨겨진 글은 수정할 수 없다(도메인이 판정) — 신고 기록이 대상 글을 가리키기 때문이다.
    edited        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- 삭제는 소프트 삭제다. 신고 대상이 사라질 때 신고 레코드가 고아가 되지 않도록
    -- 행 자체는 남긴다.
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_post_target_type CHECK (target_type IN ('SPOT')),
    CONSTRAINT ck_community_post_topic
        CHECK (topic IN ('QUESTION', 'TIP', 'REVIEW', 'COMPANION', 'PHOTO')),
    -- 공백만 남은 제목·본문이 우회로 들어오지 않게 한다. 길이가 아니라 "비어 있지 않음"만 본다.
    CONSTRAINT ck_community_post_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_community_post_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_community_post_like_count    CHECK (like_count >= 0),
    CONSTRAINT ck_community_post_comment_count CHECK (comment_count >= 0)
);

-- 대상별 피드(R6): WHERE target_type = ? AND target_id = ? AND deleted_at IS NULL
--                 ORDER BY created_at DESC, id DESC
-- 정렬 키와 타이브레이크까지 인덱스에 담아 keyset 페이징이 인덱스만으로 전진한다(V21과 같은 형태).
-- target_type이 선행이라 종류가 늘어도 서로의 조회를 방해하지 않는다.
CREATE INDEX idx_community_post_target_feed
    ON community_post (target_type, target_id, created_at DESC, id DESC);

-- 주제 필터가 붙는 경로(R8). 지역 피드는 대상 id 목록을 IN으로 받으므로 위 인덱스를 탄다.
CREATE INDEX idx_community_post_topic_feed
    ON community_post (topic, created_at DESC, id DESC);

-- 작성자 기준 조회(차단 필터의 반대편, 탈퇴 시 정리).
CREATE INDEX idx_community_post_author ON community_post (author_id);
