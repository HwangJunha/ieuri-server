-- 피드 제거(SPEC-0021 R3·R4·R5·R6·R7). DM은 남는다.
--
-- **순서가 전부다.** 아래 다섯 단계는 서로 의존하며, 뒤집으면 실행 시점에 실패한다.
--   1) 피드 사진 키를 보존한다  — 테이블을 지운 뒤에는 무엇이 피드 것이었는지 알 수 없다(R6)
--   2) 메시지의 피드 인용을 끊는다 — 빈 메시지 제약을 먼저 떼야 컬럼을 지울 수 있고,
--                                  FK가 남아 있으면 피드 테이블을 지울 수 없다(AC5.2)
--   3) 피드 테이블을 지운다      — 자식(좋아요·사진)이 먼저, 본체가 나중
--   4) FEED_ITEM 신고를 지운다   — 행이 남으면 CHECK 제약 환원이 실패한다(AC7.3)
--   5) 신고 대상 제약을 환원한다

-- ── 1. 피드 사진 키 보존 (R6) ────────────────────────────────────────
--
-- 스토리지 원본 삭제는 이 마이그레이션의 일이 아니다 — 운영 작업이며 ieuri-server#83이 맡는다.
-- 여기서는 **판정에 필요한 입력만 남긴다.** 삭제 대상 판정(커뮤니티 글이 같은 키를 쓰는지)은
-- 애플리케이션 계층이 하므로 SQL에 묻지 않는다.
--
-- 이 테이블은 #83이 스토리지를 비운 뒤 지워진다. 그때까지는 남은 일의 목록이다.
CREATE TABLE community_orphan_photo_object (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    object_key VARCHAR(512) NOT NULL,
    -- 어디서 나온 키인지. 지금은 피드뿐이지만, 뒤에 다른 제거 작업이 생기면 같은 표를 쓴다.
    source     VARCHAR(32)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_community_orphan_photo_object UNIQUE (object_key)
);

INSERT INTO community_orphan_photo_object (object_key, source)
SELECT DISTINCT object_key, 'FEED_ITEM'
FROM community_feed_item_photo
ON CONFLICT (object_key) DO NOTHING;

-- ── 2. 메시지의 피드 인용 제거 (R3·R4) ───────────────────────────────
--
-- 컬럼을 지우기 전에 **빈 메시지 제약을 먼저 떼어낸다.** ck_community_message_not_empty가
-- feed_item_id IS NOT NULL을 유효 조건으로 인정하고 있어, 컬럼만 먼저 지우면 남은 행이
-- 제약을 위반한다.
--
-- **다시 만들지 않는다.** 좁힌 조건(본문 또는 사진)으로 재추가하면, 인용만 있던 과거 메시지가
-- 정확히 그 조건에 걸린다 — R4가 "삭제하지 않고 남긴다"고 정한 바로 그 행들이다. 도메인은
-- 이들을 복원할 수 있도록 Message.restore에서 빈 본문을 허용하게 바꿨는데(AC4.1), 저장소가
-- 같은 행을 거부하면 두 결정이 서로 모순된다.
--
-- 빈 메시지 금지는 **생성 시점의 불변식이고 Message.create가 소유한다**(AC4.3). 저장소 제약은
-- 과거 데이터와 양립해야 하므로 여기서는 내려놓는다.
ALTER TABLE community_message
    DROP CONSTRAINT ck_community_message_not_empty;

ALTER TABLE community_message
    DROP CONSTRAINT fk_community_message_feed_item;

ALTER TABLE community_message
    DROP COLUMN feed_item_id;

-- ── 3. 피드 테이블 삭제 (R5) ─────────────────────────────────────────
--
-- 자식이 먼저다. CASCADE로 한 번에 지울 수도 있지만, 무엇이 지워지는지 이 파일에 적혀 있는
-- 편이 낫다 — 나중에 읽는 사람이 CASCADE의 도달 범위를 추측하지 않아도 된다.
DROP TABLE community_feed_item_like;
DROP TABLE community_feed_item_photo;
DROP TABLE community_feed_item;

-- ── 4·5. 신고 대상에서 FEED_ITEM 제거 (R7) ───────────────────────────
--
-- 행을 먼저 지우고 제약을 환원한다. 순서를 뒤집으면 남은 행이 새 제약의 재검증에 걸려
-- ADD CONSTRAINT가 실패한다.
DELETE FROM community_report WHERE target_type = 'FEED_ITEM';

ALTER TABLE community_report
    DROP CONSTRAINT ck_community_report_target_type;

ALTER TABLE community_report
    ADD CONSTRAINT ck_community_report_target_type
        CHECK (target_type IN ('POST', 'COMMENT', 'MEMBER'));
