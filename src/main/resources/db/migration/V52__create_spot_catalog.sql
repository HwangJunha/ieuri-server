-- tourism 도메인: 장소 단위 카탈로그(SPEC-0019 R-1).
--
-- TourAPI는 같은 장소를 언어마다 다른 contentId로 발급한다. SPEC-0004가 그것을
-- UNIQUE(content_id, language)로 적재했고, 그 결과 부산 마린시티 한 곳이 4개의 spot 행으로
-- 존재한다. 사용자에게는 하나의 장소인데 시스템은 넷으로 센다.
--
-- 카탈로그는 그 위에 얹는 층이다. **spot 테이블의 구조는 바꾸지 않는다** — 언어별 행은
-- 그대로 두고, catalog_id가 "이 행들은 같은 장소다"를 표현한다.

-- ---------------------------------------------------------------------------
-- 1. spot_catalog — 장소 하나. 언어에 독립적이다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 대표 한국어명. 카탈로그를 사람이 식별하는 이름이자 번역의 원문이다.
    representative_name_ko VARCHAR(255) NOT NULL,
    -- 대표 한국어명의 출처.
    --   OFFICIAL: KO spot 행의 title을 그대로 쓴 것.
    --   DERIVED : KO 행이 없어 외국어 제목의 괄호에서 추출한 것(R-3). TourAPI가 보증한
    --             정식 명칭이 아니므로 구분해 둔다 — 언어별 괄호값이 서로 다른 경우가
    --             163개 그룹 있다(`초유향`/`초류향`).
    name_source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_spot_catalog_name_source CHECK (name_source IN ('OFFICIAL', 'DERIVED')),
    CONSTRAINT ck_spot_catalog_name_not_blank CHECK (btrim(representative_name_ko) <> '')
);

-- ---------------------------------------------------------------------------
-- 2. spot_catalog_translation — 카탈로그의 언어별 제목.
--
-- **OFFICIAL 행은 spot.title과 값이 중복된다. 의도한 것이다** — 조회가 이 테이블 하나만
-- 보면 되고, 그 언어의 spot 행이 존재하지 않아도 제목을 가질 수 있다.
-- community_post_spot이 좌표를 복사해 둔 것(V34)과 같은 판단이다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_translation (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_id BIGINT NOT NULL,
    language VARCHAR(2) NOT NULL,
    title VARCHAR(255) NOT NULL,
    -- 그 언어의 주소. **제목과 함께 번역된다**(R-4, 2026-08-14 개정).
    --
    -- 제목만 번역하면 목록에는 나오지만 **찾아갈 수는 없다** — 일본어 사용자가 일본어 제목
    -- 아래 한국어 주소를 보게 된다. 노출을 늘린다는 이 스펙의 목적이 거기서 멈춘다.
    --
    -- nullable인 이유는 원본에 주소가 없는 spot이 실재하기 때문이다(KO 기준 1.5%).
    -- 번역할 원문이 없으면 여기도 null이다.
    address VARCHAR(500),
    -- 이 제목이 어디서 왔는가.
    --   OFFICIAL: TourAPI가 그 언어로 발급한 제목.
    --   MACHINE : 이 스펙이 KO 원문에서 생성한 번역.
    --
    -- **이 컬럼이 없으면 TourAPI가 나중에 공식 번역을 제공했을 때 우리 번역을 덮어써도
    -- 되는지 판단할 수 없다**(R-5). 공식 표기가 우리 번역보다 우선한다.
    source VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 카탈로그는 언어당 제목 하나를 갖는다.
    CONSTRAINT uq_spot_catalog_translation UNIQUE (catalog_id, language),
    CONSTRAINT ck_spot_catalog_translation_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_spot_catalog_translation_source CHECK (source IN ('OFFICIAL', 'MACHINE')),
    CONSTRAINT ck_spot_catalog_translation_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT fk_spot_catalog_translation_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------------
-- 3. spot.catalog_id
--
-- **nullable로 시작한다.** 배정 배치가 전량을 채운 뒤 별도 마이그레이션으로 NOT NULL을
-- 조인다 — 기존 115,217행을 마이그레이션 안에서 판정할 수는 없다(판정이 도메인 규칙이라
-- 애플리케이션에 있다).
--
-- FK는 ON DELETE RESTRICT가 아니라 SET NULL이다. spot이 사라지는 것은 정상이지만
-- (SPEC-0004 R-5의 정리), 카탈로그가 사라지는 것은 정상이 아니다 — 그런 일이 생기면
-- spot 행을 지우는 대신 참조만 끊고 배치가 다시 배정하게 둔다.
-- ---------------------------------------------------------------------------
ALTER TABLE spot ADD COLUMN catalog_id BIGINT;

ALTER TABLE spot
    ADD CONSTRAINT fk_spot_catalog FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE SET NULL;

-- 제목 정규화 키(전각 괄호 반각화·대괄호 태그 제거·공백 제거 후의 값).
--
-- **적재 시점에 계산해 저장한다.** 매칭은 이 키로 KO 후보를 찾는데, 조회할 때마다 SQL에서
-- 정규화하면 인덱스를 탈 수 없어 51,992개 KO 행을 매번 전량 스캔한다. 정규화 규칙은
-- 도메인(SpotTitleNormalizer)이 소유하므로 DB 함수로 옮기지 않고 애플리케이션이 채운다.
--
-- nullable인 이유는 정규화 결과가 빈 문자열일 수 있기 때문이다 — 제목이 태그뿐인 경우다.
-- 빈 키로 매칭하면 무관한 장소가 전부 한 카탈로그로 뭉치므로 그때는 키를 두지 않는다.
ALTER TABLE spot ADD COLUMN title_key VARCHAR(255);

-- 카탈로그 → 언어별 spot 조회. 좋아요 목록·커뮤니티 칩이 전부 이 경로를 탄다
-- (catalog_id로 좁힌 뒤 language로 1행을 고른다).
CREATE INDEX idx_spot_catalog_language ON spot (catalog_id, language);

-- 배정 배치의 대상 선정: catalog_id IS NULL인 행을 찾는다.
CREATE INDEX idx_spot_catalog_id_unassigned ON spot (id) WHERE catalog_id IS NULL;

-- 매칭 순위 1·2가 타는 인덱스: 정규화 키로 KO 후보를 찾는다.
-- **KO 부분 인덱스다** — 매칭 대상은 항상 KO 행이고, 51,992행만 담으면 인덱스가 절반 이하로 작아진다.
CREATE INDEX idx_spot_title_key_ko ON spot (title_key) WHERE language = 'KO' AND title_key IS NOT NULL;

-- 매칭 순위 3이 타는 인덱스: 썸네일 URL로 KO 후보를 찾는다.
CREATE INDEX idx_spot_thumbnail_ko ON spot (thumbnail_image_url) WHERE language = 'KO';

-- ---------------------------------------------------------------------------
-- 4. spot_catalog_unresolved — 자동 판정이 불가능한 건(R-8).
--
-- 실측 509건(0.81%)이며 규칙을 정교화해도 줄지 않는다 — 원본에 한국어가 아예 없거나
-- (`SM Entertainment`), `만두집`처럼 전국에 흔해 후보가 모순되기 때문이다.
--
-- **미해결 건도 독립 카탈로그를 갖는다.** 감추면 SM Entertainment 같은 유효한 spot이
-- 사라진다. 이 테이블은 "무엇이 사람 판정 대상인가"를 알리는 용도이며, 판정 결과는
-- 사람이 DB를 직접 고쳐 반영한다 — 반영용 API·화면을 만들지 않는다.
-- ---------------------------------------------------------------------------
CREATE TABLE spot_catalog_unresolved (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    spot_id BIGINT NOT NULL,
    -- 판정 방법이 사유마다 달라 구분한다.
    --   KEY_AMBIGUOUS: 제목 키는 맞는 KO 후보가 여럿인데 좌표 완전일치가 없거나 둘 이상.
    --   NO_KOREAN_KEY: 제목에서 한국어를 추출할 수 없음.
    --   URL_AMBIGUOUS: 썸네일 URL을 여러 KO가 공유.
    reason VARCHAR(30) NOT NULL,
    -- 제목에서 추출한 한국어 키. NO_KOREAN_KEY면 null이다.
    extracted_key VARCHAR(255),
    -- 후보 KO spot id 목록. CSV에 후보별 좌표·카테고리를 싣기 위한 원본이다.
    candidate_spot_ids BIGINT[] NOT NULL DEFAULT '{}',
    detected_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 spot은 한 번만 미해결로 기록된다. 배치를 다시 돌려도 행이 늘지 않는다.
    CONSTRAINT uq_spot_catalog_unresolved UNIQUE (spot_id),
    CONSTRAINT ck_spot_catalog_unresolved_reason
        CHECK (reason IN ('KEY_AMBIGUOUS', 'NO_KOREAN_KEY', 'URL_AMBIGUOUS')),
    CONSTRAINT fk_spot_catalog_unresolved_spot
        FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE
);
