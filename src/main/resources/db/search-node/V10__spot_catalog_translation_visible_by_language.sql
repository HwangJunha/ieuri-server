-- SPEC-0029 · 검색 노드에서도 `has_origin`을 걷어낸다.
--
-- **메인 V71과 컬럼 구성이 일치해야 한다** — 발행 측에 없는 컬럼이 구독 측에 남아 있으면
-- 복제가 조용히 멈춘다. `visible`의 의미가 언어별로 바뀌는 것은 값 갱신이라 복제로 따라오고,
-- 여기서는 컬럼 삭제만 맞춘다.
DROP INDEX IF EXISTS tour.idx_spot_catalog_translation_origin;

ALTER TABLE tour.spot_catalog_translation DROP COLUMN IF EXISTS has_origin;
