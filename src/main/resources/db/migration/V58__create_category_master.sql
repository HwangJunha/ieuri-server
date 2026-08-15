-- 카테고리를 코드에서 데이터로 옮긴다(SPEC-0020).
--
-- 지금까지 카테고리는 Kotlin enum + GraphQL enum + CHECK 제약 3개에 같은 9개 값이 박혀 있었다.
-- "관광 촬영지" 같은 카테고리가 하나 생기면 네 곳을 함께 고치고 배포해야 했고, 표시 문구·아이콘은
-- 앱에 하드코딩되어 있어 **앱 업데이트까지** 필요했다.
--
-- 이 테이블이 카테고리의 정의를 갖는다. 새 카테고리는 여기에 INSERT 한 번이며, 서버 배포도 앱
-- 업데이트도 필요 없다.
--
-- **표시 문구를 서버가 갖는 것은 POL-023의 예외다.** enum 값은 식별자이지 표시 문자열이 아니라는
-- 원칙은 그대로이나 — 그래서 code와 label을 분리했다 — 앱이 모르는 카테고리의 label까지 앱이
-- 가질 수는 없다. 앱 업데이트 없이 새 카테고리를 노출하는 것이 이 변경의 목적이므로 label과
-- 아이콘은 서버가 내려준다.
CREATE TABLE category (
    -- 안정 식별자. 필터 인자·spot.category가 이 값을 쓴다. 한번 정하면 바꾸지 않는다 —
    -- 캐시 키와 다른 도메인의 저장값이 이 문자열이다.
    code       VARCHAR(30) PRIMARY KEY,
    -- 칩·마커에 그릴 이미지(https). 없으면 클라이언트 기본 표현으로 떨어진다.
    icon_url   VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_category_code_not_blank CHECK (btrim(code) <> ''),
    -- 코드는 대문자 스네이크로 고정한다. 대소문자만 다른 두 코드가 생기면 저장값 비교가 깨진다.
    CONSTRAINT ck_category_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$')
);

-- 언어별 표시 문구. 앱이 모르는 카테고리도 이름을 그릴 수 있게 하는 것이 존재 이유다.
CREATE TABLE category_translation (
    category_code VARCHAR(30) NOT NULL,
    language      VARCHAR(2)  NOT NULL,
    label         VARCHAR(50) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (category_code, language),
    CONSTRAINT ck_category_translation_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_category_translation_label_not_blank CHECK (btrim(label) <> ''),
    CONSTRAINT fk_category_translation_category
        FOREIGN KEY (category_code) REFERENCES category (code) ON DELETE CASCADE
);

-- 기존 9개 값을 옮긴다. 지금까지 enum이 갖고 있던 목록 그대로이며 새 값은 넣지 않는다.
INSERT INTO category (code) VALUES
    ('TOURIST_SPOT'), ('CULTURAL_FACILITY'), ('FESTIVAL_EVENT'), ('TRAVEL_COURSE'),
    ('LEISURE_SPORTS'), ('LODGING'), ('SHOPPING'), ('RESTAURANT'), ('TRANSPORT');

INSERT INTO category_translation (category_code, language, label) VALUES
    ('TOURIST_SPOT',      'KO', '관광지'),
    ('TOURIST_SPOT',      'EN', 'Attractions'),
    ('TOURIST_SPOT',      'JA', '観光地'),
    ('TOURIST_SPOT',      'ZH', '觀光景點'),
    ('CULTURAL_FACILITY', 'KO', '문화시설'),
    ('CULTURAL_FACILITY', 'EN', 'Culture'),
    ('CULTURAL_FACILITY', 'JA', '文化施設'),
    ('CULTURAL_FACILITY', 'ZH', '文化設施'),
    ('FESTIVAL_EVENT',    'KO', '축제·공연'),
    ('FESTIVAL_EVENT',    'EN', 'Festivals'),
    ('FESTIVAL_EVENT',    'JA', '祭り・イベント'),
    ('FESTIVAL_EVENT',    'ZH', '慶典活動'),
    ('TRAVEL_COURSE',     'KO', '여행코스'),
    ('TRAVEL_COURSE',     'EN', 'Courses'),
    ('TRAVEL_COURSE',     'JA', '旅行コース'),
    ('TRAVEL_COURSE',     'ZH', '旅遊路線'),
    ('LEISURE_SPORTS',    'KO', '레포츠'),
    ('LEISURE_SPORTS',    'EN', 'Leisure'),
    ('LEISURE_SPORTS',    'JA', 'レジャー'),
    ('LEISURE_SPORTS',    'ZH', '休閒運動'),
    ('LODGING',           'KO', '숙박'),
    ('LODGING',           'EN', 'Stays'),
    ('LODGING',           'JA', '宿泊'),
    ('LODGING',           'ZH', '住宿'),
    ('SHOPPING',          'KO', '쇼핑'),
    ('SHOPPING',          'EN', 'Shopping'),
    ('SHOPPING',          'JA', 'ショッピング'),
    ('SHOPPING',          'ZH', '購物'),
    ('RESTAURANT',        'KO', '맛집'),
    ('RESTAURANT',        'EN', 'Food'),
    ('RESTAURANT',        'JA', 'グルメ'),
    ('RESTAURANT',        'ZH', '美食'),
    ('TRANSPORT',         'KO', '교통'),
    ('TRANSPORT',         'EN', 'Transport'),
    ('TRANSPORT',         'JA', '交通'),
    ('TRANSPORT',         'ZH', '交通');

-- 카테고리 값을 열거하던 CHECK 제약을 FK로 바꾼다. 값 목록이 두 곳에 있으면 새 카테고리를
-- 넣을 때 반드시 한쪽을 잊는다 — 그게 이 변경이 없애려는 것이다.
ALTER TABLE app_category DROP CONSTRAINT ck_app_category_category;
ALTER TABLE app_category ADD CONSTRAINT fk_app_category_category
    FOREIGN KEY (category) REFERENCES category (code) ON DELETE CASCADE;

ALTER TABLE community_post_chip DROP CONSTRAINT ck_community_post_chip_category;
-- community_post_chip은 다른 도메인(community)이라 물리 FK를 걸지 않는다 — 도메인 간 FK 금지.
-- 값의 정합성은 애플리케이션이 지킨다(모르는 코드는 칩에서 조용히 빠진다).
