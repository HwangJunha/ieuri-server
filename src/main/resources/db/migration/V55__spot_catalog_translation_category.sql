-- 번역 행에 분류 사본을 둔다(SPEC-0019 후속).
--
-- **검색의 카테고리 필터가 죽어 있었다.** 클라이언트가 `category`를 보내도 서버가 받아만 놓고
-- 조건으로 쓰지 못했다 — 분류는 `spot_catalog`에 있는데 검색은 `spot_catalog_translation`
-- 단독으로 돌아야 하기 때문이다. pg_search가 평면 질의를 요구해(`@@@`·`pdb.score()`·
-- `ORDER BY`·`LIMIT`이 모두 같은 레벨) 조인이 하나라도 끼면 인덱스 푸시다운이 깨진다.
--
-- 좌표를 이 테이블에 복사한 것과 **같은 판단이자 같은 이유다**(V52). 사본이므로 카탈로그의
-- 분류가 바뀌면 함께 갱신해야 한다 — 배정 배치의 번역 upsert가 그 일을 한다.
ALTER TABLE spot_catalog_translation ADD COLUMN category VARCHAR(30);

-- 기존 행 백필. 카탈로그가 이미 확정한 값을 그대로 복사한다.
UPDATE spot_catalog_translation t
SET category = c.category
FROM spot_catalog c
WHERE c.id = t.catalog_id;

ALTER TABLE spot_catalog_translation ALTER COLUMN category SET NOT NULL;

-- **부분 인덱스가 아니라 복합 인덱스다.** 검색은 언어로 먼저 좁힌 뒤 분류를 거르므로
-- 선행 컬럼이 language여야 한다. BM25 인덱스와 별개로, 분류만 좁히는 질의를 받친다.
CREATE INDEX idx_spot_catalog_translation_language_category
    ON spot_catalog_translation (language, category);
