-- SPEC-0033 — 제목에 부분 일치용 ngram 필드를 더한다.
--
-- 기존 인덱스는 제목을 **형태소(lindera)로만** 색인해, 한 단어로 묶인 합성어의 일부로는
-- 검색되지 않았다. 실측: "트리아 관광호텔"이 "관광호텔"·"트리아"로는 걸리지만 **"관광"으로는
-- 0건**이었다. 주소에는 처음부터 ngram이 걸려 있어 "강남" 같은 부분 일치가 되던 것과 대비된다.
--
-- 자기가 저장해 둔 장소를 다시 찾는 화면에서는 사용자가 기억나는 조각을 치므로, 제목에도
-- 부분 일치가 필요하다. 기존 필드는 하나도 빼지 않고 `title_ng`만 더한다 — 형태소 검색과
-- 주소 검색의 동작은 그대로다.
--
-- **ParadeDB는 테이블당 인덱스가 하나뿐이라 필드 추가에 재생성이 필요하다.** ALTER로 필드를
-- 붙일 수 없어 DROP 후 같은 이름으로 다시 만든다. 테이블 데이터는 영향받지 않는다.
DROP INDEX IF EXISTS tour.idx_spot_catalog_translation_bm25;

CREATE INDEX idx_spot_catalog_translation_bm25 ON tour.spot_catalog_translation
USING bm25 (
    id,
    ((title)::pdb.lindera('korean')),
    ((title)::pdb.lindera('japanese', 'alias=title_ja')),
    ((title)::pdb.lindera('chinese', 'alias=title_zh')),
    ((title)::pdb.simple('alias=title_en')),
    -- 신규. 형태소가 한 단어로 묶는 합성어를 조각으로 찾기 위한 필드다.
    ((title)::pdb.ngram('2', '3', 'alias=title_ng')),
    ((COALESCE(address, ''::character varying))::pdb.ngram('2', '3'))
) WITH (key_field=id);
