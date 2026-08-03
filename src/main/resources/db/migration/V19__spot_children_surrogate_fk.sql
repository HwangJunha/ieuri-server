-- spot 자식 테이블 6개의 FK를 자연키 (content_id, language)에서 surrogate PK spot.id로 전환한다.
--
-- V17이 자연키 FK를 건 판단을 뒤집는다. 당시에는 자식 테이블이 이미 (content_id, language)를 갖고
-- 있어 컬럼 추가 없이 FK를 걸 수 있다는 점이 근거였다. 그러나 자연키 FK는 두 컬럼을 모든 자식의
-- 참조 경로에 고착시켜, 부모의 키 구성이 바뀌면 자식 6개가 연쇄로 바뀐다. surrogate 참조는 그
-- 연결을 끊는다 — 부모가 무엇으로 식별되든 자식은 id 하나만 들고 있으면 된다.
--
-- **content_id·language 컬럼은 자식에 그대로 남긴다.** 조회 경로가 전부 contentId로 들어오기
-- 때문이다(클라이언트가 주는 값은 contentId이지 spot.id가 아니다). 지우면 모든 자식 조회에
-- spot 조인이 강제되고, 특히 상세 write-behind 적재는 spot 행 없이도 동작해야 하므로 조인이
-- 불가능한 경우가 생긴다. 중복 저장의 대가를 치르고 조회 단순성을 얻는다.
--
-- 따라서 spot_id는 **쓰기 시점의 부모 참조 전용**이다. 조회 조건으로 쓰지 않으며(모든 자식 조회는
-- content_id·language로 한다), 도메인 모델에도 대응 필드를 두지 않는다.

-- ---------------------------------------------------------------------------
-- 1. spot_id 컬럼 추가 (nullable로 시작 — 백필 후 NOT NULL로 조인다)
-- ---------------------------------------------------------------------------
ALTER TABLE spot_detail       ADD COLUMN spot_id BIGINT;
ALTER TABLE spot_detail_image ADD COLUMN spot_id BIGINT;
ALTER TABLE spot_detail_intro ADD COLUMN spot_id BIGINT;
ALTER TABLE spot_detail_info  ADD COLUMN spot_id BIGINT;
ALTER TABLE spot_reaction     ADD COLUMN spot_id BIGINT;
ALTER TABLE spot_festival     ADD COLUMN spot_id BIGINT;

-- ---------------------------------------------------------------------------
-- 2. 백필
--
-- 기존 FK가 ON DELETE CASCADE로 이미 걸려 있어 **고아 행은 존재할 수 없다.** 따라서 이 UPDATE는
-- 전 행을 채우며, 아래 NOT NULL이 그것을 검증한다 — 한 건이라도 못 채우면 여기서 실패한다.
-- ---------------------------------------------------------------------------
UPDATE spot_detail       c SET spot_id = s.id FROM spot s WHERE s.content_id = c.content_id AND s.language = c.language;
UPDATE spot_detail_image c SET spot_id = s.id FROM spot s WHERE s.content_id = c.content_id AND s.language = c.language;
UPDATE spot_detail_intro c SET spot_id = s.id FROM spot s WHERE s.content_id = c.content_id AND s.language = c.language;
UPDATE spot_detail_info  c SET spot_id = s.id FROM spot s WHERE s.content_id = c.content_id AND s.language = c.language;
UPDATE spot_reaction     c SET spot_id = s.id FROM spot s WHERE s.content_id = c.content_id AND s.language = c.language;
UPDATE spot_festival     c SET spot_id = s.id FROM spot s WHERE s.content_id = c.content_id AND s.language = c.language;

ALTER TABLE spot_detail       ALTER COLUMN spot_id SET NOT NULL;
ALTER TABLE spot_detail_image ALTER COLUMN spot_id SET NOT NULL;
ALTER TABLE spot_detail_intro ALTER COLUMN spot_id SET NOT NULL;
ALTER TABLE spot_detail_info  ALTER COLUMN spot_id SET NOT NULL;
ALTER TABLE spot_reaction     ALTER COLUMN spot_id SET NOT NULL;
ALTER TABLE spot_festival     ALTER COLUMN spot_id SET NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. 기존 자연키 FK 제거 → surrogate FK로 교체
--
-- ON DELETE CASCADE는 그대로 유지한다. 관광지 정리(SPEC-0004 R-5)와 종료 축제 삭제(SPEC-0007 R-8)가
-- 둘 다 spot 행을 지우는 것으로 구현되어 이 CASCADE에 의존한다.
-- ---------------------------------------------------------------------------
ALTER TABLE spot_detail       DROP CONSTRAINT fk_spot_detail_spot;
ALTER TABLE spot_detail_image DROP CONSTRAINT fk_spot_detail_image_spot;
ALTER TABLE spot_detail_intro DROP CONSTRAINT fk_spot_detail_intro_spot;
ALTER TABLE spot_detail_info  DROP CONSTRAINT fk_spot_detail_info_spot;
ALTER TABLE spot_reaction     DROP CONSTRAINT fk_spot_reaction_spot;
ALTER TABLE spot_festival     DROP CONSTRAINT fk_spot_festival_spot;

ALTER TABLE spot_detail
    ADD CONSTRAINT fk_spot_detail_spot FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE;
ALTER TABLE spot_detail_image
    ADD CONSTRAINT fk_spot_detail_image_spot FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE;
ALTER TABLE spot_detail_intro
    ADD CONSTRAINT fk_spot_detail_intro_spot FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE;
ALTER TABLE spot_detail_info
    ADD CONSTRAINT fk_spot_detail_info_spot FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE;
ALTER TABLE spot_reaction
    ADD CONSTRAINT fk_spot_reaction_spot FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE;
ALTER TABLE spot_festival
    ADD CONSTRAINT fk_spot_festival_spot FOREIGN KEY (spot_id) REFERENCES spot (id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4. CASCADE 삭제용 인덱스
--
-- **FK 자식 쪽에 인덱스가 없으면 부모 1행 삭제마다 자식 전체 스캔이 발생한다.** V17이
-- idx_spot_reaction_content를 추가한 것과 같은 이유다. 이제 CASCADE가 spot_id를 타므로 그 컬럼에
-- 인덱스가 필요하다 — 기존 (content_id, language) 인덱스는 더 이상 이 경로를 커버하지 않는다.
--
-- 관광지 정리는 한 번에 수천 행을 지우므로(SPEC-0004 R-5) 이 인덱스가 없으면 정리가 사실상 멈춘다.
-- ---------------------------------------------------------------------------
CREATE INDEX idx_spot_detail_spot_id       ON spot_detail       (spot_id);
CREATE INDEX idx_spot_detail_image_spot_id ON spot_detail_image (spot_id);
CREATE INDEX idx_spot_detail_intro_spot_id ON spot_detail_intro (spot_id);
CREATE INDEX idx_spot_detail_info_spot_id  ON spot_detail_info  (spot_id);
CREATE INDEX idx_spot_reaction_spot_id     ON spot_reaction     (spot_id);
CREATE INDEX idx_spot_festival_spot_id     ON spot_festival     (spot_id);

-- ---------------------------------------------------------------------------
-- 5. 유니크 제약은 그대로 둔다
--
-- (content_id, language[, ...]) 유니크는 **upsert 단위**이며 FK와 목적이 다르다. 적재는 여전히
-- TourAPI가 준 contentId로 ON CONFLICT를 치므로 이 제약이 없으면 중복 행이 생긴다.
-- spot_reaction의 (member_id, content_id, language)도 같은 이유로 유지한다.
--
-- uq_spot(content_id, language)도 남긴다 — 자식 FK가 더 이상 참조하지 않지만, spot 자체의 적재
-- upsert 단위가 그 조합이다.
-- ---------------------------------------------------------------------------
