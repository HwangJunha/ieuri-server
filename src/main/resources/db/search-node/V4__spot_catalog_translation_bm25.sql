-- SPEC-0019 · 번역 테이블의 BM25 인덱스.
--
-- **이 경로는 검색 노드 전용이다. 메인 DB에 적용하면 안 된다.** BM25 인덱스 생성은 WAL에
-- pg_search 커스텀 레코드를 남기는데, 커뮤니티 에디션은 standby가 그 레코드를 재생하는 순간
-- FATAL로 죽인다. 실제로 한 번 발생해 replica 2개의 PVC를 지우고 복구했다(V2 헤더).
--
-- **왜 spot이 아니라 번역 테이블인가.**
--
-- 검색이 `WHERE spot.language = ?`로 도는 한, 그 언어의 spot 행이 없는 장소는 영원히 검색되지
-- 않는다. 그런데 이 스펙이 번역으로 채우는 것이 바로 "그 언어의 spot 행이 없는" 카탈로그다 —
-- 목록에는 나오는데 검색에는 안 나오는 상태가 된다.
--
-- 번역 테이블에 인덱스를 걸면 그 어긋남이 사라진다. **조인으로는 풀 수 없다** — pg_search는
-- `@@@`·`pdb.score()`·`ORDER BY`·`LIMIT`이 전부 같은 질의 레벨에 있어야 인덱스 푸시다운이
-- 일어나고, 조인·서브쿼리·CTE가 끼면 `query is incompatible with @@@ operator`로 실패한다.
--
-- 메인 spot에 언어별 제목 컬럼을 만드는 안도 검토했으나 철회했다 — 복제가 구조를 옮기지 않아
-- 메인에도 컬럼이 필요하고, 그러면 번역이 두 곳에 중복 저장되어 갱신 시 어긋난다.
--
-- **테이블당 BM25 인덱스는 하나뿐이다**(`a relation may only have one ParadeDB index`).
-- 번역 테이블은 새 테이블이라 이 제약에 걸리지 않는다. spot의 기존 인덱스(idx_spot_bm25)는
-- 전환이 검증될 때까지 그대로 둔다 — 디스크 외의 비용은 없다.

CREATE INDEX idx_spot_catalog_translation_bm25 ON spot_catalog_translation
USING bm25 (
    id,
    -- 한국어(KoDic). 별칭 없는 기본 필드다 — KO가 가장 흔한 경로라 질의에서 별칭 없이 쓴다.
    (title::pdb.lindera(korean)),
    (title::pdb.lindera(japanese, 'alias=title_ja')),   -- IPADIC
    (title::pdb.lindera(chinese, 'alias=title_zh')),    -- CC-CEDICT
    (title::pdb.simple('alias=title_en')),              -- 공백 분리 + 어간
    -- **주소는 형태소가 아니라 n-gram이다.** 도로명·지명은 고유명사 덩어리라 사전 기반 분석이
    -- 잘 맞지 않는다. "두산로 71"은 `{두산, 로, 71}`로 잘리는데 질의 "두산로"는 문맥이 없어
    -- `{두, 산로}`로 잘려 겹치는 토큰이 없어진다. 2~3글자 n-gram은 이 어긋남이 생기지 않는다.
    --
    -- 언어별로 나누지 않는 이유도 같다 — n-gram은 사전을 쓰지 않아 언어와 무관하다.
    -- 언어 격리는 질의의 `language = ?` 조건이 전담한다.
    (coalesce(address, '')::pdb.ngram(2, 3))
)
WITH (key_field = 'id');
