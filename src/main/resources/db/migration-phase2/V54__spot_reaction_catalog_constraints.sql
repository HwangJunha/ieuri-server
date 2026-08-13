-- 좋아요의 참조 축을 카탈로그로 옮긴다 — 2단계: 제약 교체(SPEC-0019 §4.1의 4~7).
--
-- **이 파일은 부팅 시 자동 적용되지 않는다** — `db/migration-phase2`는 flyway locations에
-- 없다. 운영자가 배정·dedupe 배치를 완주시킨 뒤 명시적으로 적용한다(이 폴더의 README).
--
-- **백필과 dedupe가 끝난 뒤에만 성공한다.** 순서가 뒤집히면 아래 NOT NULL이나 ADD CONSTRAINT가
-- 실패한다 — 백필되지 않은 null이 남아 있거나, 한 회원이 같은 장소를 여러 언어로 좋아요한
-- 행이 (member_id, catalog_id) 중복으로 남아 있기 때문이다.
-- 실패하는 편이 옳다. 조용히 넘기면 어느 좋아요가 유실됐는지 알 수 없다.

-- ---------------------------------------------------------------------------
-- 4. NOT NULL로 조이기
--
-- 백필되지 못한 행이 있으면 여기서 실패한다. 그것이 의도다(V53 주석 참조).
-- ---------------------------------------------------------------------------
ALTER TABLE spot_reaction ALTER COLUMN catalog_id SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 5. UNIQUE 교체 — 좋아요의 단위가 spot에서 장소로 바뀐다.
--
-- 이것이 이 스펙의 핵심 교정이다. 지금까지는 일본어로 누른 좋아요가 영어로 바꾸면 사라졌다
-- (SPEC-0002가 "알려진 한계"로 남기고 별도 스펙이 필요하다고 적은 문제다).
-- ---------------------------------------------------------------------------
ALTER TABLE spot_reaction DROP CONSTRAINT uq_spot_reaction;
ALTER TABLE spot_reaction ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, catalog_id);

-- ---------------------------------------------------------------------------
-- 6. FK 이전 — spot(id) → spot_catalog(id)
--
-- **현재 FK가 ON DELETE CASCADE라 SPEC-0004 R-5의 정리 배치가 spot 행을 지우면 좋아요가 함께
-- 사라진다.** spot.id가 언어별인 지금은 그럴듯해 보이지만 카탈로그 축에서는 명백한 유실이다.
--
-- 카탈로그는 장소 단위라 TourAPI 동기화로 사라졌다 다시 생기는 일이 없다. CASCADE는 그대로
-- 두되 그 대상이 훨씬 안정적인 부모로 바뀐다.
--
-- spot_id 컬럼은 **남긴다.** 조회가 카탈로그로 요청 언어의 spot을 찾으므로 참조로는 쓰지
-- 않지만, 이관이 잘못됐을 때 되돌릴 유일한 원본이다. 좋아요는 복구할 곳이 없어 지우지 않는다.
-- ---------------------------------------------------------------------------
ALTER TABLE spot_reaction DROP CONSTRAINT fk_spot_reaction_spot;
ALTER TABLE spot_reaction
    ADD CONSTRAINT fk_spot_reaction_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 7. 인덱스 교체
--
-- 세 인덱스가 전부 spot_id를 선행 컬럼으로 쓰고 있었다. 조회가 catalog_id로 바뀌면 그대로는
-- 아무것도 커버하지 못한다.
--
-- **FK 자식 쪽 인덱스가 없으면 부모 1행 삭제마다 자식 전체 스캔이 발생한다**(V19가 같은 이유로
-- 추가했다). 새 FK가 catalog_id를 타므로 그 컬럼에 인덱스가 필요하다.
-- ---------------------------------------------------------------------------
DROP INDEX IF EXISTS idx_spot_reaction_spot_id;
DROP INDEX IF EXISTS idx_spot_reaction_spot_reaction;
DROP INDEX IF EXISTS idx_spot_reaction_liked_list;

-- CASCADE 삭제 경로.
CREATE INDEX idx_spot_reaction_catalog_id ON spot_reaction (catalog_id);
-- 인기 spot 집계(SPEC-0013 R18)가 타던 (spot_id, reaction)의 카탈로그판.
CREATE INDEX idx_spot_reaction_catalog_reaction ON spot_reaction (catalog_id, reaction);
-- 좋아요 목록의 keyset 정렬 (updated_at DESC, catalog_id DESC).
CREATE INDEX idx_spot_reaction_liked_list
    ON spot_reaction (member_id, reaction, updated_at DESC, catalog_id DESC);
