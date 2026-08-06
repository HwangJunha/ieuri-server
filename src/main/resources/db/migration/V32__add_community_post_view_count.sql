-- 글 조회수(SPEC-0010 추가 요구).
--
-- like_count·comment_count와 같은 자리에 둔다 — 파생값이지만 컬럼으로 적재한다.
-- 매 조회마다 세면 피드 한 페이지에 카운트 쿼리가 스무 번 붙기 때문이다.
--
-- **중복을 방지하지 않는다.** 같은 사람이 새로고침하면 계속 오른다. 회원별 1회로 세려면
-- (post_id, member_id) 유일 제약을 둔 별도 테이블이 필요한데, 행 수가 글 수 × 회원 수로
-- 늘고 조회 경로에 INSERT가 붙는다. 커뮤니티 조회수는 통상 이 정도 정밀도로 쓴다.
--
-- 증가는 상세 조회(post(id:))에서만 일어난다. 피드 목록에 카드가 스쳐 지나가는 것은
-- 조회가 아니다 — 그것까지 세면 스크롤 한 번에 수십이 올라 숫자가 의미를 잃는다.
--
-- 기존 행은 DEFAULT 0으로 채워진다. 이미 조회된 이력은 남아 있지 않으므로 0에서 시작하며,
-- 이는 데이터 손실이 아니라 기능 도입 시점의 자연스러운 출발점이다.
ALTER TABLE community_post
    ADD COLUMN view_count INTEGER NOT NULL DEFAULT 0;

-- like_count·comment_count와 같은 불변식. 음수가 되는 경로는 없지만, 다른 카운터와
-- 규칙을 맞춰 두면 나중에 감소 연산이 생겨도 DB가 먼저 막는다.
ALTER TABLE community_post
    ADD CONSTRAINT ck_community_post_view_count CHECK (view_count >= 0);
