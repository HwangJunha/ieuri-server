-- 좋아요의 식별 축에 언어를 다시 넣는다(SPEC-0029 R-13).
--
-- **이 경로는 검색 노드(ieuri-search) 전용이다.** 메인 DB는 db/migration의 V70이 옮긴다.
--
-- **구독자가 발행자와 같은 모양이어야 한다.** 발행자에만 있는 `language NOT NULL`은 구독자가
-- 받을 자리가 없어 복제가 멈추고, 그러면 앱은 정상으로 보이는데 인기 순위만 낡아간다.
-- 인기 spot 조회(PopularSpotQueryJdbcAdapter)가 이 노드의 spot_reaction을 읽으므로,
-- `r.language` 조건이 붙은 새 쿼리는 컬럼이 없으면 아예 실패한다.
--
-- 남은 행은 지운다. 메인이 V70에서 전량 삭제했고 복제가 그것을 따라오지만, 여기서 먼저
-- 비워 두지 않으면 아래 NOT NULL 추가가 막힌다.
DELETE FROM tour.spot_reaction;

ALTER TABLE tour.spot_reaction ADD COLUMN IF NOT EXISTS language VARCHAR(2) NOT NULL;
ALTER TABLE tour.spot_reaction DROP CONSTRAINT IF EXISTS ck_spot_reaction_language;
ALTER TABLE tour.spot_reaction
    ADD CONSTRAINT ck_spot_reaction_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH'));

ALTER TABLE tour.spot_reaction DROP CONSTRAINT IF EXISTS uq_spot_reaction;
ALTER TABLE tour.spot_reaction
    ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, catalog_id, language);

-- 인기 순위 집계가 (catalog_id, language, reaction)으로 훑는다.
DROP INDEX IF EXISTS tour.idx_spot_reaction_catalog_reaction;
CREATE INDEX IF NOT EXISTS idx_spot_reaction_catalog_reaction
    ON tour.spot_reaction (catalog_id, language, reaction);

-- 좋아요 목록은 메인에서만 조회하지만, 두 노드의 모양을 갈라 두면 다음 변경 때 어느 쪽이
-- 기준인지가 매번 문제가 된다 — 발행자와 같게 유지한다.
DROP INDEX IF EXISTS tour.idx_spot_reaction_liked_list;
CREATE INDEX IF NOT EXISTS idx_spot_reaction_liked_list
    ON tour.spot_reaction (member_id, language, reaction, updated_at DESC, catalog_id DESC);
