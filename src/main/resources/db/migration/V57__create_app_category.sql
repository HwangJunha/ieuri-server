-- 앱별 카테고리 구성 통합(SPEC-0020).
--
-- 지금까지 "이 맥락에서 어떤 카테고리를 보여줄 것인가"의 답이 두 군데 따로 있었다.
--   1. tourism : SpotCategory.availableIn(locale) — 코드에 하드코딩된 언어 비대칭
--                (TRAVEL_COURSE=KO 전용, TRANSPORT=non-KO 전용)
--   2. community : community_chip — 앱·언어별 DB 행
-- 같은 9개 값을 쓰면서 노출 규칙만 둘로 갈라져 있어, 카테고리를 하나 조정하려면 코드와
-- 데이터를 각각 고쳐야 했고 둘이 어긋나도 드러나지 않았다.
--
-- 이 테이블이 두 곳의 유일한 출처가 된다. community_chip은 폐기하고 여기로 흡수한다.
-- 노출 규칙이 데이터가 되므로 언어 비대칭도 서버 배포 없이 조정된다.
--
-- **표시 문구는 저장하지 않는다**(POL-023) — enum 값과 순서만 내려주고 "맛집"·"Food"·"グルメ"는
-- 앱이 매핑한다. 문구를 서버가 들면 언어별 레코드와 어드민 화면이 따라붙는다.
CREATE TABLE app_category (
    id       BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 어느 앱의 구성인가. 헤더(X-App-Key)로 받은 값과 대조한다. 회원·인증과 무관하다.
    app_key  VARCHAR(50) NOT NULL,
    -- **어느 언어의 구성인가.** 카테고리는 언어마다 가용 집합이 다르다 — 이 비대칭이
    -- 코드가 아니라 행으로 표현되는 것이 이 테이블의 목적이다.
    language VARCHAR(2)  NOT NULL,
    -- SpotCategory(=TargetCategory)의 값. 두 도메인이 같은 축을 쓴다.
    category VARCHAR(30) NOT NULL,
    -- 표시 순서. 작은 값이 앞이다.
    position SMALLINT    NOT NULL,
    -- 잠시 내리고 싶을 때 행을 지우지 않고 끈다 — 순서를 둔 채 다시 켤 수 있어야 시즌 운영이 편하다.
    enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 앱·한 언어에 같은 카테고리가 두 번 뜨지 않는다.
    CONSTRAINT uq_app_category UNIQUE (app_key, language, category),
    CONSTRAINT ck_app_category_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_app_category_category
        CHECK (category IN ('TOURIST_SPOT', 'CULTURAL_FACILITY', 'FESTIVAL_EVENT',
                            'TRAVEL_COURSE', 'LEISURE_SPORTS', 'LODGING',
                            'SHOPPING', 'RESTAURANT', 'TRANSPORT')),
    CONSTRAINT ck_app_category_app_key_not_blank CHECK (btrim(app_key) <> '')
);

-- 조회는 언제나 "이 앱·이 언어의 켜진 카테고리를 순서대로":
--   WHERE app_key = ? AND language = ? AND enabled ORDER BY position, id
CREATE INDEX idx_app_category_app_language
    ON app_category (app_key, language, position, id);

-- 기존 칩 구성을 그대로 옮긴다. 컬럼 구성이 같아 값 변환이 없다.
-- 옮긴 뒤 community_chip을 지우므로 이 INSERT가 유일한 이관 경로다.
INSERT INTO app_category (app_key, language, category, position, enabled, created_at, updated_at)
SELECT app_key, language, category, position, enabled, created_at, updated_at
FROM community_chip;

DROP TABLE community_chip;
