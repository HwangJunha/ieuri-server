-- 좋아요의 참조 축을 spot에서 카탈로그로 옮긴다(SPEC-0019 R-10).
--
-- 지금까지는 `UNIQUE(member_id, spot_id)`이고 `spot.id`가 언어별이라, 일본어로 누른 좋아요가
-- 영어로 바꾸면 사라졌다. SPEC-0002가 "알려진 한계"로 남기고 별도 스펙이 필요하다고 적은
-- 문제이며, 카탈로그가 그것을 해소한다.
--
-- **아직 배정되지 않은 좋아요는 지운다.** 이 마이그레이션이 도는 시점에는 배정 배치가 돌기
-- 전이라 `spot.catalog_id`가 전부 null이고, 따라서 백필로 채울 수 있는 행이 없다.
--
-- 운영 데이터가 없는 지금이라 가능한 선택이다(사용자 확인 2026-08-14 · 로컬 8건은 테스트
-- 데이터). **운영 데이터가 생긴 뒤였다면 이렇게 하면 안 된다** — 좋아요는 사용자가 만든
-- 데이터라 복구할 원본이 없으므로, 그때는 `컬럼 추가 → 배정 배치 → 백필 → dedupe → 조이기`를
-- 배포 두 번에 나눠야 한다(V19의 선례).

ALTER TABLE spot_reaction ADD COLUMN catalog_id BIGINT;

-- spot을 경유해 백필한다. 배정이 끝난 환경에서 재적용하면 이 한 줄로 이관이 끝난다.
UPDATE spot_reaction r
SET catalog_id = s.catalog_id
FROM spot s
WHERE s.id = r.spot_id;

-- 백필되지 못한 행을 지운다. 남겨 두면 아래 NOT NULL이 실패한다.
--
-- **지우는 편이 옳은 이유는 이 행들이 가리킬 장소가 없기 때문이다** — 카탈로그가 조회 축이라
-- catalog_id가 없는 좋아요는 어떤 목록에도 나타나지 못한다. 조용히 남기면 "기록은 있는데
-- 아무 데서도 안 보이는" 행이 된다.
DELETE FROM spot_reaction WHERE catalog_id IS NULL;

-- 같은 회원이 같은 장소를 여러 언어로 좋아요한 중복을 접는다. 남길 행은 spec R-10이 정한
-- 규칙 그대로다: `updated_at`이 최신인 것, 동률이면 `id`가 큰 것.
--
-- **애플리케이션 배치(reactionDedupeJob)와 같은 규칙이며 그쪽이 정본이다.** 여기 두는 이유는
-- 아래 UNIQUE가 중복을 만나면 실패하기 때문이고, 배치는 이관 후에도 재실행 가능한 형태로
-- 남는다(규칙이 결정적이라 두 경로가 같은 행을 남긴다).
DELETE FROM spot_reaction r
USING spot_reaction keep
WHERE r.member_id = keep.member_id
  AND r.catalog_id = keep.catalog_id
  AND (r.updated_at, r.id) < (keep.updated_at, keep.id);

ALTER TABLE spot_reaction ALTER COLUMN catalog_id SET NOT NULL;

-- 좋아요의 단위가 spot에서 장소로 바뀐다. 이것이 이 스펙의 핵심 교정이다.
ALTER TABLE spot_reaction DROP CONSTRAINT uq_spot_reaction;
ALTER TABLE spot_reaction ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, catalog_id);

-- FK를 카탈로그로 옮긴다.
--
-- **현재 FK가 `spot(id) ON DELETE CASCADE`라 SPEC-0004 R-5의 정리 배치가 spot 행을 지우면
-- 좋아요가 함께 사라진다.** spot.id가 언어별인 지금은 그럴듯해 보이지만 카탈로그 축에서는
-- 명백한 유실이다. 카탈로그는 장소 단위라 동기화로 사라졌다 다시 생기지 않는다.
--
-- `spot_id` 컬럼은 **남긴다** — 어느 언어 화면에서 눌렀는지의 기록이다. 조회 조건으로는
-- 더 이상 쓰지 않는다.
ALTER TABLE spot_reaction DROP CONSTRAINT fk_spot_reaction_spot;
ALTER TABLE spot_reaction
    ADD CONSTRAINT fk_spot_reaction_catalog
        FOREIGN KEY (catalog_id) REFERENCES spot_catalog (id) ON DELETE CASCADE;

-- 인덱스 교체. 셋 다 spot_id를 선행 컬럼으로 써서 카탈로그 조회를 전혀 커버하지 못한다.
--
-- **FK 자식 쪽 인덱스가 없으면 부모 1행 삭제마다 자식 전체 스캔이 발생한다**(V19가 같은
-- 이유로 추가했다). 새 FK가 catalog_id를 타므로 그 컬럼에 인덱스가 필요하다.
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
