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

-- spot을 경유해 백필한다. 배정 배치가 spot.catalog_id를 이미 채워 둔 상태를 전제한다.
UPDATE community_post_spot ps
SET catalog_id = s.catalog_id
FROM spot s
WHERE s.id = ps.spot_id;

-- **`NOT NULL`은 여기서 걸지 않는다.** 이 마이그레이션이 도는 시점에는 배정 배치가 아직
-- 돌지 않아 `spot.catalog_id`가 전부 null이고, 따라서 위 백필도 전부 null을 넣는다.
-- 조이는 단계는 배치 완주 후 `db/migration-phase2`가 맡는다(그 폴더의 README 참조).

-- spot 상세 화면의 피드가 타는 인덱스. 조회 축이 바뀌었으므로 함께 교체한다.
--
-- 기존 인덱스는 **남긴다** — 배정 전까지 catalog_id가 null이라 새 인덱스로는 아무것도
-- 찾지 못하고, 그 구간에도 기존 경로가 살아 있어야 한다. 정리는 phase2가 한다.
CREATE INDEX idx_community_post_spot_catalog ON community_post_spot (catalog_id, post_id);

-- spot_id 컬럼은 **남긴다.** 어느 언어 화면에서 붙였는지의 기록이고, 이관이 잘못됐을 때
-- 되돌릴 원본이다. 조회 조건으로는 더 이상 쓰지 않는다.
