-- 커뮤니티 스키마 초기화 (로컬·개발 전용)
--
-- 커뮤니티 테이블과 그 Flyway 이력만 지워, 재기동 시 V23부터 새로 적용되게 한다.
-- 적용된 마이그레이션은 체크섬 때문에 수정할 수 없으므로, 개발 중 V23 같은 기존 파일이
-- 바뀌면 이 초기화가 필요하다.
--
-- **지워지는 데이터는 전부 개발 시드다.** 재기동하면 R__seed_community.sql이 다시 채운다.
-- 다른 도메인(auth·tourism, V1~V22)은 건드리지 않는다.
--
-- 실행: DB 클라이언트에서 이 파일을 통째로 실행한 뒤 서버를 재기동한다.

BEGIN;

-- 1) 커뮤니티 테이블 제거. 자식은 FK CASCADE로 함께 지워지지만 순서에 기대지 않도록 명시한다.
--    아직 없을 수 있는 테이블이 섞여 있어 전부 IF EXISTS로 둔다.
DROP TABLE IF EXISTS community_post_photo CASCADE;
DROP TABLE IF EXISTS community_post_tag   CASCADE;
DROP TABLE IF EXISTS community_post_spot  CASCADE;
DROP TABLE IF EXISTS community_post_chip  CASCADE;
DROP TABLE IF EXISTS community_comment    CASCADE;
DROP TABLE IF EXISTS community_post_like  CASCADE;
DROP TABLE IF EXISTS community_report     CASCADE;
DROP TABLE IF EXISTS community_block      CASCADE;
DROP TABLE IF EXISTS community_chip       CASCADE;
DROP TABLE IF EXISTS community_post       CASCADE;

-- 2) 커뮤니티 이력 제거. R__ 반복 마이그레이션은 version이 NULL이라 description으로 잡는다.
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
--   WHERE (version IS NOT NULL AND version::int >= 23) OR description ILIKE '%community%';
--
-- 재기동하면 아래가 순서대로 적용된다:
--   V23 community_post        글 본체(title·body·edited). 대상 컬럼은 없다
--   V24 community_post_photo  사진 object key
--   V25 community_comment
--   V26 community_post_like
--   V27 community_block
--   V28 community_report
--   V29 community_chip        상단 칩 구성(앱별·언어별)
--   V30 community_post_tag    사용자 입력 태그
--   V31 community_post_spot / community_post_chip   글 ↔ 장소·카테고리 연결
--   V32 community_post.view_count       조회수
--   V33 community_comment.parent_comment_id  대댓글(깊이 2단계)
--   V34 community_post_spot.map_x/map_y/location  글의 좌표(반경 조회용)
--   R__ seed_community        글 16건(장소 없는 글 1건 포함) + 칩 34건
