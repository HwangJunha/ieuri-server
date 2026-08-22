-- tourism 도메인: spot을 시군구에 귀속시킨다(SPEC-0030 R11).
--
-- **요청 시점 공간 조인이 아니라 사전 계산이다.** 칸 상세는 "한 화면 한 왕복"이 목표인데,
-- 조회마다 카탈로그와 시군구 252개를 공간 조인하면 그 목표가 성립하지 않는다(실측: 서울 칸
-- 20건에 120ms / 버퍼 30,623 → 사전 계산 시 36ms / 3,386). spot 좌표는 변하지 않으므로
-- 매번 계산할 이유도 없다.
--
-- **어느 시군구에도 속하지 않는 spot은 null로 남는다.** 오류가 아니며 칸 조회에서 빠질 뿐이다.
-- 좌표 CHECK가 한국 범위를 강제하지만 그 범위에는 바다도 포함되므로 null은 실제로 발생한다
-- (실측 43,216행 중 273행).
--
-- **이 컬럼을 트리거로 채우지 않는다.** `location`은 PostGIS 타입이라 JPA로 표현할 수 없어
-- 트리거가 유일한 수단이지만, 이쪽은 varchar(5)이고 담고 있는 것이 "장소는 자기 좌표가 속한
-- 시군구에 귀속된다"는 도메인 규칙이다. DB에 넣으면 DB를 바꿀 때 규칙이 함께 사라진다.
-- 채우는 일은 `spotDistrictAssignmentJob`이 맡는다.
--
-- 이 컬럼은 spot의 소유이며 방문 기록과 무관하다 — 사용자 위치가 아니라 장소 데이터이므로
-- POL-045의 대상이 아니다.

ALTER TABLE tour.spot_catalog ADD COLUMN district_code varchar(5);

-- 기존 행 전량 적재. 이후 새 spot은 `spotDistrictAssignmentJob`이 채운다.
--
-- **`ST_Intersects(boundary, location)`이며 컬럼을 캐스팅하지 않는다.** 양쪽 다 geography이고
-- boundary에 GiST 인덱스가 있어 그대로 인덱스를 탄다. 어느 한쪽을 ::geometry로 바꾸면
-- 인덱스가 죽는다(V52 계열 어댑터에서 실측된 함정과 같다).
UPDATE tour.spot_catalog cat
SET district_code = d.code
FROM tour.district d
WHERE ST_Intersects(d.boundary, cat.location);

-- 칸 상세가 이 컬럼으로 필터한다. 좋아요 수 내림차순 정렬이 뒤따르므로 선행 컬럼이어야 한다.
CREATE INDEX idx_spot_catalog_district ON tour.spot_catalog (district_code)
    WHERE district_code IS NOT NULL;
