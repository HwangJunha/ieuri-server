-- 시군구 대표 위치(SPEC-0015 추가 요건).
--
-- **center와 다른 값이고 용도도 다르다.**
--   center                  = 경계에서 산출한 기하학적 대표점. 항상 있다(NOT NULL).
--   representative_location = spot이 실제로 몰려 있는 지점. 없을 수 있다(NULL).
--
-- center만으로 카메라를 옮기면 산속·해안처럼 spot이 없는 곳을 비추는 경우가 생긴다.
-- 실측(2026-08-11): 36개 시군구가 center 반경 2km 안에 spot이 0건이고, 최악은 영덕군으로
-- 가장 가까운 spot이 5.8km 떨어져 있었다. spot을 추천하는 서비스에서 이는 결함이다.
--
-- **NULL은 "대표 위치를 선정하지 못했다"는 뜻이며 그 지역은 추천 대상에서 제외된다.**
-- 추천했는데 정작 그 지역에 갈 곳이 없으면 추천이 성립하지 않기 때문이다. 0으로 채우는
-- 대신 부재를 드러내는 POL-043과 같은 원리다.
--
-- 선정은 배치가 한다(`sigunguRepresentativeLocationJob`). **spot 동기화 이후에 실행해야
-- 한다** — spot 테이블을 읽어 산출하므로 순서가 뒤집히면 전날 데이터로 계산한다.

ALTER TABLE sigungu
    ADD COLUMN representative_location geography(Point, 4326),
    ADD COLUMN representative_spot_count integer,
    ADD COLUMN representative_updated_at timestamptz;

COMMENT ON COLUMN sigungu.representative_location IS
    'spot 밀집 지점에서 고른 실제 spot 좌표. NULL이면 선정 실패이며 추천에서 제외된다.';
COMMENT ON COLUMN sigungu.representative_spot_count IS
    '선정 근거가 된 밀집 격자 안의 spot 수. 선정 기준 미달 여부를 사후에 확인하는 용도다.';
COMMENT ON COLUMN sigungu.representative_updated_at IS
    '대표 위치를 마지막으로 갱신한 시각. 배치가 돌지 않고 있는 것을 관측하는 용도다.';

-- 추천 질의가 `representative_location IS NOT NULL`로 후보를 좁힌다.
CREATE INDEX idx_sigungu_representative_location
    ON sigungu USING GIST (representative_location)
    WHERE representative_location IS NOT NULL;
