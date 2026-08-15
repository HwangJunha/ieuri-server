-- 번역 행에 원문 보유 플래그를 둔다(SPEC-0019 후속).
--
-- **그 언어의 `spot` 원본이 있는가.** 목록 정렬의 1순위다 — 원문이 있는 장소를 먼저
-- 보여주고, 번역으로 채운 장소는 그것이 소진된 뒤에 나온다.
--
-- 격차 해소가 이 스펙의 목적이지만 번역본이 원문보다 앞에 나올 이유는 없다. 외국어
-- 사용자는 원문 21,000건을 먼저 보고, 그다음 번역 33,000건을 만난다(실측).
--
-- `visible`과 같은 이유로 번역 행에 둔다 — 검색이 평면 질의라 `spot`을 조인할 수 없고,
-- 조인하면 BM25 인덱스 푸시다운이 깨진다. 판정 대상만 다르다: `visible`은 카탈로그에
-- 원본이 하나라도 있는가, 이쪽은 **그 언어의** 원본이 있는가다.
ALTER TABLE spot_catalog_translation ADD COLUMN has_origin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE spot_catalog_translation t
SET has_origin = EXISTS (
    SELECT 1 FROM spot s WHERE s.catalog_id = t.catalog_id AND s.language = t.language
);

-- 정렬이 `has_origin DESC`로 시작하므로 그 순서로 만든다. 언어를 앞에 두는 이유는 조회가
-- 언제나 언어로 먼저 좁힌 뒤 이 순서를 쓰기 때문이다.
CREATE INDEX idx_spot_catalog_translation_origin
    ON spot_catalog_translation (language, has_origin DESC);
