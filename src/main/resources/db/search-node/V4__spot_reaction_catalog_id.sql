-- SPEC-0019 · 검색 노드의 spot_reaction.catalog_id
--
-- **이 경로는 검색 노드(ieuri-search) 전용이다.** 메인 DB는 db/migration이 소유한다.
--
-- 인기 spot 조회(PopularSpotQueryJdbcAdapter)가 이 노드를 보며, 카탈로그 단위 집계로 바뀌므로
-- 여기에도 컬럼이 있어야 한다. 동시에 논리 복제가 이어지려면 **메인 V53과 컬럼 정의가 문자
-- 단위로 같아야 한다**: BIGINT, nullable, DEFAULT 없음.
--
-- **NOT NULL로 조이지 않는다.** 메인 V54가 조이더라도 여기서는 복제된 행을 받는 그릇일 뿐이고,
-- 구독자 쪽 제약이 더 엄격하면 백필 중간 상태의 행이 거부되어 복제가 멈춘다.
ALTER TABLE spot_reaction ADD COLUMN catalog_id BIGINT;

-- 인기 집계가 타는 인덱스. 메인의 idx_spot_reaction_catalog_reaction과 같은 목적이다.
-- (spot_id, reaction) 인덱스는 남긴다 — 복제 대상이 아니고, 되돌릴 때 필요하다.
CREATE INDEX idx_spot_reaction_catalog_reaction ON spot_reaction (catalog_id, reaction);
