-- 관광지 하위 데이터를 spot에 FK로 연결하고, spot 삭제 시 함께 지운다(SPEC-0004 R-5 후속).
--
-- **V16이 FK를 걸지 않은 판단을 뒤집는다.** 당시 근거는 "상세는 조회된 것만 누적되므로 spot에 없는
-- contentId도 적재될 수 있다"였는데, 실제로는 그렇지 않다 — spotDetail은 목록에서 받은 id로만
-- 조회되고, 목록은 spot 테이블에서 나온다. 따라서 상세의 (content_id, language)는 항상 spot에 있다.
--
-- FK가 없으면 정리 단계가 spot 행을 지울 때 상세·반응이 고아로 남는다. 그 데이터는 조회 경로가
-- 없어 영원히 도달 불가능하면서 용량만 차지한다. ON DELETE CASCADE로 같이 지우는 편이 옳다.
--
-- **도메인 간 FK 금지 규칙과 충돌하지 않는다.** spot·spot_detail·spot_reaction은 전부 tourism
-- 도메인이며, 프로젝트 규약은 "도메인 **내부** FK는 권장"이다. spot_reaction의 member_id는 auth
-- 도메인을 가리키므로 여전히 FK를 두지 않는다(V11의 결정 유지).
--
-- 적용 시점에 세 하위 테이블이 모두 비어 있음을 확인했다(spot_detail 0건, spot_reaction 0건).
-- 고아 행이 있으면 FK 생성이 실패하므로, 데이터가 쌓인 뒤에는 정리 후에야 걸 수 있다.
--
-- **V16 주석 정정**: V16은 갤러리·이용안내의 유니크 제약이 "중복 행을 막는다(AC-41)"고만 적었으나,
-- 그 두 테이블은 "전량 교체"(DELETE 후 삽입)라 동시 실행 시 제약 위반으로 **실패**한다 — 멱등이
-- 아니다. 그래서 어댑터의 삽입을 ON CONFLICT DO UPDATE로 두어 실제로 멱등하게 만들었다.
-- 적용된 마이그레이션은 불변이므로 V16을 고치지 않고 여기에 기록한다.

-- spot의 자연키에 FK를 걸려면 그 조합에 유니크 제약이 있어야 한다 — uq_spot이 이미 제공한다.

ALTER TABLE spot_detail
    ADD CONSTRAINT fk_spot_detail_spot
    FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language)
    ON DELETE CASCADE;

ALTER TABLE spot_detail_image
    ADD CONSTRAINT fk_spot_detail_image_spot
    FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language)
    ON DELETE CASCADE;

ALTER TABLE spot_detail_intro
    ADD CONSTRAINT fk_spot_detail_intro_spot
    FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language)
    ON DELETE CASCADE;

ALTER TABLE spot_detail_info
    ADD CONSTRAINT fk_spot_detail_info_spot
    FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language)
    ON DELETE CASCADE;

-- 좋아요/싫어요도 같이 지운다. 존재하지 않는 관광지에 대한 반응은 의미가 없고,
-- likeCount 집계가 유령 행을 세는 것도 막는다.
ALTER TABLE spot_reaction
    ADD CONSTRAINT fk_spot_reaction_spot
    FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language)
    ON DELETE CASCADE;

-- CASCADE 삭제는 자식 쪽 (content_id, language) 인덱스를 탄다. 상세 계열은 유니크 제약이
-- 그 조합을 선행으로 포함해 커버되지만, spot_reaction의 유니크는 (member_id, ...) 선행이라
-- 커버되지 않는다 — 없으면 spot 1행 삭제마다 spot_reaction 전체 스캔이 발생한다.
CREATE INDEX idx_spot_reaction_content ON spot_reaction (content_id, language);
