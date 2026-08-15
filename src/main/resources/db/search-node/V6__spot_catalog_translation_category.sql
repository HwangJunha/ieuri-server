-- SPEC-0019 후속 · 검색 노드의 번역 행 분류 사본.
--
-- **이름과 타입은 메인 V55와 일치해야 한다** — 어긋나면 복제가 조용히 멈추고 앱은 정상으로
-- 보이는데 검색 결과만 낡아간다.
--
-- **제약은 반대다 — 구독자가 더 느슨해야 한다.** V1·V3 헤더의 "문자 단위 일치"는 이름과
-- 타입에 대한 말이며, NOT NULL·CHECK까지 똑같이 걸면 컬럼 추가 이전 시점의 행이 복제로
-- 올 때 거부되어 복제가 막힌다. 값의 정합성은 발행자가 이미 보장하므로 구독자는 그릇
-- 역할만 한다(선례: `spot_reaction.spot_id`로 복제가 3.5GB 밀린 채 멈췄다).
--
-- **NOT NULL을 걸지 않는다.** 구독자 제약이 발행자보다 엄격하면 복제가 막힌다 — 컬럼이
-- 추가되기 전 시점의 행이 오면 그 값이 없다. 값의 정합성은 발행자가 이미 보장한다.
-- (선례: `spot_reaction.spot_id`를 구독자에서 먼저 지웠다가 복제가 3.5GB 밀린 채 멈췄다.)
ALTER TABLE spot_catalog_translation ADD COLUMN IF NOT EXISTS category VARCHAR(30);

-- 검색이 언어로 좁힌 뒤 분류를 거른다. 선행 컬럼이 language여야 한다.
CREATE INDEX IF NOT EXISTS idx_spot_catalog_translation_language_category
    ON spot_catalog_translation (language, category);

-- 원본 생존 플래그. **DEFAULT를 두어 컬럼 추가 이전 시점의 행이 와도 값이 채워진다.**
ALTER TABLE spot_catalog_translation ADD COLUMN IF NOT EXISTS visible BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_spot_catalog_translation_visible
    ON spot_catalog_translation (visible, language);
