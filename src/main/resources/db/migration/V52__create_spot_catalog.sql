-- tourism 도메인: 장소 단위 카탈로그(SPEC-0019).
--
-- **목적은 언어 간 격차 해소다.** TourAPI는 같은 장소를 언어마다 다른 contentId로 발급하는데,
-- 국문이 56,425행인 반면 영·일·중은 각각 21,000행 안팎이다. 그래서 일본어 사용자는 지금
-- 21,111개만 본다. 국문을 기준으로 번역해 채우면 56,425개를 볼 수 있다.
--
-- 역할을 이렇게 가른다:
--
--   spot                     원본 보관. TourAPI가 준 그대로, 언어별 행 유지. 조회하지 않는다
--   spot_catalog             장소 정체성 + 언어 무관 값(좌표·썸네일·분류)
--   spot_catalog_translation 언어별 표시값(제목·주소)
--
-- **조회는 spot을 보지 않는다.** 카탈로그와 번역만 본다 — 그래야 그 언어의 spot 행이 없어도
-- 번역만 있으면 목록에 나온다. 그것이 이 스펙의 목적이다.

-- ---------------------------------------------------------------------------
-- 1. spot_catalog — 장소 하나. 언어에 독립적이다.
--
-- 언어 무관 값을 여기 둔다. 실측(2026-08-14) 근거:
--   copyright  언어별 불일치 0건
--   thumbnail  사실상 동일 (매칭 신호로 쓸 만큼)
--   category   88건만 불일치. 65%가 "외국어는 TOURIST_SPOT으로 뭉개고 KO가 구체적"
--   좌표       불일치처럼 보이던 것은 (0,0)·한국 밖 결손 317건이었다
--
-- **어느 값을 채택하는가는 전부 KO 우선이다** — KO가 원본 언어이고 가장 많으며 번역 기준이다.
-- 보정 규칙을 따로 두지 않는다. 교통(TRANSPORT)은 국문 코드 체계에 없어 외국어 148건에만
-- 있는데, KO 행이 있으면 KO를 따르고 KO가 없는 파생 카탈로그에만 남는다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 대표 한국어명. 사람이 카탈로그를 식별하는 이름이자 번역의 원문이다.
    representative_name_ko VARCHAR(255) NOT NULL,

    -- 대표명의 출처.
    --   OFFICIAL: KO spot 행의 title을 그대로 쓴 것.
    --   DERIVED : KO 행이 없어 외국어 제목의 괄호에서 추출한 것(R-3). 정식 명칭이 아니다 —
    --             언어별 괄호값이 서로 다른 경우가 163개 그룹 있다(`초유향`/`초류향`).
    name_source VARCHAR(20) NOT NULL,

    -- **매칭 조회 키.** 비한국어 행이 유입되면 제목 괄호에서 한글을 뽑아 이 컬럼으로 카탈로그를
    -- 찾는다: `Marine City (마린시티)` → `마린시티` → 이 키와 대조.
    --
    -- 정규화(전각 괄호 반각화·대괄호 태그 제거·공백 제거)를 거친 값이라 원문과 다를 수 있다.
    -- **저장해 두는 이유는 인덱스다** — 조회할 때마다 SQL에서 정규화하면 인덱스를 타지 못해
    -- 매번 전량 스캔이 된다. 정규화 규칙은 도메인(SpotTitleNormalizer)이 소유하며 DB 함수로
    -- 옮기지 않는다. 규칙이 두 곳에 생기면 반드시 어긋난다.
    --
    -- 정규화 결과가 빈 문자열일 수 있어(제목이 태그뿐인 경우) nullable이다.
    name_key VARCHAR(255),

    -- ── 언어 무관 값 ────────────────────────────────────────────────────────
    --
    -- **유효 좌표만 담는다.** 원본에 (0,0) 135건과 한국 밖 182건이 있다 — 상류 결손이다.
    -- 배치가 유효 좌표를 KO → EN → JA → ZH 순으로 골라 채운다. 간절곶처럼 KO가 (0,0)이고
    -- EN만 유효한 경우가 이 순서로 구제된다.
    --
    -- 전부 무효면 null이며, 그 카탈로그는 지도·반경 조회에서 빠진다 — 위치를 줄 수 없는
    -- 장소를 지도에 찍을 수는 없다.
    map_x DOUBLE PRECISION,
    map_y DOUBLE PRECISION,
    location geography(Point, 4326),

    thumbnail_image_url VARCHAR(500),
    category VARCHAR(30),
    copyright VARCHAR(20),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_spot_catalog_name_source CHECK (name_source IN ('OFFICIAL', 'DERIVED')),
    CONSTRAINT ck_spot_catalog_name_not_blank CHECK (btrim(representative_name_ko) <> ''),
    -- 좌표 유효 범위. spot 테이블과 같은 불변식을 여기서도 지킨다.
    CONSTRAINT ck_spot_catalog_map_x CHECK (map_x IS NULL OR map_x BETWEEN -180 AND 180),
    CONSTRAINT ck_spot_catalog_map_y CHECK (map_y IS NULL OR map_y BETWEEN -90 AND 90),
    -- 좌표는 쌍으로 있거나 함께 없다. 하나만 있으면 위치를 만들 수 없다.
    CONSTRAINT ck_spot_catalog_coords_paired CHECK ((map_x IS NULL) = (map_y IS NULL))
);

-- 매칭이 타는 인덱스. 신규 유입분마다 한 번씩 조회한다.
CREATE INDEX idx_spot_catalog_name_key ON spot_catalog (name_key) WHERE name_key IS NOT NULL;

-- 반경 조회·거리 정렬(spots·mapViewport·popularSpots·ongoingFestivals).
CREATE INDEX idx_spot_catalog_location ON spot_catalog USING GIST (location);

-- 카테고리 필터.
CREATE INDEX idx_spot_catalog_category ON spot_catalog (category);

-- 좌표를 map_x/map_y에서 파생시킨다(spot·community_post_spot과 같은 방식).
-- 애플리케이션이 파생을 관리하면 둘이 어긋날 수 있고, JPA 엔티티가 PostGIS 타입을 알 필요도 없다.
--
-- **ST_MakePoint는 (경도, 위도) 순서다** — 뒤집으면 조용히 틀린다.
CREATE OR REPLACE FUNCTION spot_catalog_sync_location() RETURNS trigger AS $$
BEGIN
    NEW.location := CASE
        WHEN NEW.map_x IS NULL OR NEW.map_y IS NULL THEN NULL
        ELSE ST_MakePoint(NEW.map_x, NEW.map_y)::geography
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_spot_catalog_sync_location
    BEFORE INSERT OR UPDATE OF map_x, map_y ON spot_catalog
    FOR EACH ROW EXECUTE FUNCTION spot_catalog_sync_location();

-- ---------------------------------------------------------------------------
-- 2. spot_catalog_translation — 언어별 표시값.
--
-- **제목과 주소를 함께 갖는다**(R-4). 제목만 번역하면 일본어 제목 아래 한국어 주소가 붙어
-- 그 장소를 찾아갈 수 없다 — 목록에 나오는 의미가 사라진다.
--
-- 길찾기는 좌표가 담당하므로(응답에 이미 나간다) 여기 주소는 **읽기용**이다. 사용자가
-- "대략 어디인지" 읽고, 원문이 필요하면 KO 행의 주소를 쓴다.
--
-- OFFICIAL 행은 spot.title과 값이 중복된다. 의도한 것이다 — 조회가 이 테이블 하나만 보면 되고,
-- 그 언어의 spot 행이 없어도 제목을 가질 수 있다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_translation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    language VARCHAR(2) NOT NULL,
    title VARCHAR(255) NOT NULL,

    -- 원문에 주소가 없는 spot이 실재해(KO 기준 1.5%) nullable이다.
    address VARCHAR(500),

    -- 이 값이 어디서 왔는가.
    --   OFFICIAL: TourAPI가 그 언어로 발급한 것.
    --   MACHINE : 이 스펙이 KO 원문에서 생성한 번역.
    --
    -- **이 컬럼이 없으면 TourAPI가 나중에 공식 번역을 제공했을 때 우리 번역을 덮어써도 되는지
    -- 판단할 수 없다**(R-5). 공식 표기가 우리 번역보다 우선한다.
    source VARCHAR(20) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 한 카탈로그는 언어당 값 하나를 갖는다. 조회가 이 제약에 기대 1행을 보장한다.
    CONSTRAINT uq_spot_catalog_translation UNIQUE (catalog_id, language),
    CONSTRAINT ck_spot_catalog_translation_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_spot_catalog_translation_source CHECK (source IN ('OFFICIAL', 'MACHINE')),
    CONSTRAINT ck_spot_catalog_translation_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT fk_spot_catalog_translation_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE
);

-- 조회가 (언어, 카탈로그)로 조인한다. language를 선행에 두는 이유는 언어가 항상 고정값이고
-- 카탈로그가 범위이기 때문이다.
CREATE INDEX idx_spot_catalog_translation_language ON spot_catalog_translation (language, catalog_id);

-- ---------------------------------------------------------------------------
-- 3. spot.catalog_id — 원본과 카탈로그를 잇는 유일한 연결.
--
-- **spot 테이블에 추가하는 것은 이 컬럼 하나뿐이다.** 기존 컬럼은 값도 구조도 건드리지 않는다.
-- 매칭 조회 키(name_key)를 카탈로그에 둔 이유가 이것이다 — 원본을 최대한 원본으로 남긴다.
--
-- nullable이다. 적재 배치가 새 행을 넣은 직후와 매칭 배치가 돌기 전 사이에 null인 구간이
-- 항상 존재한다.
--
-- FK는 ON DELETE SET NULL이다. 카탈로그가 사라지는 것은 정상이 아니지만, 그런 일이 생기면
-- spot 행을 지우는 대신 참조만 끊고 배치가 다시 배정하게 둔다 — 원본은 지우지 않는다.
-- ---------------------------------------------------------------------------
ALTER TABLE spot ADD COLUMN catalog_id BIGINT;

ALTER TABLE spot
    ADD CONSTRAINT fk_spot_catalog FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE SET NULL;

-- 상세 조회가 (catalog_id, language)로 spot 1행을 해석한다 — contentId를 얻어 TourAPI를 부르는
-- 경로다. 매칭 배치의 대상 선정(catalog_id IS NULL)도 이 인덱스를 탄다.
CREATE INDEX idx_spot_catalog_language ON spot (catalog_id, language);

-- ---------------------------------------------------------------------------
-- 4. spot_catalog_festival — 축제 기간(1:1, 축제인 카탈로그만).
--
-- 기간은 **언어와 무관한 장소 자체의 속성이다**. 실측(2026-08-14): 다국어 축제 60개 중
-- 기간이 다른 것은 3개뿐이고, 스펙 §3.6도 "제목만 다르고 기간과 이미지는 완전히 같다"를
-- 실증했다. 그래서 카탈로그의 표준값으로 올린다 — 좌표·썸네일과 같은 취급이다.
--
-- **spot_catalog에 컬럼으로 넣지 않고 별도 테이블로 둔다.** 축제는 전체의 일부라
-- (4,530 / 56,000) 컬럼으로 두면 나머지 전 행에서 null로 남는다. V18이 `spot`에 축제 컬럼을
-- 늘리지 않은 것과 같은 판단이다.
--
-- **EAV(키-값)로 두지 않는다.** 속성 종류가 2개로 고정이고, 무엇보다 `DATE`를 문자열로
-- 눕히면 `event_end_date >= :today` 조회가 캐스팅 때문에 인덱스를 타지 못한다.
--
-- 원본은 `spot_festival`에 그대로 남는다 — 이 테이블은 매칭 배치가 KO 우선으로 확정한 사본이다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_festival (
    catalog_id BIGINT PRIMARY KEY,
    event_start_date DATE NOT NULL,
    event_end_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 기간이 뒤집힌 행이 들어오면 종료 판정이 무의미해진다. 쓰기 시점에 드러나게 한다.
    CONSTRAINT ck_spot_catalog_festival_period CHECK (event_end_date >= event_start_date),
    CONSTRAINT fk_spot_catalog_festival_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE
);

-- 진행 중 축제 조회(`ongoingFestivals`)가 종료일로 거른다.
CREATE INDEX idx_spot_catalog_festival_end_date ON spot_catalog_festival (event_end_date);

-- ---------------------------------------------------------------------------
-- 5. spot_catalog_unresolved — 자동 판정이 불가능한 건(R-8).
--
-- 실측 509건(0.81%)이며 규칙을 정교화해도 줄지 않는다 — 원본에 한국어가 아예 없거나
-- (`SM Entertainment`), `만두집`처럼 전국에 흔해 후보가 모순되기 때문이다.
--
-- **미해결 건도 독립 카탈로그를 갖는다.** 감추면 유효한 spot이 사라진다. 이 테이블은 "무엇이
-- 사람 판정 대상인가"를 알리는 용도이며, 판정 결과는 사람이 DB를 직접 고쳐 반영한다 —
-- 반영용 API·화면을 만들지 않는다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_unresolved (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spot_id BIGINT NOT NULL,

    -- 판정 방법이 사유마다 달라 구분한다.
    --   KEY_AMBIGUOUS: 제목 키는 맞는 후보가 여럿인데 좌표 완전일치가 없거나 둘 이상.
    --   NO_KOREAN_KEY: 제목에서 한국어를 추출할 수 없음.
    --   URL_AMBIGUOUS: 썸네일 URL을 여러 카탈로그가 공유.
    reason VARCHAR(30) NOT NULL,

    -- 제목에서 추출한 한국어 키. NO_KOREAN_KEY면 null이다.
    extracted_key VARCHAR(255),

    -- 후보 카탈로그 id 목록. CSV에 후보별 좌표·카테고리를 싣기 위한 원본이다 —
    -- 사람이 판정하려면 "무엇과 헷갈렸는가"를 알아야 한다.
    candidate_catalog_ids BIGINT[] NOT NULL DEFAULT '{}',

    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 한 spot은 한 번만 기록된다. 배치를 다시 돌려도 CSV 줄 수가 부풀지 않는다.
    CONSTRAINT uq_spot_catalog_unresolved UNIQUE (spot_id),
    CONSTRAINT ck_spot_catalog_unresolved_reason
        CHECK (reason IN ('KEY_AMBIGUOUS', 'NO_KOREAN_KEY', 'URL_AMBIGUOUS')),
    CONSTRAINT fk_spot_catalog_unresolved_spot
        FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE
);
