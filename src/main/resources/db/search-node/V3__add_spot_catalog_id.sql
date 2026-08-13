-- SPEC-0019 · 검색 노드의 spot.catalog_id
--
-- **이 경로는 검색 노드(ieuri-search) 전용이다.** 메인 DB는 db/migration이 소유한다.
--
-- 논리 복제는 행만 옮기고 테이블 구조는 옮기지 않는다. 메인 spot에 컬럼이 생기면
-- **구독자에도 같은 컬럼이 있어야 복제가 이어진다** — 어긋나면 복제가 조용히 멈추고
-- 앱은 정상으로 보이는데 검색 결과만 낡아간다(V1 헤더의 경고).
--
-- 메인 V52와 **타입·널 허용성·기본값이 문자 단위로 같아야 한다**:
-- catalog_id는 BIGINT nullable, title_key는 VARCHAR(255) nullable, 둘 다 DEFAULT 없음.
--
-- **FK는 걸지 않는다.** spot_catalog 테이블 자체를 여기 만들지 않기 때문이다. 검색 노드가
-- 카탈로그를 조회에 쓰지 않으므로(BM25 인덱스는 언어별 부분 필드이고 카탈로그 단위 검색은
-- SPEC-0019의 범위 밖이다) 이 컬럼은 **복제 정합성을 위해서만 존재한다.**
-- 그릇 없는 FK를 걸면 복제된 행이 전부 거부된다.
--
-- 인덱스도 만들지 않는다 — 여기서 이 컬럼들로 조회하지 않는다.
ALTER TABLE spot ADD COLUMN catalog_id BIGINT;
ALTER TABLE spot ADD COLUMN title_key VARCHAR(255);
