-- 시군구별 카테고리 구성비(SPEC-0015 추천 항목 확장).
--
-- **외부 데이터가 아니라 우리 spot을 다시 해석한 것이다.** 지역마다 성격이 뚜렷하게 갈린다
-- (실측 2026-08-11): 가평군은 레저가 전체의 42%, 평창군은 맛집이 45%, 영월군은 레저 114건에
-- 맛집 16건이다. "한산한 곳"만으로는 알 수 없는 정보이며, 사용자가 취향을 고르면 ML 없이도
-- 개인화가 성립한다.
--
-- **별도 테이블을 만들지 않는다.** 시군구 1건에 카테고리별 개수 하나씩이라 1:1이고, 대표
-- 위치와 같은 배치가 같은 시점에 갱신한다. 테이블을 나누면 조인만 늘고 얻는 것이 없다.
--
-- 개수는 **KO spot 기준**이다 — 언어마다 행이 따로라 전 언어를 세면 번역이 잘 된 지역이
-- 부풀려진다(대표 위치 선정과 같은 이유, V46 주석 참조).

ALTER TABLE district
    ADD COLUMN spot_count_total             integer,
    ADD COLUMN spot_count_tourist_spot      integer,
    ADD COLUMN spot_count_restaurant        integer,
    ADD COLUMN spot_count_leisure_sports    integer,
    ADD COLUMN spot_count_shopping          integer,
    ADD COLUMN spot_count_cultural_facility integer,
    ADD COLUMN spot_count_festival_event    integer,
    ADD COLUMN spot_count_lodging           integer,
    ADD COLUMN spot_count_travel_course     integer,
    ADD COLUMN spot_count_updated_at        timestamptz;

COMMENT ON COLUMN district.spot_count_total IS
    '그 시군구 경계 안의 KO spot 총 개수. NULL이면 아직 집계되지 않았다.';
COMMENT ON COLUMN district.spot_count_updated_at IS
    '카테고리 집계를 마지막으로 갱신한 시각. 배치가 돌지 않는 것을 관측하는 용도다.';
