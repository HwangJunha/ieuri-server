-- 메시지 인용을 **별도 표로** 분리한다(SPEC-0026 R14).
--
-- V66이 `community_message`에 컬럼 6개를 직접 붙였다. 대상이 장소 답변 하나뿐일 때는
-- 가장 단순한 형태였지만, **인용 대상이 늘어날 때마다 컬럼이 6개씩 붙는 구조**다 —
-- 커뮤니티 글을 인용하면 post_id·post_title_snapshot…, 상품이면 또. 실제로 이 저장소에는
-- 그 전례가 있다(community_feed_item 인용이 그렇게 붙었다가 SPEC-0021에서 뜯겨 나갔다).
--
-- 대상 종류를 **컬럼이 아니라 값으로** 표현한다 — community_report가 target_type으로 이미
-- 쓰는 방식이며 새로 발명하지 않는다. 대상이 늘어도 quote_type 값만 늘고 DDL은 그대로다.
--
-- **한 메시지에 인용 여럿도 구조가 막지 않는다.** 지금 요구사항은 하나뿐이라 애플리케이션이
-- 하나만 쓰지만, 컬럼으로 박아 두면 바꿀 때 또 마이그레이션이다.
--
-- **클라이언트 표면은 바뀌지 않는다** — GraphQL의 Message.spotQaOrigin과
-- SendMessageInput.sourceAnswerId는 그대로다. 저장 구조만 옮긴다.

CREATE TABLE community.community_message_quote (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    message_id BIGINT      NOT NULL,

    -- 대상 종류. **target_id의 의미가 이 값에 따라 달라진다** — 지금은 SPOT_ANSWER뿐이다.
    -- 종류가 늘면 여기 값과 CHECK만 넓히면 되고 컬럼은 그대로다.
    quote_type VARCHAR(30) NOT NULL,

    -- 인용한 대상의 id. **FK가 아니다** — 가리키는 표가 행마다 다르고, 원본이 삭제돼도
    -- 인용은 남아야 한다. 맥락을 남기는 것이 이 표의 존재 이유이므로 링크로 두면 목적을
    -- 달성하지 못한다. 재조회하면 없을 수 있는 참고용 식별자다.
    target_id  BIGINT      NOT NULL,

    -- ── 스냅샷 ──
    --
    -- 값 복사다. 원본이 지워지거나 제목이 바뀌어도 이 값은 고정이다.

    -- 인용 카드의 제목 줄. 장소 답변이면 장소명이다.
    title_snapshot VARCHAR(255) NOT NULL,

    -- 카드의 본문 두 줄. 장소 답변이면 질문 발췌·답변 발췌다.
    -- base64(iv || ciphertext+tag) — 대화의 일부라 본문과 같이 암호화한다(POL-050).
    -- 평문 길이는 각 100자로 잘려 있다(AC-14.4).
    primary_excerpt_encrypted   TEXT NOT NULL,
    secondary_excerpt_encrypted TEXT,

    -- 대상에 딸린 부가 식별자. 장소 답변이면 카탈로그 id(클라이언트가 장소로 이동한다)와
    -- 질문 id다. 종류마다 쓰임이 달라 nullable이며, 없는 대상도 있다.
    context_id BIGINT,
    parent_id  BIGINT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 메시지가 보관 기간(6개월)으로 지워지면 인용도 함께 사라진다. 같은 도메인 안이라
    -- FK를 걸 수 있고, 지웠다고 하면서 데이터가 남는 일을 DB가 막는다.
    CONSTRAINT fk_community_message_quote_message
        FOREIGN KEY (message_id) REFERENCES community.community_message (id) ON DELETE CASCADE,

    CONSTRAINT ck_community_message_quote_type
        CHECK (quote_type IN ('SPOT_ANSWER'))
);

-- 메시지 목록을 그릴 때 인용을 **한 번에** 읽는 경로. 메시지마다 물으면 30건짜리 페이지에
-- 조회가 30번 붙는다(N+1).
CREATE INDEX idx_community_message_quote_message
    ON community.community_message_quote (message_id);

-- ── V66이 붙인 컬럼 제거 ─────────────────────────────────────────────
--
-- **기존 데이터를 옮기지 않는다.** V66은 이 브랜치에서만 적용됐고 실사용 인용이 없다.
ALTER TABLE community.community_message
    DROP CONSTRAINT IF EXISTS ck_community_message_spot_qa_origin;

DROP INDEX IF EXISTS community.idx_community_message_spot_qa_origin;

ALTER TABLE community.community_message
    DROP COLUMN spot_qa_catalog_id,
    DROP COLUMN spot_qa_spot_title,
    DROP COLUMN spot_qa_question_id,
    DROP COLUMN spot_qa_question_body_encrypted,
    DROP COLUMN spot_qa_answer_id,
    DROP COLUMN spot_qa_answer_body_encrypted;
