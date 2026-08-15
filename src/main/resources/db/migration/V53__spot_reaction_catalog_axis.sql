-- 좋아요의 참조 축을 spot에서 카탈로그로 옮긴다(SPEC-0019 R-10).
--
-- 지금까지는 `UNIQUE(member_id, spot_id)`이고 `spot.id`가 언어별이라, 일본어로 누른 좋아요가
-- 영어로 바꾸면 사라졌다. SPEC-0002가 "알려진 한계"로 남기고 별도 스펙이 필요하다고 적은
-- 문제이며, 카탈로그가 그것을 해소한다.
--
-- **`spot_id` 컬럼을 지우고 `catalog_id`로 대체한다.** 참조 축이 옮겨 가면 쓰이지 않는
-- 컬럼이고, 남겨 두면 "어느 쪽을 봐야 하는가"가 매번 발생하는 부채가 된다.
--
-- **운영 데이터가 없다는 전제에서만 이렇게 쓴다**(사용자 확인 2026-08-14). 좋아요는 사용자가
-- 만든 데이터라 복구할 원본이 없으므로, 운영 데이터가 생긴 뒤에는
-- `컬럼 추가 → 배정 배치 → 백필 → dedupe → 조이기`를 배포 두 번에 나눠야 한다(V19의 선례).

-- 옛 축의 컬럼·제약·인덱스를 걷어낸다. 셋 다 spot_id를 선행 컬럼으로 써서 카탈로그 조회를
-- 전혀 커버하지 못한다. IF EXISTS를 쓰는 이유는 이 파일이 재작성되었기 때문이다 — 이전
-- 버전이 이미 일부를 적용한 환경에서도 같은 결과가 되어야 한다.
DROP INDEX IF EXISTS idx_spot_reaction_spot_id;
DROP INDEX IF EXISTS idx_spot_reaction_spot_reaction;
DROP INDEX IF EXISTS idx_spot_reaction_liked_list;
DROP INDEX IF EXISTS idx_spot_reaction_catalog_id;
DROP INDEX IF EXISTS idx_spot_reaction_catalog_reaction;

ALTER TABLE spot_reaction DROP CONSTRAINT IF EXISTS fk_spot_reaction_spot;
ALTER TABLE spot_reaction DROP CONSTRAINT IF EXISTS uq_spot_reaction;
ALTER TABLE spot_reaction DROP COLUMN IF EXISTS spot_id;

-- **기존 행을 지운다.** 참조 축을 옮기는데 백필할 원본(spot_id)이 방금 사라졌으므로 남은
-- 행은 가리킬 장소가 없다. 운영 데이터가 없다는 전제에서만 성립하며(위 헤더 참조), 이
-- DELETE가 없으면 행이 하나라도 있는 환경에서 아래 NOT NULL 추가가 실패해 기동이 막힌다.
DELETE FROM spot_reaction;

ALTER TABLE spot_reaction ADD COLUMN catalog_id BIGINT NOT NULL;

-- 좋아요의 단위가 spot에서 장소로 바뀐다. 이것이 이 스펙의 핵심 교정이다.
ALTER TABLE spot_reaction ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, catalog_id);

-- FK를 카탈로그로 옮긴다.
--
-- **옛 FK가 `spot(id) ON DELETE CASCADE`라 SPEC-0004 R-5의 정리 배치가 spot 행을 지우면
-- 좋아요가 함께 사라졌다.** spot.id가 언어별인 구조에서는 그럴듯해 보이지만 카탈로그
-- 축에서는 명백한 유실이다. 카탈로그는 장소 단위라 동기화로 사라졌다 다시 생기지 않는다.
ALTER TABLE spot_reaction
    ADD CONSTRAINT fk_spot_reaction_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE;

-- **FK 자식 쪽 인덱스가 없으면 부모 1행 삭제마다 자식 전체 스캔이 발생한다**(V19가 같은
-- 이유로 추가했다).
CREATE INDEX idx_spot_reaction_catalog_id ON spot_reaction (catalog_id);

-- 인기 spot 집계(SPEC-0013 R18)가 타던 (spot_id, reaction)의 카탈로그판.
CREATE INDEX idx_spot_reaction_catalog_reaction ON spot_reaction (catalog_id, reaction);

-- 좋아요 목록의 keyset 정렬 (updated_at DESC, catalog_id DESC).
CREATE INDEX idx_spot_reaction_liked_list
    ON spot_reaction (member_id, reaction, updated_at DESC, catalog_id DESC);
