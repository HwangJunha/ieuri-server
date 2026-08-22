-- tourism 도메인: 지도 관계(SPEC-0030 확장 · 관계 지도).
--
-- 공유 링크는 **단방향 스냅샷**이라 링크를 연 사람만 겹침을 보고 주인은 누가 열었는지도
-- 모른다. 그래서 "여러 명과 겹치는 곳을 본다"가 원리적으로 불가능했다. 관계를 저장해
-- 그것을 양방향으로 만든다.
--
-- **방향이 있는 두 행으로 둔다.** 등록 한 번에 (A→B)와 (B→A)를 함께 만들고, 삭제는 자기
-- 행만 지운다:
--
--   A가 B를 지움  →  (owner=A, counterpart=B)만 사라진다
--                     B의 목록에는 A가 그대로 남는다
--
-- 무방향 한 행(`least/greatest`)으로 두면 한쪽이 지울 때 상대 화면까지 사라진다 —
-- "내 화면에서 누굴 안 볼지는 내가 정한다"가 이 기능의 규칙이라 그렇게 둘 수 없다.
--
-- **겹친 칸 수를 저장하지 않는다.** 조회 시점에 다시 계산한다 — 서로 여행을 더 다니면
-- 겹침이 늘어야 자연스럽고, 그것이 다시 열어볼 이유가 된다. 등록 시점 스냅샷으로 굳히면
-- 숫자가 그대로라 기능이 한 번 쓰고 버려진다.
--
-- **member_id에 FK를 걸지 않는다**(컨텍스트 간 FK 금지). 회원은 auth 스키마의 소유이며,
-- 탈퇴 시 삭제는 애플리케이션이 한다 — `owner_id`·`counterpart_id` **양쪽 모두**를 지워야
-- 상대 목록에 남은 탈퇴자도 사라진다.

CREATE TABLE tour.map_relation (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 이 행을 자기 목록에서 보는 사람.
    owner_id       bigint      NOT NULL,
    -- 그 목록에 나타나는 상대.
    counterpart_id bigint      NOT NULL,

    created_at     timestamptz NOT NULL DEFAULT now(),

    -- 멱등성의 근거. 같은 상대를 두 번 등록해도 행이 늘지 않는다.
    CONSTRAINT uq_map_relation UNIQUE (owner_id, counterpart_id),

    -- 자기 자신과는 관계를 맺을 수 없다. 서비스가 먼저 막지만 최후 방어선을 둔다.
    CONSTRAINT ck_map_relation_not_self CHECK (owner_id <> counterpart_id)
);

-- 목록 조회는 `owner_id`로 찾아 `created_at` 내림차순으로 읽는다(정렬은 계약이다).
-- **동률 키가 `counterpart_id`라 인덱스도 그 컬럼이어야 한다** — `id`로 두면 같은 시각의
-- 커서 조건을 인덱스가 풀지 못하고 걸러내기로 떨어진다.
CREATE INDEX idx_map_relation_owner_created
    ON tour.map_relation (owner_id, created_at DESC, counterpart_id DESC);

-- 탈퇴 정리가 상대 방향도 지워야 하므로 역방향 조회가 필요하다.
CREATE INDEX idx_map_relation_counterpart ON tour.map_relation (counterpart_id);
