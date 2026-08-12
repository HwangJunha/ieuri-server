-- `sigungu` → `district` 이름 변경(SPEC-0015).
--
-- **한국어 음차는 스키마 이름으로 쓰지 않는다.** 다른 테이블은 전부 영어 보통명사인데
-- (`spot`·`member`·`congestion_area`) 이 하나만 발음 표기였고, 정작 컬럼은 영어라
-- `sigungu.province_code`처럼 한 줄에서 두 언어를 오갔다. 다국어 서비스를 만드는
-- 저장소의 스키마를 한국인만 읽을 수 있는 것도 앞뒤가 맞지 않는다.
--
-- `district`를 고른 이유: 행정 하위구역의 표준 번역이고 `province_code`와 층위가 맞는다.
-- `region`은 시도·권역과 겹쳐 모호하고, `municipality`는 길다.
--
-- KTO 테이블의 참조 컬럼도 함께 바꾼다 — 가리키는 테이블 이름이 사라지면 컬럼명만 남아
-- 무엇을 참조하는지 알 수 없게 된다.
--
-- **적재 데이터는 건드리지 않는다.** 이름만 바뀌므로 행·값·인덱스 내용은 그대로다.

ALTER TABLE sigungu RENAME TO district;

-- 인덱스 이름도 실체에 맞춘다. 어긋나면 EXPLAIN을 읽을 때 혼란이 된다.
ALTER INDEX idx_sigungu_boundary RENAME TO idx_district_boundary;
ALTER INDEX idx_sigungu_representative_location RENAME TO idx_district_representative_location;

ALTER TABLE kto_visitor_count       RENAME COLUMN sigungu_code TO district_code;
ALTER TABLE kto_concentration_rate  RENAME COLUMN sigungu_code TO district_code;
ALTER TABLE kto_stay_intensity      RENAME COLUMN sigungu_code TO district_code;
ALTER TABLE kto_spending_intensity  RENAME COLUMN sigungu_code TO district_code;

-- 자연키 UNIQUE는 이름에 `sigungu`가 없어 그대로 둔다.
-- 조회용 인덱스만 대상 컬럼이 바뀐 것을 이름에 반영한다.
ALTER INDEX idx_kto_visitor_count_latest       RENAME TO idx_kto_visitor_count_district_date;
ALTER INDEX idx_kto_concentration_rate_latest  RENAME TO idx_kto_concentration_rate_district_date;
ALTER INDEX idx_kto_stay_intensity_latest      RENAME TO idx_kto_stay_intensity_district_month;
ALTER INDEX idx_kto_spending_intensity_latest  RENAME TO idx_kto_spending_intensity_district_month;

COMMENT ON TABLE district IS
    '시군구 252건. 행정표준코드 5자리가 PK이며 KTO 4종 API의 공통 키다(SPEC-0015).';
