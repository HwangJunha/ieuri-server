-- spot 자식 6개에서 자연키 컬럼(content_id·language)을 제거하고, 식별을 spot_id로 일원화한다.
--
-- **V19의 판단을 뒤집는다.** V19는 두 컬럼을 남기며 "조회 경로가 전부 contentId로 들어오기 때문"이라고
-- 적었다. 그 전제가 바뀐다 — 이제 외부 식별자 자체가 spot.id다. GraphQL이 노출하던 `ID!`가
-- TourAPI contentId에서 우리 PK로 바뀌므로, 클라이언트가 spot.id를 들고 오고 서버는
-- contentId → spot.id 해석을 할 필요가 없다. 해석 단계가 사라지니 자식이 자연키를 들 이유도 없다.
--
-- **contentId는 TourAPI 호출 전용 내부 값으로 남는다.** `spot` 테이블은 두 컬럼을 그대로 유지한다 —
-- 배치 적재(`ON CONFLICT (content_id, language)`)의 upsert 키이고, 상세 조회 시 업스트림에 넘길
-- 값이기 때문이다. 자식에서만 사라진다.
--
-- 배포 전이라 데이터 호환을 고려하지 않는다. 운영 중이었다면 두 키를 병행 노출하는 단계를 거쳤겠지만,
-- 지금 정리하지 않으면 이 이중 키가 그대로 레거시가 된다.

-- ---------------------------------------------------------------------------
-- 1. 유니크 제약 재정의 — upsert 단위를 spot_id 기반으로 옮긴다
--
-- 기존 제약은 전부 (content_id, language[, ...])였다. 컬럼이 사라지므로 함께 사라지며,
-- **같은 판별 능력을 spot_id로 재구성한다** — spot_id 자체가 (content_id, language)에 1:1이므로
-- 유니크성은 동일하다(uq_spot이 그것을 보장한다).
-- ---------------------------------------------------------------------------
ALTER TABLE spot_detail       DROP CONSTRAINT uq_spot_detail;
ALTER TABLE spot_detail_image DROP CONSTRAINT uq_spot_detail_image;
ALTER TABLE spot_detail_intro DROP CONSTRAINT uq_spot_detail_intro;
ALTER TABLE spot_detail_info  DROP CONSTRAINT uq_spot_detail_info;
ALTER TABLE spot_reaction     DROP CONSTRAINT uq_spot_reaction;
ALTER TABLE spot_festival     DROP CONSTRAINT uq_spot_festival;

ALTER TABLE spot_detail
    ADD CONSTRAINT uq_spot_detail UNIQUE (spot_id);
ALTER TABLE spot_detail_image
    ADD CONSTRAINT uq_spot_detail_image UNIQUE (spot_id, display_order);
-- category가 키에 남는다 — upstream 응답이 카테고리별로 갈리기 때문이다(V16의 근거 유지).
ALTER TABLE spot_detail_intro
    ADD CONSTRAINT uq_spot_detail_intro UNIQUE (spot_id, category);
ALTER TABLE spot_detail_info
    ADD CONSTRAINT uq_spot_detail_info UNIQUE (spot_id, category, display_order);
-- 한 회원이 한 관광지에 한 번만 반응한다. language가 빠져도 판별력은 같다 — spot_id가 언어별이다.
ALTER TABLE spot_reaction
    ADD CONSTRAINT uq_spot_reaction UNIQUE (member_id, spot_id);
ALTER TABLE spot_festival
    ADD CONSTRAINT uq_spot_festival UNIQUE (spot_id);

-- ---------------------------------------------------------------------------
-- 2. 자연키 컬럼에 의존하던 인덱스·CHECK 제거
--
-- 새 유니크 제약이 spot_id 선행 인덱스를 제공하므로, V19가 CASCADE용으로 추가한 단독 인덱스는
-- 중복이 된다(spot_reaction 제외 — 거기는 유니크가 member_id 선행이라 여전히 필요하다).
-- ---------------------------------------------------------------------------
DROP INDEX idx_spot_detail_image_lookup;
DROP INDEX idx_spot_detail_info_lookup;
DROP INDEX idx_spot_reaction_content;
DROP INDEX idx_spot_reaction_content_lang_reaction;
DROP INDEX idx_spot_festival_end_date;

DROP INDEX idx_spot_detail_spot_id;
DROP INDEX idx_spot_detail_image_spot_id;
DROP INDEX idx_spot_detail_intro_spot_id;
DROP INDEX idx_spot_detail_info_spot_id;
DROP INDEX idx_spot_festival_spot_id;
-- idx_spot_reaction_spot_id는 **남긴다.** 이 테이블의 유니크는 (member_id, spot_id)로 member_id가
-- 선행이라 spot_id 단독 조회(CASCADE 삭제)를 커버하지 못한다. 다른 다섯은 유니크가 spot_id 선행이라
-- 중복이지만 여기만 다르다.

ALTER TABLE spot_detail       DROP CONSTRAINT ck_spot_detail_language;
ALTER TABLE spot_detail_image DROP CONSTRAINT ck_spot_detail_image_language;
ALTER TABLE spot_detail_intro DROP CONSTRAINT ck_spot_detail_intro_language;
ALTER TABLE spot_detail_info  DROP CONSTRAINT ck_spot_detail_info_language;
ALTER TABLE spot_reaction     DROP CONSTRAINT ck_spot_reaction_language;
ALTER TABLE spot_festival     DROP CONSTRAINT ck_spot_festival_language;

-- ---------------------------------------------------------------------------
-- 3. 컬럼 제거
-- ---------------------------------------------------------------------------
ALTER TABLE spot_detail       DROP COLUMN content_id, DROP COLUMN language;
ALTER TABLE spot_detail_image DROP COLUMN content_id, DROP COLUMN language;
ALTER TABLE spot_detail_intro DROP COLUMN content_id, DROP COLUMN language;
ALTER TABLE spot_detail_info  DROP COLUMN content_id, DROP COLUMN language;
ALTER TABLE spot_reaction     DROP COLUMN content_id, DROP COLUMN language;
ALTER TABLE spot_festival     DROP COLUMN content_id, DROP COLUMN language;

-- ---------------------------------------------------------------------------
-- 4. 조회 인덱스 재구성
-- ---------------------------------------------------------------------------

-- 좋아요 집계(likeCount)는 spot_id로 조회한다. V12가 (content_id, language, reaction)로 만든 것을
-- 대체한다 — 유니크(member_id, spot_id)는 member_id 선행이라 이 조회를 커버하지 못한다.
CREATE INDEX idx_spot_reaction_spot_reaction ON spot_reaction (spot_id, reaction);

-- 종료 축제 조회(R-8). language가 사라졌으므로 spot을 조인해 언어를 판별한다 — 이 인덱스는
-- 조인 후 날짜 필터를 받는다. 축제는 절대 건수가 적어 이것으로 충분하다.
CREATE INDEX idx_spot_festival_end_date ON spot_festival (event_end_date);
