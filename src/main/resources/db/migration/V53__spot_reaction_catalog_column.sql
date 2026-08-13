-- 좋아요의 참조 축을 카탈로그로 옮긴다 — 1단계: 컬럼 추가와 백필(SPEC-0019 §4.1).
--
-- **이 절이 SPEC-0019에서 가장 위험하다.** 좋아요는 사용자가 만든 유일한 데이터이며 복구할
-- 원본이 없다. TourAPI 데이터는 다시 받으면 되지만 좋아요는 되돌릴 수 없다.
--
-- V19가 `(content_id, language)` → `spot_id` 전환을 이미 같은 문제로 풀었다. 그 순서를 그대로 쓴다:
--   1. catalog_id 컬럼 추가 (nullable)          ← 이 마이그레이션
--   2. spot을 경유해 백필                        ← 이 마이그레이션 + 배정 배치 후 재실행
--   3. dedupe                                    ← 애플리케이션 배치(reactionDedupeJob)
--   4. NOT NULL로 조이기                         ← db/migration-phase2/V54
--   5. UNIQUE 교체                               ← db/migration-phase2/V54
--   6. FK 이전                                   ← db/migration-phase2/V54
--   7. 인덱스 교체                               ← db/migration-phase2/V54
--
-- **4~7은 이 폴더에 두지 않는다.** Flyway가 부팅 때 대기 중인 마이그레이션을 연속 적용하므로,
-- 같은 폴더에 두면 배정 배치가 돌기 전에 조이기가 실행돼 앱이 기동하지 못한다.
-- 실행 순서는 `db/migration-phase2/README.md`가 소유한다.
--
-- **3번을 건너뛰면 5번이 실패한다.** 한 회원이 같은 장소를 여러 언어로 좋아요했다면 카탈로그
-- 축에서 최대 4행이 하나로 충돌하기 때문이다. dedupe를 마이그레이션이 아니라 애플리케이션에
-- 두는 이유는 남길 행을 고르는 규칙이 도메인 판단이고(어느 반응이 현재 의사인가) 단위
-- 테스트로 검증해야 하기 때문이다(설계 문서 §4.2).

ALTER TABLE spot_reaction ADD COLUMN catalog_id BIGINT;

-- spot을 경유해 백필한다.
--
-- **최초 배포 시점에는 이 UPDATE가 아무것도 채우지 못한다** — 배정 배치가 아직 돌지 않아
-- spot.catalog_id가 전부 null이기 때문이다. 그래서 배정 완주 후 같은 UPDATE를 한 번 더
-- 돌려야 하며, 그 절차는 db/migration-phase2/README.md의 3단계다.
--
-- 여기서 그대로 두는 이유는 재배포·신규 환경에서는 이 한 번으로 끝나기 때문이다.
UPDATE spot_reaction r
SET catalog_id = s.catalog_id
FROM spot s
WHERE s.id = r.spot_id;

-- dedupe 배치가 (member_id, catalog_id) 묶음을 훑는다. 이 인덱스가 없으면 전량 스캔이다.
CREATE INDEX idx_spot_reaction_member_catalog ON spot_reaction (member_id, catalog_id);
