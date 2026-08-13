-- community_post_spot의 참조 축을 카탈로그로 옮긴다(SPEC-0019 R-12).
--
-- `spot_id`는 언어별 id라, 일본어로 쓴 글에 붙은 spot은 JA 행을 가리킨다. 카탈로그로 옮기면
-- **언어와 무관하게 같은 장소의 글이 한 피드에 모인다.**
--
-- **좌표는 그대로 유지한다.** V34가 좌표를 이 테이블에 복사해 둔 덕분에 반경 조회와 글 목록은
-- spot을 경유하지 않으므로 그 경로는 영향을 받지 않는다. "글이 쓰인 시점의 위치가 남는 편이
-- 자연스럽다"는 V34의 설계 의도는 카탈로그 전환 후에도 유효하다.
--
-- **물리 FK를 새로 만들지 않는다.** 이 테이블은 community 소유이고 참조 대상은 tourism이라
-- 도메인 간 FK 금지 규칙이 그대로 적용된다(V31 주석: "도메인 간이라 물리 FK를 두지 않는다").
-- 참조 대상이 spot에서 spot_catalog로 바뀔 뿐이다.

ALTER TABLE community_post_spot ADD COLUMN catalog_id BIGINT;

-- spot을 경유해 백필한다. 배정이 끝난 환경에서 재적용하면 이 한 줄로 이관이 끝난다.
UPDATE community_post_spot ps
SET catalog_id = s.catalog_id
FROM spot s
WHERE s.id = ps.spot_id;

-- 백필되지 못한 연결을 지운다. **글은 남고 연결만 없어진다** — V31이 이미 "spot이 사라져도
-- 글은 남으며, 조회 시 요약이 없으면 칩을 그리지 않는다"고 정해 둔 동작과 같다.
--
-- 이 마이그레이션이 도는 시점에는 배정 배치 전이라 `spot.catalog_id`가 전부 null이므로
-- 실질적으로 기존 연결이 모두 끊긴다. 운영 데이터가 없는 지금이라 가능한 선택이며
-- (사용자 확인 2026-08-14), 데이터가 생긴 뒤였다면 배포를 두 번에 나눠야 한다.
DELETE FROM community_post_spot WHERE catalog_id IS NULL;

-- 좌표 없는 연결 행이 생기면 그 글은 거리순에서 조용히 사라지므로, 카탈로그도 같은 기준으로
-- 조인다 — 조회 축이 비어 있는 연결은 어떤 장소 피드에도 나타나지 못한다.
ALTER TABLE community_post_spot ALTER COLUMN catalog_id SET NOT NULL;

-- spot 상세 화면의 피드가 타는 인덱스. 조회 축이 바뀌었으므로 함께 교체한다.
DROP INDEX IF EXISTS idx_community_post_spot_spot;
CREATE INDEX idx_community_post_spot_catalog ON community_post_spot (catalog_id, post_id);

-- spot_id 컬럼은 **남긴다.** 어느 언어 화면에서 붙였는지의 기록이다.
-- 조회 조건으로는 더 이상 쓰지 않는다.
