-- tourism 도메인: 관광지 목록 자체 적재(SPEC-0004 R-1).
--
-- **SPEC-0002의 "관광 데이터는 적재하지 않는다"를 번복한다.** areaBasedSyncList2 기반 일 1회 배치가
-- 4개 언어 전량을 이 테이블에 upsert하고, spots 조회는 TourAPI를 거치지 않고 여기서만 응답한다(R-8).
--
-- PK는 surrogate id(BIGSERIAL) — spot_reaction(V11)과 동일한 관례. 자연키는 UNIQUE로 강제한다.
-- UNIQUE (content_id, language) = upsert 단위(AC-2).
--   - content_id: TourAPI contentId. **같은 장소라도 언어별로 다르다** — 그래서 단독으로는 장소를
--     식별하지 못하고 language가 키에 함께 들어간다(R-1 근거, V11이 이미 쓰는 패턴).
--   - language:   적재된 언어(EN/KO/JA/ZH).
--
-- 공간 인덱스는 이 마이그레이션에 없다. PostGIS가 운영 인스턴스에서 사용 불가한 것이 실측으로
-- 확인되어(pg_available_extensions에 postgis 없음, 역할이 비-슈퍼유저) 반경 조회 방식이 [AMEND] 회신
-- 대기 중이다. map_x/map_y는 아래에 이미 있으므로, 확정되면 인덱스만 별도 마이그레이션으로 추가한다.
CREATE TABLE spot (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_id          VARCHAR(32)  NOT NULL,
    language            VARCHAR(2)   NOT NULL,
    title               VARCHAR(255) NOT NULL,
    -- addr1/addr2 병합 주소. 둘 다 비면 null(원본 그대로, 보정하지 않음).
    address             VARCHAR(500),
    -- https로 정규화해 적재한다(AC-17). 대표이미지 없는 항목은 아예 적재하지 않으므로 NOT NULL —
    -- 이것이 Spot.thumbnailImageUrl: String! 을 쓰기 시점에 보장한다(런타임 계약 위험 1번).
    thumbnail_image_url VARCHAR(500) NOT NULL,
    -- 경도(mapX)·위도(mapY). 조회 응답 필드이자 반경 필터·거리 정렬의 입력.
    map_x               DOUBLE PRECISION NOT NULL,
    map_y               DOUBLE PRECISION NOT NULL,
    -- 해석된 도메인 카테고리.
    category            VARCHAR(30)  NOT NULL,
    -- 해석 이전의 TourAPI 원본 contentTypeId. **적재 전용이며 응답·로그로 나가서는 안 된다**(SPEC-0002 R8).
    -- 두는 이유는 두 가지다(설계 문서 §2.3): (1) category 해석이 실패하면 조용히 TOURIST_SPOT으로
    -- 떨어지는데, 원본이 남아 있어야 "몇 건이 폴백이었나"를 사후 질의할 수 있다(런타임 계약 위험 4번).
    -- (2) 코드 맵 설정을 고쳤을 때 다음 배치를 기다리지 않고 재해석 마이그레이션으로 교정할 수 있다.
    content_type_id     INTEGER,
    copyright           VARCHAR(20)  NOT NULL,
    -- 이번 실행이 이 행을 건드렸는지. 사라진 항목 정리(R-5)의 기준이 된다. 정리 방식([TBD-1])이
    -- 미결이지만 세 선택지 어느 쪽도 이 컬럼을 요구하거나 최소한 무해하고, 나중에 추가하면 전 행
    -- 백필이 필요하므로 지금 넣어 둔다(설계 문서 §2.5).
    last_synced_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot UNIQUE (content_id, language),
    CONSTRAINT ck_spot_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    -- 좌표 유효 범위. 도메인 Coordinates가 강제하는 불변식을 DB에서도 지켜, 배치가 이상 좌표를
    -- 적재하면 조회 시점이 아니라 쓰기 시점에 드러나게 한다.
    CONSTRAINT ck_spot_map_x CHECK (map_x BETWEEN -180 AND 180),
    CONSTRAINT ck_spot_map_y CHECK (map_y BETWEEN -90 AND 90),
    -- 대표이미지 보장이 빈 문자열로 우회되지 않도록 한다(AC-15).
    CONSTRAINT ck_spot_thumbnail_not_blank CHECK (btrim(thumbnail_image_url) <> '')
);

-- 언어별 조회가 모든 spots 쿼리의 선행 조건이고, 카테고리 필터가 그 다음이다(AC-33).
-- 반경 조건은 확정 후 별도 인덱스로 추가한다.
CREATE INDEX idx_spot_language_category ON spot (language, category);
