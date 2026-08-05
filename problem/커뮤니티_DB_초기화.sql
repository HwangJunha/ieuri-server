-- 커뮤니티 스키마 초기화 (로컬·개발 전용)
--
-- **지금 DB가 어떤 마이그레이션 파일로도 설명되지 않는 상태다.** 앞선 초기화에서 이력만
-- 지워지고 테이블은 남았다:
--   - community_post 6개 테이블이 존재하고 title·edited 컬럼까지 있다(옛 V30·V31의 결과)
--   - community_chip은 없다(새 V29가 아직 안 돌았다)
--   - flyway_schema_history에는 커뮤니티 항목이 하나도 없다
--
-- 그래서 Flyway가 V23부터 새로 만들려 하고 "relation community_post already exists"로 멈춘다.
-- 테이블을 지워 이력과 실제 스키마를 다시 맞춘다.
--
-- **지워지는 데이터는 전부 개발 시드다**(글 15건과 그에 딸린 댓글·좋아요·신고).
-- 재기동하면 R__seed_community.sql이 다시 채운다.
--
-- 다른 도메인(auth·tourism)은 건드리지 않는다 — V1~V22와 그 데이터는 그대로다.
--
-- 실행: DB 클라이언트에서 이 파일을 통째로 실행한 뒤 서버를 재기동한다.

BEGIN;

-- 1) 커뮤니티 테이블 제거. 자식은 FK CASCADE로 함께 지워지지만 순서에 기대지 않도록 명시한다.
--    community_chip·community_discovery_tile은 지금 없을 수도 있어 IF EXISTS로 둔다.
DROP TABLE IF EXISTS community_post_photo     CASCADE;
DROP TABLE IF EXISTS community_comment        CASCADE;
DROP TABLE IF EXISTS community_post_like      CASCADE;
DROP TABLE IF EXISTS community_report         CASCADE;
DROP TABLE IF EXISTS community_block          CASCADE;
DROP TABLE IF EXISTS community_chip           CASCADE;
DROP TABLE IF EXISTS community_discovery_tile CASCADE;
DROP TABLE IF EXISTS community_post           CASCADE;

-- 2) 커뮤니티 이력이 남아 있다면 함께 지운다. 지금은 비어 있지만, 이 스크립트를 다시 돌릴
--    때를 위해 둔다. auth·tourism(V1~V22)은 조건에서 제외된다.
DELETE FROM flyway_schema_history
WHERE (version IS NOT NULL AND version::int >= 23)
   OR description ILIKE '%community%';

COMMIT;

-- 3) 확인 — 둘 다 0건이어야 한다.
--
--   SELECT count(*) FROM information_schema.tables
--   WHERE table_schema='public' AND table_name LIKE 'community%';
--
--   SELECT count(*) FROM flyway_schema_history
--   WHERE version::int >= 23 OR description ILIKE '%community%';
--
-- 그 다음 서버를 재기동하면 V23~V29가 새로 적용된다:
--   V23 community_post (title·edited 포함)
--   V24 community_post_photo
--   V25 community_comment
--   V26 community_post_like
--   V27 community_block
--   V28 community_report
--   V29 community_chip
--   R__ seed_community  (글 15건 + 칩 34건 재적재)
