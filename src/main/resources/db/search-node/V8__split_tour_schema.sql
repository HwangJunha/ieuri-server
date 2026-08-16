-- 검색 노드를 `tour` 스키마로 정렬한다(SPEC-0023 R7).
--
-- **이 경로는 검색 노드(ieuri-search) 전용이다.** 메인 DB는 db/migration의 V61이 옮긴다.
--
-- 복제 대상 6개가 전부 tourism 소유라 이 노드에는 `tour` 하나만 만든다. auth·community·
-- category·batch는 여기 존재할 이유가 없다 — 다른 컨텍스트가 검색을 필요로 하면 그때
-- 복제를 추가한다.
--
-- **원본과 스키마 이름이 같아야 한다.** 논리 복제는 발행자의 (스키마, 테이블)을 구독자의
-- 같은 이름에 적용하므로, 원본이 `tour.spot`이 되면 구독자도 `tour.spot`이어야 행이 도착한다.
-- 이름이 어긋나면 복제가 조용히 멈추고, 앱은 정상으로 보이는데 검색 결과만 낡아간다.
--
-- **BM25 인덱스는 테이블을 따라 이동한다** — 인덱스는 테이블 OID에 매여 있어 재생성하지
-- 않는다. 재생성은 11만 행 재색인이라 피할 수 있으면 피하는 편이 좋다.

CREATE SCHEMA IF NOT EXISTS tour;

-- **이 파일은 재적용에 견뎌야 한다.** V3 헤더와 같은 이유로(부분 적용된 노드가 실재한다)
-- 이미 옮겨진 테이블을 다시 옮기려 하면 실패하므로 존재 여부를 확인하고 옮긴다.
DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'spot',
        'spot_festival',
        'spot_reaction',
        'spot_catalog',
        'spot_catalog_translation',
        'spot_catalog_festival'
    ] LOOP
        IF EXISTS (
            SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = t
        ) THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA tour', t);
        END IF;
    END LOOP;
END $$;

-- pg_search(paradedb)와 PostGIS(public)는 옮기지 않는다 — 확장은 CNPG가 소유하며,
-- `tour`의 BM25 인덱스와 geography 컬럼은 search_path로 그 확장들을 찾는다.
