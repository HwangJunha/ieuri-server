-- 작성자가 사라진 커뮤니티·관광 데이터를 정리한다(SPEC-0011 R17 정합성 회복).
--
-- 탈퇴 시 팬아웃 삭제가 동작하지 않던 기간에 남은 행들이다. 원인은 AFTER_COMMIT 리스너가
-- 부르는 유스케이스의 트랜잭션 전파가 REQUIRED였던 것 — 이미 커밋된 트랜잭션에 합류해
-- 삭제가 흘러가지 않았고, 예외도 없어 성공한 것처럼 보였다.
--
-- **남기지 않고 지운다.** 작성자 없는 글은 조회 경로가 자리표시자 닉네임으로 견디긴 하지만,
-- R17이 "소유 데이터 전량 삭제"를 요구하므로 남겨 두면 그 계약이 깨진 상태가 유지된다.
--
-- 도메인 간 FK가 없어(추출 가능성을 위한 의도적 설계) DB가 이 정합성을 강제하지 못한다.
-- 그래서 애플리케이션이 놓친 것을 여기서 한 번 걷어낸다.

-- 글. 사진·태그·카테고리·좋아요·숨김과 그 글의 댓글은 ON DELETE CASCADE가 따라 지운다.
DELETE FROM community_post p
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = p.author_id);

-- 남의 글에 남긴 댓글. 자기 글의 댓글은 위에서 cascade로 사라진 뒤다.
DELETE FROM community_comment c
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = c.author_id);

-- 좋아요·숨김·차단. 글과 달리 회원이 주체라 cascade로 지워지지 않는다.
DELETE FROM community_post_like l
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = l.member_id);

DELETE FROM community_post_hide h
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = h.member_id);

DELETE FROM community_block b
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = b.blocker_id)
   OR NOT EXISTS (SELECT 1 FROM member m WHERE m.id = b.blocked_id);

-- 신고. 신고자가 사라진 기록은 남겨도 쓸 곳이 없다.
DELETE FROM community_report r
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = r.reporter_id);

-- 관광 좋아요/싫어요.
DELETE FROM spot_reaction sr
WHERE NOT EXISTS (SELECT 1 FROM member m WHERE m.id = sr.member_id);
