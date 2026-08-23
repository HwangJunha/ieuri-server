-- ═══════════════════════════════════════════════════════════════════════════
-- [1/3] 운영 이관 전 데이터 정리 — 개발 클러스터 메인 DB(ieuri-db/ieuri-pg)에서 실행
-- ═══════════════════════════════════════════════════════════════════════════
--
-- 남기는 것: 회원 본체·프로필·역할·약관동의, spot 계열 전부, catalog, district·area,
--            map_cell·map_cell_image, category, KTO 통계, 혼잡구역
-- 지우는 것: 커뮤니티 전체, spot 좋아요, 회원 부가 이력, 알림, 배치 실행 이력,
--            지도 개인 기록
--
-- **한 트랜잭션이며 COMMIT을 담지 않는다.** 중간에 실패하면 전부 되돌아가고, 성공해도
-- 마지막 확인 표를 사람이 본 뒤 직접 커밋한다(파일 끝 참조). 일부만 지워진 상태도,
-- 확인 없이 커밋된 상태도 되돌릴 수 없다.
--
-- 실행 — **대화형 세션이어야 한다**(`-it`). 파일 리다이렉트로 넣으면 확인 표를 보고
-- 판단할 틈 없이 세션이 끝나며, 커밋하지 않았으므로 삭제가 통째로 롤백된다:
--
--   kubectl -n ieuri-db exec -it ieuri-pg-2 -c postgres -- psql -U postgres -d ieuri
--   -- 그 세션에 이 파일 내용을 붙여넣는다
--
-- **논리 복제가 붙어 있다**(search_pub → 검색 노드). spot_reaction이 발행 대상이라
-- 여기서 지우면 검색 노드에서도 함께 지워진다 — 의도한 동작이며 따로 손댈 것이 없다.
-- 나머지 삭제 대상은 발행 대상이 아니다.

BEGIN;

-- ── 커뮤니티 ────────────────────────────────────────────────────────────────
-- 자식은 전부 ON DELETE CASCADE라 부모만 지우면 따라 지워지지만, 무엇이 사라지는지
-- 눈에 보이도록 명시한다. 순서는 FK 방향을 거스르지 않게 자식부터다.

-- 게시글 계열. post를 지우면 chip·hide·like·photo·spot·tag·comment가 CASCADE로 함께 간다.
DELETE FROM community.community_post;
-- post에 매달리지 않는 것들.
DELETE FROM community.community_post_hide;
DELETE FROM community.community_report;
DELETE FROM community.community_block;
DELETE FROM community.community_orphan_photo_object;

-- DM. conversation → message → quote·report_item 순으로 CASCADE된다.
DELETE FROM community.community_conversation;
DELETE FROM community.community_message_report;

-- Spot Q&A. question → answer → reply 가 CASCADE된다. 사진은 owner_type으로만
-- 묶여 있어(FK 없음) 따라가지 않으므로 직접 지운다.
DELETE FROM community.community_spot_qa_photo;
DELETE FROM community.community_spot_question;

-- **community_spot_catalog_language(8만 행)는 지우지 않는다.** 사용자 생성물이 아니라
-- catalog가 어느 언어로 존재하는지를 담은 파생 데이터이며, 지우면 Q&A 기능이 어느
-- catalog에 질문을 걸 수 있는지 판단하지 못한다.

-- ── spot 좋아요 ─────────────────────────────────────────────────────────────
-- catalog는 남고 반응만 지운다. 검색 노드에도 복제로 전파된다(위 머리말 참조).
DELETE FROM tour.spot_reaction;

-- ── 회원 부가 이력 ──────────────────────────────────────────────────────────
-- 회원 본체·프로필·역할·약관동의는 남긴다. 여기서 지우는 것은 재발급 가능하거나
-- 지나간 기록뿐이다.
DELETE FROM auth.member_audit_log;
DELETE FROM auth.email_verification;
DELETE FROM auth.password_reset;

-- ── 알림 ────────────────────────────────────────────────────────────────────
-- 기기 토큰은 앱이 다시 등록한다. 수신 설정은 기본값으로 되돌아간다.
DELETE FROM alarm.notification_message;
DELETE FROM alarm.device_token;
DELETE FROM alarm.notification_preference;

-- ── 배치 실행 이력 ──────────────────────────────────────────────────────────
-- Spring Batch 메타. **순서가 강제된다** — 컨텍스트·파라미터가 실행을 참조하고,
-- 실행이 인스턴스를 참조한다.
DELETE FROM batch.batch_step_execution_context;
DELETE FROM batch.batch_step_execution;
DELETE FROM batch.batch_job_execution_context;
DELETE FROM batch.batch_job_execution_params;
DELETE FROM batch.batch_job_execution;
DELETE FROM batch.batch_job_instance;

-- ── 지도 개인 기록 ──────────────────────────────────────────────────────────
-- 회원이 만든 방문·관계·공유 링크. map_cell(161)·map_cell_image(365)는 서비스
-- 기준정보라 남긴다.
DELETE FROM tour.member_district_visit;
DELETE FROM tour.map_relation;
DELETE FROM tour.map_share_link;

-- ── 확인 ────────────────────────────────────────────────────────────────────
-- 남아야 할 것과 비어야 할 것을 한 번에 본다. 이 표를 보고 사람이 커밋을 판단한다.

SELECT '남김' AS 구분, 'auth.member' AS 테이블, count(*) FROM auth.member
UNION ALL SELECT '남김', 'auth.member_profile', count(*) FROM auth.member_profile
UNION ALL SELECT '남김', 'auth.member_role', count(*) FROM auth.member_role
UNION ALL SELECT '남김', 'auth.member_terms_agreement', count(*) FROM auth.member_terms_agreement
UNION ALL SELECT '남김', 'tour.spot', count(*) FROM tour.spot
UNION ALL SELECT '남김', 'tour.spot_catalog', count(*) FROM tour.spot_catalog
UNION ALL SELECT '남김', 'tour.spot_catalog_translation', count(*) FROM tour.spot_catalog_translation
UNION ALL SELECT '남김', 'tour.district', count(*) FROM tour.district
UNION ALL SELECT '남김', 'tour.map_cell_image', count(*) FROM tour.map_cell_image
UNION ALL SELECT '남김', 'community.community_spot_catalog_language', count(*) FROM community.community_spot_catalog_language
UNION ALL SELECT '비움', 'community.community_post', count(*) FROM community.community_post
UNION ALL SELECT '비움', 'community.community_message', count(*) FROM community.community_message
UNION ALL SELECT '비움', 'community.community_spot_question', count(*) FROM community.community_spot_question
UNION ALL SELECT '비움', 'tour.spot_reaction', count(*) FROM tour.spot_reaction
UNION ALL SELECT '비움', 'auth.member_audit_log', count(*) FROM auth.member_audit_log
UNION ALL SELECT '비움', 'alarm.device_token', count(*) FROM alarm.device_token
UNION ALL SELECT '비움', 'tour.member_district_visit', count(*) FROM tour.member_district_visit
ORDER BY 1 DESC, 2;

-- ⚠ 여기서 멈춘다. 위 표를 확인하고 직접 친다:
--
--   COMMIT;     -- 표가 기대와 같다
--   ROLLBACK;   -- 하나라도 어긋난다
