-- SPEC-0035 S2 — 장소 임베딩(취향 요인의 재료).
--
-- 번역 행(언어별 제목·주소) 하나에 벡터 하나. 회원이 좋아요·싫어요한 장소 벡터의 평균과 후보의
-- 코사인을 SQL 한 번으로 구하기 위해 반응·좌표와 같은 DB에 둔다.
--
-- `vector` 타입은 public의 pgvector 확장이다. **확장은 여기서 만들지 않는다**(POL-034) — CNPG
-- 매니페스트(`database.extensions`)가 소유한다. 차원 1024는 voyage-4 기본값이며, 모델의 차원이
-- 바뀌면 컬럼 타입도 새 마이그레이션으로 바꿔야 한다.
--
-- JPA 엔티티가 없다 — `vector`를 JPA가 표현하지 못하므로 이 테이블을 건드리는 질의는 전부
-- 전용 JDBC 어댑터에 있다(SpotEmbeddingUpsertJdbcAdapter·TasteSimilarityQueryJdbcAdapter).
CREATE TABLE tour.spot_embedding (
    translation_id bigint PRIMARY KEY
        REFERENCES tour.spot_catalog_translation (id) ON DELETE CASCADE,
    -- 벡터를 만든 모델. 설정과 다르면 대상으로 다시 잡혀 교체된다(초기 적재·모델 교체·증분이 한 규칙).
    model varchar(50) NOT NULL,
    -- md5(title || '|' || category || '|' || coalesce(address, '')). 제목·분류·주소가 바뀌면 대상이다.
    text_hash char(32) NOT NULL,
    embedding vector(1024) NOT NULL,
    updated_at timestamp with time zone NOT NULL DEFAULT now()
);
