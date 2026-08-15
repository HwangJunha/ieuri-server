-- tourism 도메인: 장소 단위 카탈로그(SPEC-0019).
--
-- **목적은 언어 간 격차 해소다.** TourAPI는 같은 장소를 언어마다 다른 contentId로 발급하는데,
-- 국문이 56,425행인 반면 영·일·중은 각각 21,000행 안팎이다. 그래서 일본어 사용자는 지금
-- 21,111개만 본다. 국문을 기준으로 번역해 채우면 56,425개를 볼 수 있다.
--
-- 역할을 이렇게 가른다:
--
--   spot                     원본 보관. TourAPI가 준 그대로, 언어별 행 유지. 조회하지 않는다
--   spot_catalog             장소 하나. **언어 무관 값만** 갖는다 (좌표·썸네일·분류·저작권)
--   spot_catalog_translation 언어별 표시값(제목·주소). **KO도 하나의 언어다**
--
-- **카탈로그에 이름이 없다는 점이 이 설계의 핵심이다.** 이름은 언어에 종속된 값이므로 번역
-- 테이블에만 있다. 그래서:
--   - KO 원본이 있는 장소  → KO 행 + (번역으로 채운) EN/JA/ZH 행
--   - 외국어에만 있는 장소 → 그 언어 행만. **한국어 이름을 지어내지 않는다**
-- 후자를 한국어로 억지 번역하면 정합성이 깨진다 — 원본에 없는 이름을 우리가 만드는 셈이다.
-- 그 장소는 해당 언어 사용자에게만 보이면 되고, 그것이 올바른 동작이다.
--
-- **조회는 spot을 보지 않는다.** 카탈로그와 번역만 본다 — 그래야 그 언어의 spot 행이 없어도
-- 번역만 있으면 목록에 나온다. 그것이 이 스펙의 목적이다.

-- ---------------------------------------------------------------------------
-- 1. spot_catalog — 장소 하나. 언어에 독립적이다.
--
-- 언어 무관 값만 둔다. 실측(2026-08-14) 근거:
--   copyright  언어별 불일치 0건
--   thumbnail  사실상 동일
--   category   88건만 불일치. 65%가 "외국어는 TOURIST_SPOT으로 뭉개고 KO가 구체적"
--
-- **채택은 KO 우선이다** — KO가 원본 언어이고 가장 많으며 번역 기준이다.
--
-- **좌표가 NOT NULL이다.** 좌표가 없거나 (0,0)이거나 한국 밖인 spot은 위치를 모르는 장소가
-- 아니라 **깨진 행이다**(실측 317건). 그런 행에 카탈로그를 내주면 지도·반경 조회에서 빠지는
-- "존재하지만 아무 데도 안 나오는" 데이터가 된다. 배치가 적재 단계에서 제외한다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 경도/위도. 도메인 Coordinates.longitude/latitude에 대응한다.
    map_x DOUBLE PRECISION NOT NULL,
    map_y DOUBLE PRECISION NOT NULL,

    -- PostGIS 지리 좌표. **map_x/map_y에서 파생되며 트리거가 유지한다** — 애플리케이션이
    -- 직접 쓰지 않는다. JPA는 이 컬럼을 매핑하지 않으므로(표현할 타입이 없다) 이 컬럼을
    -- 건드리는 질의는 전부 생 SQL 어댑터에 있다.
    --
    -- **ST_MakePoint는 (경도, 위도) 순서다.** 뒤집으면 전 국토가 남극 근처로 간다.
    location GEOGRAPHY(POINT, 4326) NOT NULL,

    thumbnail_image_url VARCHAR(500) NOT NULL,
    category VARCHAR(30) NOT NULL,
    copyright VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_spot_catalog_coordinates_in_korea
        CHECK (map_x BETWEEN 124 AND 132 AND map_y BETWEEN 33 AND 39)
);

-- location을 map_x/map_y에서 파생시킨다. 애플리케이션이 두 값만 쓰면 되게 한다.
CREATE OR REPLACE FUNCTION spot_catalog_sync_location() RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.map_x, NEW.map_y), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_spot_catalog_sync_location
    BEFORE INSERT OR UPDATE OF map_x, map_y ON spot_catalog
    FOR EACH ROW EXECUTE FUNCTION spot_catalog_sync_location();

CREATE INDEX idx_spot_catalog_location ON spot_catalog USING GIST (location);
CREATE INDEX idx_spot_catalog_category ON spot_catalog (category);

-- ---------------------------------------------------------------------------
-- 2. spot_catalog_translation — 언어별 표시값. **KO도 하나의 언어다.**
--
-- 대표 이름을 카탈로그가 아니라 여기에 두는 이유는 이름이 언어에 종속되기 때문이다.
-- KO를 특별 취급하면 "한국어 이름이 없는 장소"를 표현할 수 없고, 결국 없는 이름을
-- 만들어 넣게 된다.
--
-- **출처(OFFICIAL/MACHINE)를 기록하지 않는다.** 유일한 용도가 "TourAPI가 나중에 공식
-- 번역을 주면 우리 번역을 덮어쓸지" 판단인데, 그 시나리오가 실제로 오는지 확인된 바 없다.
-- 필요해지면 그때 컬럼을 추가한다.
--
-- 좌표 사본을 두는 이유는 검색(pg_search BM25)이 **평면 질의만 받기 때문이다** — JOIN이
-- 하나라도 끼면 인덱스 푸시다운이 깨진다. 그래서 "제목 검색 + 반경 필터"를 한 문장에
-- 담으려면 좌표가 이 테이블에 있어야 한다. 카탈로그 좌표가 NOT NULL이므로 사본도 NOT NULL이다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_translation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    catalog_id BIGINT NOT NULL,
    language VARCHAR(2) NOT NULL,

    title VARCHAR(255) NOT NULL,

    -- 원문에 주소가 없는 spot이 실재한다(KO 기준 1.5%).
    address VARCHAR(500),

    -- 카탈로그 좌표의 사본. 위 주석 참조 — 검색이 이 테이블 단독으로 돌아야 한다.
    map_x DOUBLE PRECISION NOT NULL,
    map_y DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_spot_catalog_translation UNIQUE (catalog_id, language),
    CONSTRAINT ck_spot_catalog_translation_language
        CHECK (language IN ('KO', 'EN', 'JA', 'ZH')),
    CONSTRAINT ck_spot_catalog_translation_title_not_blank
        CHECK (btrim(title) <> ''),
    CONSTRAINT fk_spot_catalog_translation_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE
);

CREATE TRIGGER trg_spot_catalog_translation_sync_location
    BEFORE INSERT OR UPDATE OF map_x, map_y ON spot_catalog_translation
    FOR EACH ROW EXECUTE FUNCTION spot_catalog_sync_location();

-- FK 자식 쪽 인덱스. 없으면 부모 1행 삭제마다 자식 전체 스캔이 발생한다.
CREATE INDEX idx_spot_catalog_translation_catalog ON spot_catalog_translation (catalog_id);

-- 언어별 목록 조회의 진입 인덱스.
CREATE INDEX idx_spot_catalog_translation_language ON spot_catalog_translation (language);

-- ---------------------------------------------------------------------------
-- 3. spot_catalog_festival — 축제 기간(1:1, 축제인 카탈로그만).
--
-- 기간은 **언어 무관한 장소 자체의 속성이다** — 실측상 다국어 축제 60개 중 기간이 다른 것은
-- 3개뿐이다. 원본은 spot_festival에 남고 이것은 KO 우선으로 확정한 사본이다.
--
-- PK가 catalog_id라 그 자체로 upsert 단위다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_festival (
    catalog_id BIGINT PRIMARY KEY,

    -- 원본 spot_festival(V18)과 같은 DATE다. 축제 기간은 날짜 단위이며 시각이 없다.
    event_start_date DATE NOT NULL,
    event_end_date DATE NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_spot_catalog_festival_period CHECK (event_end_date >= event_start_date),
    CONSTRAINT fk_spot_catalog_festival_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE
);

-- 진행 중 축제 조회가 타는 인덱스.
CREATE INDEX idx_spot_catalog_festival_end_date ON spot_catalog_festival (event_end_date);

-- ---------------------------------------------------------------------------
-- 4. spot.catalog_id — 원본이 어느 카탈로그에 속하는가.
--
-- **spot에 추가하는 유일한 컬럼이다.** 원본 테이블의 나머지는 그대로 둔다.
--
-- nullable인 이유는 적재와 배정이 다른 Step이기 때문이다 — 새로 적재된 행은 다음 배정까지
-- null이고, 좌표가 깨진 행은 영구히 null이다(카탈로그를 만들지 않는다).
-- ---------------------------------------------------------------------------
ALTER TABLE spot ADD COLUMN catalog_id BIGINT;

ALTER TABLE spot
    ADD CONSTRAINT fk_spot_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE SET NULL;

-- 배정 배치가 미배정 행을 찾을 때 쓴다. 부분 인덱스라 배정이 끝날수록 작아진다.
CREATE INDEX idx_spot_catalog_id ON spot (catalog_id) WHERE catalog_id IS NOT NULL;

-- **상세 조회의 진입점이다.** `spot`을 읽는 유일한 조회 경로가 `(catalog_id, language)`로
-- contentId를 찾는다 — TourAPI의 contentId가 언어별이라 그 언어의 원본 행을 거쳐야 한다.
CREATE INDEX idx_spot_catalog_language ON spot (catalog_id, language);
