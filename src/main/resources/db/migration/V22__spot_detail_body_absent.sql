-- tourism 도메인: 상세 본문 "부재"를 적재 가능한 상태로 만든다(SPEC-0008 R-3·R-4).
--
-- 문제: `spot`에는 있는데 TourAPI detailCommon2에 본문이 없는 항목이 실제로 존재한다. 지금은 그
-- 부재를 적재할 방법이 없어 매 조회마다 upstream을 다시 두드린다 — 다시 물어도 없는 데이터인데도.
--
-- **spot_detail_intro가 이미 쓰는 패턴의 확장이다.** V16이 적어 둔 그대로 — "조회했으나 값이 없음"을
-- 행으로 남기지 않으면 값 없는 항목을 매번 미스로 판정한다. 본문 테이블에 같은 원리를 적용한다.
--
-- ---------------------------------------------------------------------------
-- 왜 `overview IS NULL`이 아니라 명시 컬럼인가
-- ---------------------------------------------------------------------------
-- 설계 문서(§2.3)가 판정 컬럼을 구현에 맡겼다. 명시 컬럼을 택한다.
--
-- `overview IS NULL`로 판정하면 **본문이 정상적으로 조회됐지만 overview만 비어 있는 spot**과
-- 본문 자체가 부재한 spot이 구분되지 않는다. 둘은 실제로 다른 상태다 — 전자는 homepage·tel·address가
-- upstream 값이고, 후자는 그 셋이 전부 null이며 title·category·thumbnail조차 `spot` 행에서 빌려온
-- 값이다. 판정을 데이터 모양에서 추론하는 대신 사실 그 자체를 기록한다.
--
-- 이 컬럼이 없으면 부재 행을 읽은 쪽이 "이 title은 upstream이 준 것인가, spot에서 빌린 것인가"를
-- 영원히 알 수 없다. 부재는 추론 대상이 아니라 우리가 관측한 사실이다.
--
-- ---------------------------------------------------------------------------
-- 세 가지 상태
-- ---------------------------------------------------------------------------
--   미조회        : 행 없음                      → upstream 호출 (AC-10)
--   조회함·부재   : 행 있음 + body_absent = true  → upstream 호출 안 함 (AC-9)
--   조회함·존재   : 행 있음 + body_absent = false → upstream 호출 안 함
--
-- 부재 행도 NOT NULL 컬럼(title·category·thumbnail_image_url·copyright)을 채워야 하므로
-- `spot` 행의 값으로 채운다(R-2). overview·homepage·tel은 null로 남는다.
--
-- 신선도(R-4): 부재 행은 fetched_at이 7일을 넘기면 재조회한다. 정상 행은 지금처럼 만료가 없다
-- (1차 신선도는 Valkey TTL 24h, 배치가 목록을 매일 최신화). 부재를 더 짧게 잡는 이유는 업스트림에
-- 나중에 데이터가 생길 수 있기 때문이며, 임계값은 애플리케이션이 소유한다 — 튜닝 대상이라
-- 스키마에 못박지 않는다.
ALTER TABLE spot_detail
    ADD COLUMN body_absent BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN spot_detail.body_absent IS
    '업스트림에 상세 본문이 없음을 확인한 행. true면 title·category·thumbnail_image_url·copyright는 spot 행에서 빌려온 값이고 overview·homepage·tel은 null이다. fetched_at 기준 신선도가 지나면 재조회된다.';
