-- SPEC-0018 · 검색 노드의 spot 활성 상태 컬럼 (메인 V52에 대응)
--
-- **컬럼 정의가 메인과 어긋나면 논리 복제가 조용히 멈춘다** — 앱은 정상으로 보이고 검색 결과만
-- 낡아간다(V1의 머리말 참조). 타입·널 허용성·기본값을 메인 V52와 문자 단위로 맞춘다.
--
-- 검색 노드는 판정하지 않는다. 값은 복제로 도착하며, `trg_spot_sync_location`을 두지 않는 것과
-- 같은 이유다(V1:34-36).
ALTER TABLE spot ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;

-- 검색·자동완성·인기·축제 조회가 모두 `active = true`를 함께 건다(R2).
CREATE INDEX idx_spot_language_category_active ON spot (language, category, active);

-- 기존 인덱스는 위 인덱스의 접두사라 완전히 중복이다. 메인 V52와 같은 처리를 한다 —
-- 인덱스는 복제 대상이 아니므로 노드마다 따로 지워야 한다.
DROP INDEX idx_spot_language_category;
