-- 좋아요의 식별 축에 언어를 다시 넣는다(SPEC-0029 R-13).
--
-- SPEC-0019(V53)가 `UNIQUE(member_id, catalog_id)`로 언어를 걷어냈다. 그때는 "같은 장소를
-- 언어만 바꿔 다시 만나는" 것이 문제였는데, 이 스펙에서 spot 노출 전체가 활성 언어 단위로
-- 분리되면서 전제가 뒤집혔다 — 언어를 바꾸면 목록도 상세도 그 언어의 것만 보이므로,
-- 좋아요만 언어를 가로지르면 **그 언어에서 한 번도 보지 못한 장소가 발견 큐에서 빠지고**
-- 좋아요 수가 읽을 수 없는 언어의 반응까지 합산해 보고된다(AC-13.5).
--
-- 되돌리는 것이 아니라 축이 하나 늘어나는 것이다. `catalog_id`는 그대로 남는다 — 언어는
-- 별도 축이고, 같은 언어 안에서 장소는 여전히 카탈로그 단위다.
--
-- **기존 행을 지운다.** 어느 언어에서 눌린 것인지 알아낼 근거가 어디에도 없어 백필할 수
-- 없다 — 한 언어로 몰아넣는 것은 나머지 언어에서 좋아요가 사라진 것과 같고, 전 언어에
-- 복제하는 것은 누른 적 없는 반응을 만들어 낸다. R-17이 어차피 반응을 비우므로 여기서
-- 함께 정리한다. 이 DELETE가 없으면 행이 있는 환경에서 아래 NOT NULL 추가가 막힌다.
DELETE FROM tour.spot_reaction;

-- V11이 쓰던 것과 같은 형태다(VARCHAR(2) + 허용값 CHECK). V53이 지웠던 컬럼이 돌아온다.
ALTER TABLE tour.spot_reaction ADD COLUMN language VARCHAR(2) NOT NULL;
ALTER TABLE tour.spot_reaction
    ADD CONSTRAINT ck_spot_reaction_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH'));

-- upsert(재평가 덮어쓰기)의 단위. 언어가 빠지면 다른 언어의 행을 집어 덮어쓴다.
ALTER TABLE tour.spot_reaction DROP CONSTRAINT uq_spot_reaction;
ALTER TABLE tour.spot_reaction
    ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, catalog_id, language);

-- 세 인덱스를 언어 축에 맞춘다. 조회가 전부 language를 함께 걸게 되었으므로 그것이 빠진
-- 인덱스는 필요 없는 행까지 훑는다.
DROP INDEX IF EXISTS tour.idx_spot_reaction_catalog_reaction;
DROP INDEX IF EXISTS tour.idx_spot_reaction_liked_list;

-- 좋아요 수 집계·인기 순위(countLikes, PopularSpotQuery)가 타는 경로.
CREATE INDEX idx_spot_reaction_catalog_reaction
    ON tour.spot_reaction (catalog_id, language, reaction);

-- 좋아요 목록의 keyset 정렬 (updated_at DESC, catalog_id DESC).
-- **language가 정렬 컬럼보다 앞이어야 한다** — 등치 조건이라 앞에 있어야 정렬을 인덱스로
-- 받고, 뒤에 두면 필터가 정렬 이후로 밀려 매번 sort가 붙는다.
CREATE INDEX idx_spot_reaction_liked_list
    ON tour.spot_reaction (member_id, language, reaction, updated_at DESC, catalog_id DESC);

-- **idx_spot_reaction_catalog_id는 그대로 둔다.** FK(catalog_id → spot_catalog) 자식 쪽
-- 인덱스로서의 역할이라 언어와 무관하다 — 부모 1행 삭제가 이것으로 자식을 찾는다.
