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

-- ── 원본 생존 플래그 ────────────────────────────────────────────────────────
--
-- **원본이 사라진 카탈로그를 조회에서 감추기 위한 값이다.** 상류에서 장소가 없어지면
-- 동기화가 `spot` 행을 지우지만 카탈로그와 번역은 남아, 목록·지도·검색에는 보이는데
-- 상세를 열면 실패하는 장소가 된다.
--
-- **번역 행을 지우지 않고 플래그로 감춘다.** 지우면 배정 배치의 KO 제목 맵이 그 카탈로그를
-- 못 찾아, 상류가 장소를 다시 줬을 때 새 카탈로그가 생기고 좋아요가 달린 옛 카탈로그는
-- 영원히 고아로 남는다 — 좋아요를 지키려고 카탈로그를 남긴 판단이 무의미해진다.
--
-- 카탈로그가 아니라 번역 행에 두는 이유는 **모든 조회가 이 테이블을 거치기 때문이다.**
-- 검색은 평면 질의라 `spot_catalog`를 조인할 수 없어, 여기 있어야 조건을 걸 수 있다.
ALTER TABLE spot_catalog_translation ADD COLUMN visible BOOLEAN NOT NULL DEFAULT TRUE;

-- 조회는 대부분 `visible AND language = ?` 형태다. 선행 컬럼을 visible로 두면 감춰진
-- 행이 인덱스 앞단에서 걸러진다.
CREATE INDEX idx_spot_catalog_translation_visible
    ON spot_catalog_translation (visible, language);

-- ── 원문 보유 플래그 ────────────────────────────────────────────────────────
--
-- **그 언어의 `spot` 원본이 있는가.** 목록 정렬의 1순위다 — 원문이 있는 장소를 먼저
-- 보여주고, 번역으로 채운 장소는 뒤로 민다.
--
-- 격차 해소가 이 스펙의 목적이지만 번역본이 원문보다 앞에 나올 이유는 없다. 외국어
-- 사용자는 원문 21,000건을 먼저 보고, 그것이 소진된 뒤에야 번역 33,000건을 만난다.
--
-- `visible`과 같은 이유로 번역 행에 둔다 — 검색이 평면 질의라 `spot`을 조인할 수 없고,
-- 조인하면 BM25 인덱스 푸시다운이 깨진다.
ALTER TABLE spot_catalog_translation ADD COLUMN has_origin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE spot_catalog_translation t
SET has_origin = EXISTS (
    SELECT 1 FROM spot s WHERE s.catalog_id = t.catalog_id AND s.language = t.language
);

-- 정렬이 `has_origin DESC`로 시작하므로 그 순서로 인덱스를 만든다. 언어를 뒤에 두는 이유는
-- 조회가 언제나 언어로 먼저 좁힌 뒤 이 순서를 쓰기 때문이다.
CREATE INDEX idx_spot_catalog_translation_origin
    ON spot_catalog_translation (language, has_origin DESC);
