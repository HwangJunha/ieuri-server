-- 번역 테이블에 좌표를 복사한다(SPEC-0019 · 검색 노드 작업 지시서 §2~§3).
--
-- **검색이 번역 테이블 단독으로 돌아야 하기 때문이다.** pg_search는 `@@@`·`pdb.score()`·
-- `ORDER BY`·`LIMIT`이 전부 같은 질의 레벨에 있어야 인덱스 푸시다운이 일어나고, 조인·서브쿼리·
-- CTE가 끼면 `query is incompatible with @@@ operator`로 실패한다. BM25 인덱스는 번역 테이블에
-- 걸리는데(search-node V4) 거리 필터·정렬에 필요한 좌표는 `spot_catalog`에 있으므로, 조인 없이
-- 쓰려면 좌표가 번역 행에 함께 있어야 한다.
--
-- **2단계로 거를 수는 없다.** 전국에서 관련도 상위 N개를 뽑고 나중에 반경으로 거르면 대부분이
-- 반경 밖이라 결과가 비어 보인다(실측: 기본 반경 20km 안에 spot이 12,000개가 넘는다 — V34).
--
-- 중복 저장이지만 `community_post_spot`이 좌표를 복사해 둔 것(V34)과 같은 판단이며 선례가 있다.
-- **다만 성격이 다르다**: V34의 사본은 "글이 쓰인 시점의 위치"라 이후 갱신을 따라가지 않는 것이
-- 의도였다. 여기 사본은 **카탈로그의 현재 좌표를 따라가야 한다** — 검색 결과의 위치가 지도·
-- 목록과 달라지면 안 된다. 갱신은 배치가 제목·주소와 같은 경로로 함께 수행한다.
--
-- 컬럼은 nullable이다. `spot_catalog.map_x/map_y`가 nullable이고(상류 결손 317건) 사본이 원본보다
-- 엄격할 수 없다. 좌표가 없는 행은 `ST_DWithin`이 참을 돌려주지 않아 검색 결과에서 빠진다 —
-- 위치를 줄 수 없는 장소를 거리순 목록에 넣을 수는 없다.

ALTER TABLE spot_catalog_translation
    ADD COLUMN map_x DOUBLE PRECISION,
    ADD COLUMN map_y DOUBLE PRECISION,
    ADD COLUMN location geography(Point, 4326);

-- 기존 행을 카탈로그에서 채운다. 이 시점 이후의 갱신은 배치가 담당한다.
UPDATE spot_catalog_translation t
SET map_x = c.map_x, map_y = c.map_y
FROM spot_catalog c
WHERE c.id = t.catalog_id;

-- 좌표를 map_x/map_y에서 파생시킨다(spot·spot_catalog·community_post_spot과 같은 방식).
-- 애플리케이션이 파생을 관리하면 둘이 어긋날 수 있고, JPA 엔티티가 PostGIS 타입을 알 필요도 없다.
--
-- **ST_MakePoint는 (경도, 위도) 순서다** — 뒤집으면 조용히 틀린다.
-- 좌표가 없으면 location도 null이다 — spot_catalog의 트리거와 같은 판정이다.
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

-- 위 UPDATE는 트리거 생성 전에 돌았으므로 location이 비어 있다. 한 번 더 파생시킨다.
UPDATE spot_catalog_translation
SET location = ST_MakePoint(map_x, map_y)::geography
WHERE map_x IS NOT NULL AND map_y IS NOT NULL;

-- 검색의 거리 필터·정렬이 타는 인덱스.
CREATE INDEX idx_spot_catalog_translation_location
    ON spot_catalog_translation USING GIST (location);
