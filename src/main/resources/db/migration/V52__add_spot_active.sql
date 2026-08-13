-- tourism 도메인: 비관광 spot 활성 상태(SPEC-0018 R1).
--
-- 약국·편의점·의원처럼 관광 목적이 아닌 항목을 조회에서 빼기 위한 컬럼이다. 기본값은 활성이며
-- 기존 행 전량이 그 값으로 백필된다 — 이 마이그레이션은 판정을 수행하지 않는다. 판정은 매 실행
-- 전량을 재수행하는 배치 단계가 소유하며(R19), 첫 배치가 다른 배치와 똑같이 실행되면서
-- 비활성 대상을 걸러낸다.
--
-- **키워드를 SQL에 두지 않는 이유**: 키워드는 설정이 소유한다(POL-044). SQL에도 두면 두 곳이
-- 갈리고, 설정을 고쳐도 이미 적용된 마이그레이션은 재실행되지 않는다.
--
-- **사유 열거형이 아니라 불리언이다**: 판정이 매 실행 전량을 재수행하므로 이전 실행이 남긴
-- 사유는 다음 실행에 덮어써져 의미를 잃는다.
--
-- **`SpotUpsertJdbcAdapter`의 ON CONFLICT SET 목록에 이 컬럼을 넣지 말 것.** 넣으면 매일 배치의
-- 적재 단계가 비활성을 활성으로 되돌린다(R3). 활성 상태는 판정 단계만 쓴다.
--
-- **검색 노드에도 같은 컬럼이 필요하다**(db/search-node/V3). 정의가 어긋나면 논리 복제가
-- 조용히 멈춰 검색 결과만 낡아간다.
ALTER TABLE spot ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- 모든 조회가 `active = true`를 함께 건다(R2). 언어·카테고리 필터 뒤에 붙인다 — 활성이
-- 절대다수라 단독 인덱스는 선택도가 없다.
CREATE INDEX idx_spot_language_category_active ON spot (language, category, active);

-- **기존 인덱스를 실제로 지운다.** (language, category)는 위 인덱스의 접두사라 완전히 중복이며,
-- 남겨 두면 매일 전 행 upsert가 두 인덱스를 모두 갱신한다.
DROP INDEX idx_spot_language_category;
