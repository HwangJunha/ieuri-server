-- SPEC-0013 · spot 제목·주소 BM25 전문 검색 인덱스 (R1-R9)
--
-- **이 경로는 검색 노드(ieuri-search) 전용이다. 메인 DB에 적용하면 안 된다.**
-- BM25 인덱스 생성은 WAL에 pg_search 커스텀 레코드를 남기는데, pg_search 커뮤니티
-- 에디션은 standby가 그 레코드를 재생하는 순간 FATAL로 죽인다. 실제로 한 번 발생해
-- replica 2개의 PVC를 지우고 복구했다 — 그것이 검색 노드를 분리한 이유다.
--
-- 확장 활성화(pg_search)는 이 파일이 하지 않는다 — ieuri-manifest(CNPG 오퍼레이터)가 소유한다.
-- 확장이 없으면 이 마이그레이션은 부팅 시 실패한다. 의도된 fail-fast다.

-- **한 테이블에 BM25 인덱스는 하나만 만들 수 있다**(pg_search 제약:
-- `a relation may only have one ParadeDB index`). 그래서 언어별로 인덱스를 나누지 못하고,
-- **하나의 인덱스 안에 언어별 토크나이저 필드를 나란히 둔다.**
--
-- 같은 컬럼에 토크나이저를 여러 개 걸 때는 별칭(`alias=`)으로 구분하며, 조회는
-- `title::pdb.alias('title_ja') @@@ ...` 형태로 그 필드를 지목한다. 별칭 없는 것이
-- 기본 필드이고, 여기서는 **한국어를 기본으로 둔다** — spot 11만 행 중 KO가 54,040행으로
-- 가장 많고, 기본 필드는 질의에서 별칭 없이 쓸 수 있어 가장 흔한 경로가 짧아진다.
--
-- 조회는 여기에 더해 `language = ?`를 함께 걸어야 한다. 인덱스가 언어를 가르지 않으므로
-- **언어 격리는 전적으로 그 조건이 담당한다**(AC-2.3) — 빠뜨리면 다른 언어 행이 섞인다.
--
-- key_field는 id다 — pdb.score(id)로 BM25 점수를 얻고 커서의 tie-breaker로도 쓴다.
-- address는 nullable이라 coalesce로 빈 문자열을 준다. 색인 대상에서 빠지면 주소만 일치하는
-- spot을 찾지 못한다(AC-1.4).
CREATE INDEX idx_spot_bm25 ON spot
USING bm25 (
    id,
    -- 한국어(KoDic). 별칭 없는 기본 필드다. 이 스펙이 pg_search를 택한 결정적 이유이며,
    -- 내장 FTS·pg_trgm 어느 쪽도 "경복궁 야간개장"을 "경복궁"으로 찾지 못한다.
    (title::pdb.lindera(korean)),
    (title::pdb.lindera(japanese, 'alias=title_ja')),   -- IPADIC
    (title::pdb.lindera(chinese, 'alias=title_zh')),    -- CC-CEDICT
    (title::pdb.simple('alias=title_en')),              -- 공백 분리 + 어간
    -- **주소는 형태소가 아니라 n-gram이다.** 주소는 도로명·지명 같은 고유명사 덩어리라
    -- 사전 기반 분석이 잘 맞지 않는다. 실측 예: 주소 안의 "두산로 71"은 `{두산, 로, 71}`로
    -- 잘리는데 질의 "두산로"는 문맥이 없어 `{두, 산로}`로 잘려 **겹치는 토큰이 없어진다**.
    -- 2~3글자 n-gram은 문맥에 의존하지 않아 이 어긋남이 생기지 않는다.
    --
    -- 언어별로 나누지 않는 이유도 같다 — n-gram은 사전을 쓰지 않아 언어와 무관하다.
    -- 언어 격리는 조회의 `language = ?` 조건이 전담한다.
    (coalesce(address, '')::pdb.ngram(2, 3))
)
WITH (key_field = 'id');
