-- community_post_spot·community_feed_item의 참조 축을 카탈로그로 옮긴다(SPEC-0019 R-12).
--
-- `spot_id`는 언어별 id라, 일본어로 쓴 글에 붙은 spot은 JA 행을 가리킨다. 카탈로그로 옮기면
-- **언어와 무관하게 같은 장소의 글이 한 피드에 모인다.**
--
-- **좌표는 그대로 유지한다.** V34가 좌표를 이 테이블에 복사해 둔 덕분에 반경 조회와 글 목록은
-- spot을 경유하지 않으므로 그 경로는 영향을 받지 않는다. "글이 쓰인 시점의 위치가 남는 편이
-- 자연스럽다"는 V34의 설계 의도는 카탈로그 전환 후에도 유효하다.
--
-- **물리 FK를 새로 만들지 않는다.** 이 테이블은 community 소유이고 참조 대상은 tourism이라
-- 도메인 간 FK 금지 규칙이 그대로 적용된다(V31 주석). 참조 대상이 spot에서 spot_catalog로
-- 바뀔 뿐이다.
--
-- > 장소 칩과 피드는 설계를 다시 볼 대상으로 남아 있다(사용자 판단 2026-08-14). 여기서는
-- > **참조 축만 옮기고 구조는 손대지 않는다** — 재설계 때 함께 정리한다.

-- ── community_post_spot ────────────────────────────────────────────────────
-- IF EXISTS를 쓰는 이유는 이 파일이 재작성되었기 때문이다 — 이전 버전이 이미 일부를
-- 적용한 환경에서도 같은 결과가 되어야 한다.
DROP INDEX IF EXISTS idx_community_post_spot_spot;
DROP INDEX IF EXISTS idx_community_post_spot_catalog;

-- **기존 연결을 지운다.** 백필할 원본(spot_id)이 방금 사라졌으므로 남은 행은 가리킬 장소가
-- 없다. 글 자체는 남고 연결만 없어지며, 이는 V31이 이미 정해 둔 동작이다("spot이 사라져도
-- 글은 남으며, 요약이 없으면 칩을 그리지 않는다"). 이 DELETE가 없으면 행이 있는 환경에서
-- 아래 NOT NULL 추가가 실패해 기동이 막힌다.
DELETE FROM community_post_spot;

ALTER TABLE community_post_spot DROP COLUMN IF EXISTS spot_id;
ALTER TABLE community_post_spot ADD COLUMN catalog_id BIGINT NOT NULL;

-- spot 상세 화면의 피드가 타는 인덱스. 조회 축이 바뀌었으므로 함께 교체한다.
CREATE INDEX idx_community_post_spot_catalog ON community_post_spot (catalog_id, post_id);

-- ── community_feed_item ────────────────────────────────────────────────────
-- 피드도 같은 축으로 옮긴다. 피드는 spot 연결이 필수라(V48) 두 표면이 같은 장소를 가리키는데
-- 축이 갈리면, 같은 장소의 글과 피드가 서로 다른 id로 조회된다.
-- 글과 달리 **피드 자체를 지운다** — 피드에서 spot은 장식이 아니라 여행지로 들어가는
-- 입구이고(V48), 연결만 떼면 입구 없는 피드가 남아 "spot 1곳 필수"가 깨진다.
DELETE FROM community_feed_item;

ALTER TABLE community_feed_item DROP COLUMN IF EXISTS spot_id;
ALTER TABLE community_feed_item ADD COLUMN catalog_id BIGINT NOT NULL;
