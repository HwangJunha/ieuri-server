-- SPEC-0019 후속 · 검색 노드의 원문 보유 플래그.
--
-- **이름과 타입은 메인 V56과 일치해야 한다** — 어긋나면 복제가 조용히 멈춘다.
-- **DEFAULT를 두어** 컬럼 추가 이전 시점의 행이 복제로 와도 값이 채워진다.
ALTER TABLE spot_catalog_translation ADD COLUMN IF NOT EXISTS has_origin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_spot_catalog_translation_origin
    ON spot_catalog_translation (language, has_origin DESC);
