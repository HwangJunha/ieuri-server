-- SPEC-0030: V79 이미지 오매칭 정정 + V80 인덱스 수정 (로컬 DB 복구용)
-- 마이그레이션 파일을 직접 고쳤으므로 checksum과 실제 데이터를 함께 맞춘다.
BEGIN;

-- 1) 사진이 다른 곳의 것이던 13건을 올바른 이미지로 교체
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource_photo/09/3511909_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource_photo/09/3511909_image3_1.jpg', copyright='TYPE1', content_id='127642' WHERE caption='창덕궁';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/69/4075769_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/69/4075769_image3_1.jpg', copyright='TYPE1', content_id='125552' WHERE caption='서오릉';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/70/4093670_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/70/4093670_image3_1.jpg', copyright='TYPE3', content_id='2754295' WHERE caption='양주 별산대놀이';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource_photo/59/4071759_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource_photo/59/4071759_image3_1.jpg', copyright='TYPE1', content_id='126001' WHERE caption='외암마을';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/35/3534435_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/35/3534435_image3_1.jpg', copyright='TYPE1', content_id='127190' WHERE caption='화순 고인돌 유적';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/52/4056752_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/52/4056752_image3_1.jpg', copyright='TYPE3', content_id='127131' WHERE caption='상생의 손';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/70/3506170_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/70/3506170_image3_1.jpg', copyright='TYPE1', content_id='126166' WHERE caption='불국사';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/16/4059616_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/16/4059616_image3_1.jpg', copyright='TYPE1', content_id='126117' WHERE caption='주남저수지';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/34/4099834_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/34/4099834_image3_1.jpg', copyright='TYPE1', content_id='129479' WHERE caption='바람의 언덕';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/86/2653586_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/86/2653586_image3_1.jpg', copyright='TYPE3', content_id='126435' WHERE caption='성산일출봉';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/15/4041415_image2_1.png', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/15/4041415_image3_1.png', copyright='TYPE1', content_id='125794' WHERE caption='장릉';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/44/3533344_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/44/3533344_image3_1.jpg', copyright='TYPE1', content_id='126238' WHERE caption='덕유산국립공원';
UPDATE tour.map_cell_image SET image_url='https://tong.visitkorea.or.kr/cms/resource/47/3356647_image2_1.jpg', thumbnail_url='https://tong.visitkorea.or.kr/cms/resource/47/3356647_image3_1.jpg', copyright='TYPE1', content_id='128982' WHERE caption='채석강';

-- 2) 대체 사진이 없는 4건은 삭제 (특산품 2건은 TourAPI에 이미지가 없다)
DELETE FROM tour.map_cell_image WHERE caption IN ('함평 한우','의령 망개떡','운문사','동피랑 벽화마을');

-- 3) 삭제로 생긴 순번 구멍을 메운다 — display_order 0이 대표라 비면 칸이 빈다
WITH renum AS (
    SELECT id, row_number() OVER (PARTITION BY cell_id ORDER BY display_order) - 1 AS new_order
    FROM tour.map_cell_image
)
UPDATE tour.map_cell_image i SET display_order = r.new_order
FROM renum r WHERE r.id = i.id AND i.display_order <> r.new_order;

-- 4) checksum 갱신 — 파일을 고쳤으므로 이력의 값도 맞춘다.
--    820110145는 V79 파일의 Flyway CRC32이며, 기동 실패 로그의 "Resolved locally"와 같다.
--    V80은 고친 내용이 적용 시점과 같아 체크섬이 어긋나지 않는다(확인함).
UPDATE flyway_schema_history SET checksum = 820110145 WHERE version = '79';

COMMIT;
