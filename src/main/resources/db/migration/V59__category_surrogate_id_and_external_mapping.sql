-- 카테고리 구조 정비(SPEC-0020). V58에서 세 가지를 바로잡는다.
--
-- 1) **대리 키를 둔다.** V58은 `code`를 PK로 썼다. 그러면 코드 문자열이 곧 신원이라, 오타를
--    고치거나 이름을 바꾸는 순간 참조하는 모든 행을 함께 갱신해야 한다. id를 분리하면 code는
--    바꿀 수 있는 값이 된다(여전히 UNIQUE라 조회 축으로 쓸 수 있다).
--
-- 2) **외부 코드 매핑을 테이블로 내린다.** 우리 카테고리는 우리만의 것이 아니라 TourAPI의
--    contentTypeId와 엮여 있고, 그 매핑이 지금은 application.yaml에 있다. 설정에 있으면 신규
--    카테고리의 spot을 **수집**하려 할 때마다 배포가 필요하다 — 저장·조회는 데이터로 되는데
--    수집만 배포가 필요한 반쪽짜리가 된다.
--
--    매핑은 (제공자, 코드셋)마다 있고 **비어 있는 칸이 있다** — 여행코스는 외국어 서비스에
--    없고, 교통은 국문 서비스에 없다. 별도 테이블이면 그 비대칭이 "행이 없다"로 자연히
--    표현되고, TourAPI 아닌 제공자가 늘어도 행만 추가된다.
--
-- 3) **position을 뺀다.** 서버가 노출 순서까지 정할 근거가 없다. 서버는 "이 앱·이 언어에
--    무엇을 보여줄 것인가"만 정하고, 배열 순서는 클라이언트가 자기 화면 기준으로 정한다.
--    enabled는 active로 바꾼다 — 상태를 나타내는 이름이 더 곧다.

-- ── 1) category: 대리 키 ────────────────────────────────────────────────
ALTER TABLE category ADD COLUMN id BIGINT GENERATED ALWAYS AS IDENTITY;

-- 자식이 참조를 옮기는 동안 양쪽 키가 모두 살아 있어야 한다.
ALTER TABLE category_translation ADD COLUMN category_id BIGINT;
UPDATE category_translation t SET category_id = c.id FROM category c WHERE c.code = t.category_code;
ALTER TABLE category_translation ALTER COLUMN category_id SET NOT NULL;

ALTER TABLE app_category ADD COLUMN category_id BIGINT;
UPDATE app_category a SET category_id = c.id FROM category c WHERE c.code = a.category;
ALTER TABLE app_category ALTER COLUMN category_id SET NOT NULL;

-- 이제 PK를 옮긴다. code는 UNIQUE로 남아 조회 축을 유지한다.
ALTER TABLE category_translation DROP CONSTRAINT fk_category_translation_category;
ALTER TABLE app_category DROP CONSTRAINT fk_app_category_category;
ALTER TABLE category DROP CONSTRAINT category_pkey;
ALTER TABLE category ADD CONSTRAINT category_pkey PRIMARY KEY (id);
ALTER TABLE category ADD CONSTRAINT uq_category_code UNIQUE (code);

ALTER TABLE category_translation DROP CONSTRAINT category_translation_pkey;
ALTER TABLE category_translation DROP COLUMN category_code;
ALTER TABLE category_translation ADD CONSTRAINT category_translation_pkey PRIMARY KEY (category_id, language);
ALTER TABLE category_translation ADD CONSTRAINT fk_category_translation_category
    FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE;

ALTER TABLE app_category DROP COLUMN category;
ALTER TABLE app_category ADD CONSTRAINT fk_app_category_category
    FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE;

-- ── 2) 외부 코드 매핑 ───────────────────────────────────────────────────
-- 우리 카테고리 ↔ 외부 제공자 코드. TourAPI의 contentTypeId가 첫 사용처다.
CREATE TABLE category_external_mapping (
    id            BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id   BIGINT      NOT NULL,
    -- 어느 외부 체계인가. 지금은 TOUR_API 하나이며, 늘어나면 값이 늘 뿐 구조는 그대로다.
    provider      VARCHAR(30) NOT NULL,
    -- 제공자 안에서 코드가 갈리는 축. TourAPI는 국문/외국어 서비스가 코드를 달리 쓴다
    -- (관광지가 KO 12, 외국어 76). 언어가 아니라 코드셋인 이유는 EN·JA·ZH가 같은 값을 쓰기 때문이다.
    codeset       VARCHAR(20) NOT NULL,
    -- 외부 체계의 코드. TourAPI는 숫자지만 다른 제공자가 문자열을 쓸 수 있어 문자열로 둔다.
    external_code VARCHAR(30) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 카테고리는 (제공자, 코드셋)마다 코드를 하나만 갖는다.
    CONSTRAINT uq_category_external_mapping UNIQUE (provider, codeset, category_id),
    -- 역방향도 하나여야 한다 — 같은 외부 코드가 두 카테고리를 가리키면 적재가 어느 쪽인지 모른다.
    CONSTRAINT uq_category_external_code UNIQUE (provider, codeset, external_code),
    CONSTRAINT fk_category_external_mapping_category
        FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE CASCADE
);

-- 적재는 언제나 "이 제공자·이 코드셋의 이 코드가 어느 카테고리인가"로 묻는다.
CREATE INDEX idx_category_external_mapping_lookup
    ON category_external_mapping (provider, codeset, external_code);

-- application.yaml의 category-codes를 그대로 옮긴다. **빈 칸은 행이 없다** —
-- 여행코스는 FOREIGN이, 교통은 KO가 없다.
INSERT INTO category_external_mapping (category_id, provider, codeset, external_code)
SELECT c.id, 'TOUR_API', m.codeset, m.external_code
FROM category c
JOIN (VALUES
    ('TOURIST_SPOT',      'KO',      '12'),
    ('CULTURAL_FACILITY', 'KO',      '14'),
    ('FESTIVAL_EVENT',    'KO',      '15'),
    ('TRAVEL_COURSE',     'KO',      '25'),
    ('LEISURE_SPORTS',    'KO',      '28'),
    ('LODGING',           'KO',      '32'),
    ('SHOPPING',          'KO',      '38'),
    ('RESTAURANT',        'KO',      '39'),
    ('TOURIST_SPOT',      'FOREIGN', '76'),
    ('CULTURAL_FACILITY', 'FOREIGN', '78'),
    ('FESTIVAL_EVENT',    'FOREIGN', '85'),
    ('LEISURE_SPORTS',    'FOREIGN', '75'),
    ('LODGING',           'FOREIGN', '80'),
    ('SHOPPING',          'FOREIGN', '79'),
    ('RESTAURANT',        'FOREIGN', '82'),
    ('TRANSPORT',         'FOREIGN', '77')
) AS m(code, codeset, external_code) ON m.code = c.code;

-- ── 3) 노출 구성에서 순서를 뺀다 ────────────────────────────────────────
ALTER TABLE app_category DROP COLUMN position;
ALTER TABLE app_category RENAME COLUMN enabled TO active;

-- position이 빠졌으니 인덱스도 다시 만든다. 조회는 "이 앱·이 언어의 켜진 것"이다.
DROP INDEX IF EXISTS idx_app_category_app_language;
CREATE INDEX idx_app_category_app_language ON app_category (app_key, language, category_id);

-- 한 앱·한 언어에 같은 카테고리가 두 번 뜨지 않는다(참조 축이 바뀌어 다시 건다).
ALTER TABLE app_category DROP CONSTRAINT uq_app_category;
ALTER TABLE app_category ADD CONSTRAINT uq_app_category UNIQUE (app_key, language, category_id);
