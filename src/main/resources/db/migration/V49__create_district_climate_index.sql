-- 시군구별 관광기후지수(SPEC-0015 추천 항목 확장).
--
-- 기상청이 강수량·기온·풍속·구름을 가중합해 **이미 판정한 결과**를 준다(강수 32% · 최고기온
-- 26% 등). 원자료로 우리가 계산하면 근거 없는 가중치를 또 만들게 되므로 판정 결과를 쓴다.
--
-- **전국이 1회 호출로 온다** — `CITY_AREA_ID`가 선택 파라미터라 생략하면 전 지역이 함께
-- 온다(실측 2026-08-11: 235개 시군구 472행). KTO 집중률처럼 지역을 순회하지 않는다.
--
-- **기상청은 구 행정코드를 쓴다**(강원 42 · 전북 45 · 전남/광주 12). 그대로 붙이면 252개 중
-- 173개만 맞고, 시도 코드를 매핑하면 227개가 맞는다 — 추천 후보 226개를 전부 덮는다.
-- 매핑은 `kma_province_code_map`이 들고 있다.

CREATE TABLE district_climate_index
(
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 우리 행정표준코드 5자리. 기상청 코드를 매핑한 뒤의 값이다.
    -- **FK를 걸지 않는다** — 매핑되지 않는 코드가 와도 배치가 계속되어야 한다(R9와 같은 원리).
    district_code varchar(5)  NOT NULL,

    -- 예보 대상 시각. 응답의 `tm`("2026-08-12 00:00")에서 날짜만 취한다.
    forecast_date date        NOT NULL,

    -- 관광기후지수 원값. 실측 범위 -0.66 ~ 0.52이라 정수가 아니다.
    tci_value     numeric     NOT NULL,

    -- 등급 문자열('매우좋음'·'좋음'·'보통'·'나쁨'). CHECK을 걸지 않는 이유는 V44와 같다.
    tci_grade     varchar(16),

    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),

    -- 자연키. 재실행해도 중복되지 않는다(R7).
    CONSTRAINT uk_district_climate_index_natural UNIQUE (district_code, forecast_date)
);

-- 추천은 "오늘 지수"를 지역별로 찾는다.
CREATE INDEX idx_district_climate_index_date
    ON district_climate_index (district_code, forecast_date DESC);

COMMENT ON TABLE district_climate_index IS
    '시군구별 관광기후지수. 기상청 판정 결과이며 지역 코드는 매핑 후의 행정표준코드다.';

-- 기상청 시도 코드 → 행정표준코드 시도 코드.
--
-- **코드 상수가 아니라 테이블로 둔다.** 행정구역은 실제로 바뀐다(강원·전북 특별자치도 승격이
-- 최근 사례다). 테이블이면 코드 수정·재배포 없이 행만 더하면 된다.
CREATE TABLE kma_province_code_map
(
    kma_code      varchar(2) PRIMARY KEY,
    district_code varchar(2) NOT NULL
);

COMMENT ON TABLE kma_province_code_map IS
    '기상청 구 행정코드 → 행정표준코드 시도 코드 매핑. 매핑이 없으면 그대로 쓴다.';

-- 실측으로 확인된 차이만 넣는다. 나머지 시도는 코드가 같아 매핑이 필요 없다.
INSERT INTO kma_province_code_map (kma_code, district_code) VALUES
    ('42', '51'),  -- 강원 → 강원특별자치도
    ('45', '52');  -- 전북 → 전북특별자치도

-- **광주는 시도 코드만으로 풀리지 않는다.** 기상청이 `12`(전남·광주 통합) 아래에 두면서
-- 시군구 뒷자리도 우리와 다르다(기상청 210/240/270/300/330 vs 우리 110/140/155/170/200).
-- 전남과 광주가 같은 `12`를 쓰므로 뒷자리로 갈라야 한다.
CREATE TABLE kma_district_code_map
(
    kma_code      varchar(5) PRIMARY KEY,
    district_code varchar(5) NOT NULL
);

COMMENT ON TABLE kma_district_code_map IS
    '시도 코드 매핑으로 풀리지 않는 개별 시군구. 이쪽이 우선한다.';

INSERT INTO kma_district_code_map (kma_code, district_code) VALUES
    ('12210', '29110'),  -- 광주 동구
    ('12240', '29140'),  -- 광주 서구
    ('12270', '29155'),  -- 광주 남구
    ('12300', '29170'),  -- 광주 북구
    ('12330', '29200');  -- 광주 광산구

-- 전남은 시도 코드만 바꾸면 되므로 위 테이블에 넣는다.
INSERT INTO kma_province_code_map (kma_code, district_code) VALUES ('12', '46');
