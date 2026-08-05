-- community 도메인: 글 애그리게이트 루트(SPEC-0010 R1~R4).
--
-- **글 자체는 무엇에도 매여 있지 않다.** 글이 spot에 붙었는지 카테고리에 붙었는지는
-- 연결 테이블(community_post_spot·community_post_chip)이 들고 있다.
--
-- 다형 참조(target_type, target_id)를 쓰지 않는 이유는 그것이 "종류마다 의미가 달라지는
-- 컬럼"이라 읽는 쪽에서 매번 분기해야 하고, 종류별 제약을 DB가 강제하지 못하기 때문이다.
-- 연결 테이블로 나누면 각자 자기 규칙(개수 상한·유일성)을 자기 테이블에서 지킨다.
--
-- author_id는 auth의 member.id를 가리키지만 물리 FK를 두지 않는다(V11의 선례) —
-- 도메인 간 FK 금지 규칙이며, 커뮤니티가 별도 애플리케이션으로 분리될 때 따라가야 한다.
--
-- like_count·comment_count는 파생값이지만 컬럼으로 적재한다. 매 조회마다 세면 피드 한 페이지에
-- 카운트 쿼리가 20번 붙는다. 갱신은 좋아요·댓글 커맨드가 같은 트랜잭션에서 한다.
--
-- hidden은 신고 누적(서로 다른 신고자 5명)의 결과로 갱신되는 저장값이다(R20).
-- 조회 시점에 신고 수를 세지 않는다 — 피드 필터가 인덱스를 타야 하기 때문이다.
CREATE TABLE community_post (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    author_id     BIGINT       NOT NULL,
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
    -- 공백만 남은 제목·본문이 우회로 들어오지 않게 한다. 길이가 아니라 "비어 있지 않음"만 본다.
    CONSTRAINT ck_community_post_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_community_post_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_community_post_like_count    CHECK (like_count >= 0),
    CONSTRAINT ck_community_post_comment_count CHECK (comment_count >= 0)
);

-- 전체 피드의 정렬 키. 대상으로 좁히는 조회는 연결 테이블이 글 id를 먼저 걸러 주므로
-- 여기서는 정렬만 담당한다.
CREATE INDEX idx_community_post_feed
    ON community_post (created_at DESC, id DESC);

-- 작성자 기준 조회(차단 필터의 반대편, 탈퇴 시 정리).
CREATE INDEX idx_community_post_author ON community_post (author_id);
