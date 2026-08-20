-- 커뮤니티가 "이 카탈로그는 어느 언어에서 해소되는가"의 사본을 갖는다(SPEC-0029 R-15·R-23).
--
-- 목록은 활성 언어에서 해소되지 않는 spot이 붙은 글·질문을 **오류가 아니라 조용히 뺀다.**
-- 그 판정에 필요한 값은 tourism의 `spot_catalog_translation.visible`에 있는데, 목록 조회가
-- 그 테이블을 조인해 오면 도메인 경계를 뚫고 — 스키마가 갈려 있어(V62) 조인 자체는 되지만
-- community의 조회가 tour의 컬럼 의미에 묶인다 — 조인 없이 걸러내려면 후보 id 목록을
-- 통째로 넘겨야 해서 그 목록의 상한이 곧 목록의 한계가 된다.
--
-- 그래서 **커뮤니티 스키마 안에 사본을 둔다.** 목록 필터는 이 테이블 하나에 대한 EXISTS라
-- 한 쿼리로 끝나고 페이지 크기가 정확하다. 사본은 `SpotCatalogLanguagesChanged` 이벤트를
-- 구독해 맞춘다 — 갱신 지점이 배정 배치의 노출 갱신 한 곳뿐이라 어긋날 자리가 좁다.
--
-- **글이 아니라 카탈로그를 키로 잡는다.** 글에 매다는 형태도 가능하지만, 한 카탈로그에
-- 글이 1만 건이면 같은 사실이 1만 벌 복제되고 이벤트 한 번이 그 전부를 갱신해야 한다.
-- 질문(`community_spot_question`)은 애초에 `community_post_spot`을 거치지 않고 catalog_id를
-- 직접 들고 있어, 글에 매단 표는 질문에 쓸 수도 없다. 카탈로그 단위면 두 표면이 같은 한 표를
-- 본다.
--
-- **언어를 컬럼이 아니라 행으로 둔다.** 언어 집합은 변한다 — 지금 넷이지만 늘어날 수 있고,
-- 컬럼으로 두면 언어가 하나 늘 때마다 스키마가 바뀐다. 행이면 목록 필터가 인덱스를 타는
-- 평범한 EXISTS가 된다.
--
-- **FK를 걸지 않는다.** `catalog_id`가 가리키는 `tour.spot_catalog`는 다른 컨텍스트이며
-- 컨텍스트 간 물리 FK는 두지 않는다(V54·V64와 같은 판단). 카탈로그가 사라졌을 때의 정리는
-- 기존 `SpotCatalogDeleted` 구독이 맡는다.
CREATE TABLE community.community_spot_catalog_language (
    catalog_id BIGINT      NOT NULL,
    -- V11·V70이 쓰는 것과 같은 형태다(VARCHAR(2) + 허용값 CHECK).
    language   VARCHAR(2)  NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_community_spot_catalog_language PRIMARY KEY (catalog_id, language),
    CONSTRAINT ck_community_spot_catalog_language_language
        CHECK (language IN ('EN', 'KO', 'JA', 'ZH'))
);

-- 목록 필터가 타는 경로. 글·질문 조회는 **행을 이미 손에 쥔 catalog_id로 찾으므로**
-- PK(catalog_id, language)가 그대로 그 인덱스다 — 별도 인덱스를 만들지 않는다.
--
-- 반대 방향(언어 하나에서 해소되는 카탈로그 전부)이 필요한 곳은 없다. 필요해지면 그때
-- (language, catalog_id)를 추가한다 — 지금 만들면 쓰이지 않는 인덱스가 쓰기마다 비용만 낸다.

-- **초기 적재는 하지 않는다.** community에서 tour를 읽어 채우는 문장은 이 파일이 두 컨텍스트를
-- 가로지르게 만들고, 스키마가 갈린 뒤(V62) 두 노드가 갈라질 때 재현되지 않는다. 배정 배치가
-- 다음 회차에 노출을 다시 계산하며 이벤트를 발행하므로, 그때 채워진다.
--
-- 그때까지는 이 표가 비어 있어 **글·질문 목록이 spot 붙은 항목을 전부 뺀 것처럼 보인다.**
-- 이 스펙이 반응·카탈로그를 어차피 전량 초기화하므로(R-17) 재적재와 같은 시점이다.
