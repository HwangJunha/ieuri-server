-- 반려동물 동반 가능 spot(SPEC-0015 추천 항목 확장).
--
-- **국문 데이터만 있다.** `detailPetTour2`는 KorService2에만 존재하고 EN/JA/ZH 서비스에는
-- 오퍼레이션 자체가 없다(실측 2026-08-11: NO_OPENAPI_SERVICE_ERROR). 언어별 `content_id`가
-- 서로 겹치지도 않아(교집합 0) 외국어 spot에 직접 이어붙일 수 없다.
--
-- **그래서 spot 테이블에 컬럼으로 넣지 않고 별도 테이블로 둔다.** 추후 spot을 언어 통합할
-- 계획이 있어, 이 테이블이 `content_id`만 참조하면 통합 시 손댈 필요가 없다. 결합도를 낮게
-- 유지하는 것이 목적이다.
--
-- 추천에서는 **지역별 개수 집계**로 쓴다 — 개수는 언어와 무관한 사실이라 4개 언어 사용자
-- 모두에게 같은 값이 나간다.

CREATE TABLE spot_pet_friendly
(
    -- TourAPI content_id. 국문 spot의 자연키이며 여기서도 그대로 쓴다.
    -- **spot에 FK를 걸지 않는다** — 반려동물 목록에만 있고 우리가 아직 적재하지 않은
    -- content_id가 올 수 있고(실측 40건 중 3건), 그때 배치가 통째로 실패하면 안 된다.
    content_id       varchar(32) PRIMARY KEY,

    -- 동반 가능 범위. '전구역 동반가능' · '일부구역 동반가능' 등 자유 문자열이다.
    -- CHECK을 걸지 않는다 — 업스트림이 값을 늘리면 적재가 막힌다(V44 visitor_type과 같은 판단).
    accompany_type   varchar(64),

    -- 동반 가능 반려동물 범위('전 견종 동반 가능' 등).
    accompany_scope  varchar(255),

    -- 준비물·주의사항. 줄바꿈이 섞인 긴 문자열이라 text다.
    required_matters text,
    etc_info         text,

    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE spot_pet_friendly IS
    '반려동물 동반 가능 관광지(국문 전용). spot과 content_id로 느슨하게 연결한다.';
