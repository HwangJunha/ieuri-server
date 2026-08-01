-- tourism 도메인: 상세보기 좋아요 개수 집계 인덱스(SPEC-0003).
--
-- spotDetail 조회는 매번 (content_id, language)의 LIKE 반응 수를 실시간 집계한다(§5, 캐시 비대상).
--   SELECT count(*) FROM spot_reaction WHERE content_id = ? AND language = ? AND reaction = 'LIKE'
-- 기존 인덱스(uq_spot_reaction는 member_id 선행, idx_spot_reaction_member는 member_id 단독)로는
-- content_id 기반 집계를 커버하지 못해 순차 스캔이 된다. (content_id, language, reaction) 복합 인덱스로
-- 카운트를 인덱스만으로 처리한다(index-only scan 후보).
CREATE INDEX idx_spot_reaction_content_lang_reaction
    ON spot_reaction (content_id, language, reaction);
