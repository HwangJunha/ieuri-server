-- 번역 테이블에 좌표를 둔다 — 검색 노드의 BM25가 거리 필터를 함께 걸기 위해서다.
--
-- **pg_search는 조인을 받지 못한다.** `@@@`·`pdb.score()`·`ORDER BY`·`LIMIT`이 전부 같은
-- 질의 레벨에 있어야 인덱스 푸시다운이 일어난다(`db/search-node/V2` 헤더). 그래서 검색을
-- 번역 테이블 단독 평면 질의로 써야 하고, 그러려면 그 테이블이 좌표를 갖고 있어야 한다.
--
-- 좌표를 1단계에서 못 걸면 전국에서 관련도 상위 N개를 뽑아 2단계에서 반경으로 거르게 되는데,
-- 반경 20km 안에 spot이 12,000개가 넘어(V34 실측) 대부분이 버려지고 결과가 비어 보인다.
--
-- **중복 저장은 의도한 것이다.** `community_post_spot`이 좌표를 복사해 둔 것(V34)과 같은
-- 판단이며, 원본(`spot`)이 갱신되면 배치가 함께 갱신한다 — 제목·주소와 같은 경로다.
-- 좌표는 언어와 무관하므로 한 카탈로그의 4개 언어 행이 같은 값을 갖는다(§3.6 실증).

ALTER TABLE spot_catalog_translation
    ADD COLUMN map_x DOUBLE PRECISION,
    ADD COLUMN map_y DOUBLE PRECISION,
    ADD COLUMN location geography(Point, 4326);

-- 기존 행 백필. 카탈로그의 대표 spot에서 가져온다 — 어느 언어 행이든 좌표는 같다.
UPDATE spot_catalog_translation t
SET map_x = s.map_x, map_y = s.map_y, location = s.location
FROM spot_catalog c
JOIN spot s ON s.id = c.representative_spot_id
WHERE c.id = t.catalog_id;

-- 좌표 유효 범위. spot 테이블과 같은 불변식을 여기서도 지킨다.
ALTER TABLE spot_catalog_translation
    ADD CONSTRAINT ck_sct_map_x CHECK (map_x IS NULL OR map_x BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_sct_map_y CHECK (map_y IS NULL OR map_y BETWEEN -90 AND 90);

-- 적재 시 location을 함께 쓰지 않아도 자동으로 채워지게 한다(spot·community_post_spot과 동일).
--
-- **ST_MakePoint는 (경도, 위도) 순서다** — 뒤집으면 조용히 틀린다.
CREATE OR REPLACE FUNCTION spot_catalog_translation_sync_location() RETURNS trigger AS $$
BEGIN
    NEW.location := CASE
        WHEN NEW.map_x IS NULL OR NEW.map_y IS NULL THEN NULL
        ELSE ST_MakePoint(NEW.map_x, NEW.map_y)::geography
    END;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_spot_catalog_translation_sync_location
    BEFORE INSERT OR UPDATE OF map_x, map_y ON spot_catalog_translation
    FOR EACH ROW EXECUTE FUNCTION spot_catalog_translation_sync_location();

-- 반경 조회·거리 정렬. 검색 노드에도 같은 인덱스가 필요하다(그쪽은 직접 생성 — 지시서 §4.1).
CREATE INDEX idx_sct_location ON spot_catalog_translation USING GIST (location);
