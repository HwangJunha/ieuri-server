-- tourism 도메인: 칸 대표 이미지(SPEC-0030 R9·R10).
--
-- planner가 산출한 seed(landmarks-161-with-images.json)를 적재한다. 칸당 1~4건이며
-- display_order 0이 대표다.
--
-- **이미지 URL은 TourAPI의 tong.visitkorea.or.kr 호스트를 직접 참조한다.** 자체 스토리지로
-- 복사하려면 관광공사에 별도 신청이 필요해(검토 3~5일) 1차는 직접 참조로 간다. 그래서
-- 이 표에는 우리 스토리지 키가 아니라 외부 URL이 들어온다(POL-042 비대상).
--
-- **copyright는 이미지별이며 칸에서 상속하지 않는다** — 기존 SpotImage의 규약과 같다.
-- attribution_text는 두지 않는다: CopyrightType이 그 문구를 소유하며 저장하면 둘이 어긋난다.
--
-- 대표 이미지가 없는 칸도 조회에서 빠지지 않는다(R10). 그 경우 이 표에 행이 없을 뿐이고,
-- 특산품 이미지 14건이 아직 확보되지 않아 실제로 그런 칸이 생긴다.
--
-- **cell_id는 map_cell을 참조한다.** 칸이 161행짜리 표라 참조 대상이 되며(V74), 같은
-- tourism 컨텍스트 안이라 물리 FK를 건다 — 없는 칸의 이미지가 적재되는 일이 구조적으로 막힌다.

CREATE TABLE tour.map_cell_image (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    cell_id       bigint       NOT NULL,

    -- 0이 대표. 칸 안에서 유일하다.
    display_order integer      NOT NULL,

    image_url     varchar(500) NOT NULL,
    thumbnail_url varchar(500),

    -- 이미지가 무엇인지. "경복궁", "성주 참외".
    caption       varchar(255) NOT NULL,

    -- LANDMARK | SPECIALTY
    kind          varchar(20)  NOT NULL,

    -- TYPE1 | TYPE3 | UNSPECIFIED. CopyrightType의 이름과 같다.
    copyright     varchar(20)  NOT NULL,

    -- TourAPI contentid. 출처 추적용이며 조회 조건이 아니다.
    content_id    varchar(20),

    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_map_cell_image_cell
        FOREIGN KEY (cell_id) REFERENCES tour.map_cell (id) ON DELETE CASCADE,
    CONSTRAINT uq_map_cell_image_order UNIQUE (cell_id, display_order),
    CONSTRAINT ck_map_cell_image_kind CHECK (kind IN ('LANDMARK', 'SPECIALTY')),
    CONSTRAINT ck_map_cell_image_copyright CHECK (copyright IN ('TYPE1', 'TYPE3', 'UNSPECIFIED')),
    CONSTRAINT ck_map_cell_image_display_order CHECK (display_order >= 0)
);

-- 칸 목록(161행)과 칸 상세가 모두 "이 칸의 대표 이미지"를 찾는다.
-- uq_map_cell_image_order가 (cell_id, display_order) 선행이라 그 조회를 그대로 커버한다.
