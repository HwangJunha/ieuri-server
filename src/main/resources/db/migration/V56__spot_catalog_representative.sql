-- 카탈로그의 **대표 spot 행**(SPEC-0019 R-9 노출 경로).
--
-- 번역이 채워지면 "그 언어의 제목은 있는데 그 언어의 spot 행은 없는" 카탈로그가 생긴다 —
-- 이 스펙 목적의 절반(KO 카탈로그의 64%가 외국어 제목이 없다)이 바로 그 상태다.
-- 그런데 조회는 제목 말고도 좌표·썸네일·카테고리·저작권을 함께 돌려줘야 한다.
--
-- **제목은 번역에서, 나머지는 대표 spot 행에서 가져온다.** §3.6이 실증했듯 좌표·이미지·기간은
-- 언어와 무관하게 같은 값이므로 어느 언어 행에서 가져와도 된다. 다만 **어느 행인지가 매번
-- 달라지면 안 된다** — 같은 카탈로그가 조회마다 다른 썸네일을 돌려주면 목록이 흔들린다.
--
-- 그래서 대표 행을 컬럼으로 고정한다. 매 조회마다 `MIN(id)` 같은 집계로 고르면 조회 3곳이
-- 각자 다른 규칙을 갖게 되고, 생 SQL·QueryDSL·BM25가 그 규칙을 서로 다르게 구현한다.
--
-- 채택 순서는 대표 한국어명과 같은 근거를 쓴다: **KO → EN → JA → ZH.** KO 행이 있으면
-- 그것이 원본이고, 없으면 관광공사 영문이 로마자 표기의 기준이다(R-3과 같은 순서).

ALTER TABLE spot_catalog ADD COLUMN representative_spot_id BIGINT;

-- 배정이 끝난 환경에서 재적용하면 이 한 번으로 채워진다. 최초 배포 시점에는 배정 배치가
-- 아직 돌지 않아 대상이 없고, 배치가 신규 카탈로그를 만들 때 함께 채운다.
UPDATE spot_catalog c
SET representative_spot_id = pick.id
FROM (
    SELECT DISTINCT ON (catalog_id) catalog_id, id
    FROM spot
    WHERE catalog_id IS NOT NULL
    ORDER BY catalog_id,
             -- 목록의 순서가 곧 우선순위다. CASE로 쓰는 이유는 language가 알파벳순으로는
             -- EN이 먼저 오기 때문이다.
             CASE language WHEN 'KO' THEN 0 WHEN 'EN' THEN 1 WHEN 'JA' THEN 2 ELSE 3 END,
             id
) pick
WHERE pick.catalog_id = c.id;

-- **NOT NULL로 조이지 않는다.** 배정 배치가 카탈로그를 만든 직후와 spot 행이 정리로 사라진
-- 뒤에 잠깐 null인 구간이 존재한다. 조회는 이 값이 null인 카탈로그를 결과에서 뺀다 —
-- 대표 행이 없으면 좌표도 썸네일도 줄 수 없으므로 카드를 그릴 수 없다.
ALTER TABLE spot_catalog
    ADD CONSTRAINT fk_spot_catalog_representative
        FOREIGN KEY (representative_spot_id) REFERENCES spot (id) ON DELETE SET NULL;

-- 조회가 카탈로그 → 대표 행으로 조인한다.
CREATE INDEX idx_spot_catalog_representative ON spot_catalog (representative_spot_id);

-- ---------------------------------------------------------------------------
-- 조회 전용 뷰 — 카탈로그 한 건을 "요청 언어의 카드"로 펼친다.
--
-- 뷰로 두는 이유는 **같은 조인을 조회 5곳이 되풀이하기 때문이다**(반경·지도·인기·축제·좋아요).
-- 각자 쓰면 언어 조건이나 대표 행 규칙이 한 곳에서만 어긋나도 그 조회만 조용히 다른 집합을
-- 돌려준다. 뷰는 인덱스를 막지 않는다 — 플래너가 펼쳐서 아래 테이블의 인덱스를 그대로 탄다.
--
-- **BM25 검색은 이 뷰를 쓰지 못한다.** pg_search가 평면 질의를 요구해 조인이 들어가면 인덱스
-- 푸시다운이 깨진다(search-node/V2 주석). 검색은 별도 경로다.
-- ---------------------------------------------------------------------------
CREATE VIEW spot_catalog_card AS
SELECT
    c.id                     AS catalog_id,
    t.language               AS language,
    -- 제목만 번역에서 온다. OFFICIAL이면 그 언어 spot 행의 제목과 같은 값이고,
    -- MACHINE이면 그 언어 행이 없어도 제목을 가질 수 있다 — 그것이 이 스펙의 목적이다.
    t.title                  AS title,
    -- **주소도 번역에서 온다**(R-4, 2026-08-14 개정). 대표 행에서 가져오면 일본어 제목 아래
    -- 한국어 주소가 붙어 — 그 장소를 찾아갈 수 없다. 목록에 나오는 의미가 사라진다.
    --
    -- 번역이 아직 안 채워진 구간에는 null이며, 그때는 대표 행의 주소로 대신한다.
    -- 원문에 주소가 아예 없는 spot도 있어(KO 기준 1.5%) 어느 쪽이든 null일 수 있다.
    coalesce(t.address, s.address) AS address,
    -- 나머지는 대표 행에서. 언어와 무관하게 같은 값이다(§3.6 실증) — 좌표·이미지·분류는
    -- 번역 대상이 아니다.
    s.id                     AS spot_id,
    s.thumbnail_image_url    AS thumbnail_image_url,
    s.map_x                  AS map_x,
    s.map_y                  AS map_y,
    s.location               AS location,
    s.category               AS category,
    s.copyright              AS copyright,
    -- 요청 언어의 spot 행이 실제로 있으면 그 id를 돌려준다. 클라이언트 계약이
    -- "요청 언어의 spot.id"이므로(계약 불변), 있을 때는 반드시 그것이어야 한다.
    -- 없으면 null이고, 그때는 호출자가 대표 행의 id를 쓴다.
    ls.id                    AS language_spot_id
FROM spot_catalog c
JOIN spot_catalog_translation t ON t.catalog_id = c.id
JOIN spot s ON s.id = c.representative_spot_id
LEFT JOIN spot ls ON ls.catalog_id = c.id AND ls.language = t.language;
