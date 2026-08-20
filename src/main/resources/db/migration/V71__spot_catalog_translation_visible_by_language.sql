-- `visible`과 `has_origin`을 하나로 합친다(SPEC-0029).
--
-- 두 컬럼이 **같은 사실을 다른 단위로** 말하고 있었다:
--   - `visible`    — 카탈로그에 원본이 하나라도 있는가 (카탈로그 단위)
--   - `has_origin` — **그 언어의** 원본이 있는가        (언어 단위)
--
-- 그래서 "이 언어에서 볼 수 있는가"를 알려면 조회가 늘 둘을 AND 해야 했고, 정작 언어별
-- 노출은 표현되지 않았다 — KO+JA 카탈로그에서 JA 원본만 사라져도 `visible`은 참으로 남아
-- JA 사용자에게 계속 보였다. 이 스펙은 노출 전체를 활성 언어 단위로 가르므로 그 상태가
-- 곧 결함이 된다.
--
-- 남기는 쪽은 `visible`이고 의미를 언어별로 바꾼다. 조회 코드가 이미 전부 `t.visible`을
-- 보고 있어 조건식이 그대로 맞아떨어진다.
UPDATE tour.spot_catalog_translation t
SET visible = EXISTS (
    SELECT 1 FROM tour.spot s WHERE s.catalog_id = t.catalog_id AND s.language = t.language
);

-- 정렬 우선순위로 쓰던 인덱스도 함께 간다 — `has_origin DESC`로 시작하던 정렬은 언어
-- 폴백이 사라져(R-10) 쓰는 곳이 없다.
DROP INDEX IF EXISTS tour.idx_spot_catalog_translation_origin;

ALTER TABLE tour.spot_catalog_translation DROP COLUMN has_origin;
