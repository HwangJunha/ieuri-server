-- SPEC-0038: 추천·목록의 거리 정렬이 번역 행 단독으로 돌게 되어(카탈로그 조인을 LIMIT 뒤로 옮김)
-- 번역 행의 location에 공간 인덱스가 필요해졌다. 그전에는 카탈로그의 GiST만 쓰였다.
--
-- 부분 인덱스인 이유: 이 컬럼을 쓰는 조회는 항상 visible 행만 본다. 숨겨진 행이 빠져 인덱스가
-- 작아지고, 한 번 숨겨진 뒤의 갱신은 인덱스를 건드리지 않는다(visible을 뒤집는 그 UPDATE
-- 자체는 엔트리를 넣거나 빼므로 인덱스를 건드린다).
--
-- language는 넣지 않는다 — GiST에 스칼라 컬럼을 넣으려면 btree_gist가 필요한데 설치돼 있지
-- 않고, 확장 활성화는 매니페스트 소유다(POL-034). language는 인덱스 뒤 필터로 남는다.
CREATE INDEX idx_spot_catalog_translation_location
    ON tour.spot_catalog_translation USING gist (location)
    WHERE visible;
