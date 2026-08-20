-- spot 데이터를 전량 비우고 새 규칙으로 다시 쌓는다(SPEC-0029 R-17).
--
-- 이번 스펙이 수집 규칙을 세 군데 바꾼다 — 의료 시설 제외(R-1), `showflag=1` 한정(R-2),
-- 기계번역 채움 중단(R-6). 기존 행에는 셋 다 적용되지 않은 상태라, 걸러졌어야 할 spot과
-- 원문 없이 번역만 있는 카탈로그가 그대로 남는다. 규칙을 바꾼 뒤 **빈 상태에서 다시
-- 쌓는 편이 잔재를 판별해 지우는 것보다 확실하다**(설계 문서 §3.2).
--
-- **`TRUNCATE`가 아니라 `DELETE`를 쓴다**(AC-17.4·AC-17.5). 검색 노드가 논리 복제
-- 구독자인데, `TRUNCATE`는 publication의 대상 연산에 포함돼야만 전파된다. 확인 결과
-- `search_pub`은 `pubtruncate = true`라 둘 다 전파되지만, **상시 정리(R-3)가 이미
-- `DELETE` 기반이므로**(SpotStoreAdapter.deleteMissing) 같은 연산으로 맞춘다 — 두 경로의
-- 전파 특성이 갈리면 "상시 정리는 반영되는데 초기화만 안 되는" 진단하기 어려운 상태가 된다.
--
-- **삭제 대상이 아닌 것**(AC-17.3): 사용자 계정과 글 본문. 글은 남고 spot 연결만 끊긴다.

-- 글-장소 연결. **먼저 지운다** — `community_post_spot.catalog_id`는 컨텍스트가 갈려
-- 물리 FK가 없어(V54) 카탈로그를 지워도 따라오지 않는다. 남기면 사라진 카탈로그를
-- 가리키는 연결이 되고, 목록 조회가 그 글을 영영 뺀다.
DELETE FROM community.community_post_spot;

-- 언어 사본도 비운다(V72). 배정 배치가 다음 회차에 이벤트로 다시 채운다.
DELETE FROM community.community_spot_catalog_language;

-- 카탈로그. `spot_catalog_translation`·`spot_catalog_festival`·`spot_reaction`이
-- ON DELETE CASCADE로 함께 지워진다 — **좋아요가 여기 딸려 간다**(AC-17.1).
DELETE FROM tour.spot_catalog;

-- 원본. `spot_detail`·`spot_detail_image`·`spot_detail_intro`·`spot_detail_info`·
-- `spot_festival`이 CASCADE로 따라온다.
--
-- 카탈로그를 먼저 지우면 `spot.catalog_id`가 SET NULL이 되지만, 어차피 이 문장이
-- 그 행들을 전부 지우므로 순서가 결과를 바꾸지 않는다.
DELETE FROM tour.spot;
