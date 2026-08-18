-- community 도메인: 장소 질문/답변(SPEC-0026).
--
-- **V62 이후이므로 스키마 한정으로 쓴다** — connection-init-sql의 search_path가 Flyway
-- 커넥션에 닿지 않는다(DataSourceConfig 참조).
--
-- 질문·답변·답글을 각각 별도 테이블로 둔다. 한 테이블에 종류 컬럼으로 몰지 않는 이유는
-- 부모가 서로 다른 대상이기 때문이다 — 질문은 장소(catalog_id)에, 답변은 질문에, 답글은
-- 답변에 붙는다. 한 테이블이면 그 셋 중 어느 것도 FK로 강제할 수 없다.
--
-- catalog_id를 답변·답글에도 복사해 둔다. 작성 자격이 장소 단위라(R5) 판정 때마다 질문을
-- 거슬러 올라가지 않기 위해서다. 질문이 다른 장소로 옮겨 다니지 않으므로 이 사본은
-- 갱신 대상이 아니다.

-- ── 1. 질문 ─────────────────────────────────────────────────────────
--
-- community_post와 달리 catalog_id가 NOT NULL이다 — 질문은 언제나 장소에 대한 것이다(R1).
-- **FK를 걸지 않는다.** tour.spot_catalog는 다른 컨텍스트이며 컨텍스트 간 FK는 두지 않는다.
CREATE TABLE community.community_spot_question (
    id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    catalog_id   BIGINT      NOT NULL,
    author_id    BIGINT      NOT NULL,
    -- TEXT인 이유는 community_post.body와 같다 — 길이 판정(1~500)은 도메인이 소유한다.
    body         TEXT        NOT NULL,
    -- 작성자가 "충분히 알게 되었다"고 표시했는지(R8). 되돌리는 경로는 없다.
    resolved     BOOLEAN     NOT NULL DEFAULT FALSE,
    -- 숨겨진 답변을 제외한 답변 수. 신고로 답변이 가려지면 줄어든다.
    answer_count INTEGER     NOT NULL DEFAULT 0,
    -- 신고 누적으로 숨겨졌는지(R11). 숨겨져도 작성자 본인에게는 보인다(AC-11.5).
    hidden       BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_spot_question_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT ck_community_spot_question_answer_count CHECK (answer_count >= 0)
);

-- 목록 조회(R2): WHERE catalog_id = ? AND deleted_at IS NULL
--                ORDER BY resolved ASC, created_at DESC, id DESC.
-- **정렬 키 세 개가 전부 인덱스에 있어야 한다** — 커서가 그 셋을 담기 때문이다.
CREATE INDEX idx_community_spot_question_catalog
    ON community.community_spot_question (catalog_id, resolved ASC, created_at DESC, id DESC);

-- 탈퇴 시 전량 삭제 경로(R17).
CREATE INDEX idx_community_spot_question_author
    ON community.community_spot_question (author_id);

-- ── 2. 답변 ─────────────────────────────────────────────────────────
--
-- 질문 삭제가 답변을 함께 지운다(AC-10.3). 같은 도메인 안이므로 FK CASCADE로 강제한다.
CREATE TABLE community.community_spot_answer (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT      NOT NULL,
    -- 질문에서 복사한 값. 자격 판정이 장소 단위라 질문을 되읽지 않기 위한 것이다.
    catalog_id  BIGINT      NOT NULL,
    author_id   BIGINT      NOT NULL,
    body        TEXT        NOT NULL,
    hidden      BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_spot_answer_question
        FOREIGN KEY (question_id) REFERENCES community.community_spot_question (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_community_spot_answer_body_not_blank CHECK (btrim(body) <> '')
);

-- 답변 목록: WHERE question_id = ? AND deleted_at IS NULL ORDER BY created_at DESC, id DESC.
-- FK 자식 인덱스 역할도 겸한다 — 없으면 질문 1건 삭제마다 자식 전체 스캔이 발생한다(V19 선례).
CREATE INDEX idx_community_spot_answer_question
    ON community.community_spot_answer (question_id, created_at DESC, id DESC);

CREATE INDEX idx_community_spot_answer_author
    ON community.community_spot_answer (author_id);

-- ── 3. 답글 ─────────────────────────────────────────────────────────
--
-- **부모 답글 컬럼이 없다.** 답글에 답글을 달 수 없다는 결정(R7)을 구조로 고정한다 —
-- 컬럼을 두고 NULL로 비워 두면 나중에 누군가 채우기 시작한다. community_comment가
-- V33에서 겪은 일이며, 여기서는 계약이 거부(REPLY_TO_REPLY_NOT_ALLOWED)를 요구한다.
CREATE TABLE community.community_spot_answer_reply (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    answer_id  BIGINT      NOT NULL,
    catalog_id BIGINT      NOT NULL,
    author_id  BIGINT      NOT NULL,
    body       TEXT        NOT NULL,
    hidden     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_spot_answer_reply_answer
        FOREIGN KEY (answer_id) REFERENCES community.community_spot_answer (id)
        ON DELETE CASCADE,
    CONSTRAINT ck_community_spot_answer_reply_body_not_blank CHECK (btrim(body) <> '')
);

-- 답글은 오래된 순으로 함께 내려간다(AC-7.4). 페이지네이션이 없다.
CREATE INDEX idx_community_spot_answer_reply_answer
    ON community.community_spot_answer_reply (answer_id, created_at ASC, id ASC);

CREATE INDEX idx_community_spot_answer_reply_author
    ON community.community_spot_answer_reply (author_id);

-- ── 4. 사진 ─────────────────────────────────────────────────────────
--
-- 셋을 한 표에 모으고 소유자 종류를 컬럼으로 구분한다. community_post_photo처럼 대상마다
-- 표를 나누면 같은 모양의 표가 셋이 되고, 그 셋을 읽는 코드도 셋이 된다.
--
-- **대신 FK를 걸 수 없다** — owner_id가 가리키는 표가 행마다 다르기 때문이다. 부모가
-- 지워질 때 사진 행을 함께 지우는 것은 응용 계층의 일이다(질문·답변 삭제 경로).
--
-- 순서(position)가 계약의 일부라 배열 컬럼이 아니라 행으로 둔다 — community_post_photo와
-- 같은 근거다.
CREATE TABLE community.community_spot_qa_photo (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- QUESTION | ANSWER | REPLY. owner_id의 의미를 정한다.
    owner_type VARCHAR(10)  NOT NULL,
    owner_id   BIGINT       NOT NULL,
    -- 확인(confirm) 단계를 통과해 images/ 로 옮겨진 뒤의 최종 키.
    object_key VARCHAR(512) NOT NULL,
    -- 0부터 최대 4까지 — 사진은 대상당 최대 5장(R9).
    position   SMALLINT     NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_community_spot_qa_photo_owner_type
        CHECK (owner_type IN ('QUESTION', 'ANSWER', 'REPLY')),
    -- 최대 5장을 DB에서도 강제한다. 애플리케이션의 TOO_MANY_PHOTOS와 이중 방어다.
    CONSTRAINT ck_community_spot_qa_photo_position CHECK (position BETWEEN 0 AND 4),
    CONSTRAINT uq_community_spot_qa_photo_position UNIQUE (owner_type, owner_id, position)
);

CREATE INDEX idx_community_spot_qa_photo_owner
    ON community.community_spot_qa_photo (owner_type, owner_id, position);

-- ── 5. 신고 대상 확장 ───────────────────────────────────────────────
--
-- 질문·답변·답글에 기존 reportContent를 그대로 쓴다(R11). 열거형에 값만 더하면 **런타임
-- INSERT에서 걸린다** — 애플리케이션·스키마·엔티티가 모두 통과하고 DB만 거부하므로
-- 유닛테스트로는 잡히지 않는다(V50이 같은 이유로 같은 일을 했다).
--
-- 제약을 지우고 다시 만드는 이유는 Postgres가 CHECK 조건을 in-place로 고치는 문법을
-- 제공하지 않기 때문이다. 기존 행은 전부 새 조건을 만족하므로 재검증이 통과한다.
ALTER TABLE community.community_report
    DROP CONSTRAINT ck_community_report_target_type;

ALTER TABLE community.community_report
    ADD CONSTRAINT ck_community_report_target_type
        CHECK (target_type IN (
            'POST', 'COMMENT', 'MEMBER',
            'SPOT_QUESTION', 'SPOT_ANSWER', 'SPOT_ANSWER_REPLY'
        ));

-- ── 6. DM 출처 스냅샷 ───────────────────────────────────────────────
--
-- **값 복사이며 FK가 아니다**(R14). 원본이 지워져도 맥락이 남아야 하는데, 맥락을 남기는
-- 것이 이 컬럼들의 존재 이유이므로 링크로 두면 목적을 달성하지 못한다. question_id·
-- answer_id도 값으로만 남으며 살아있는 참조를 보장하지 않는다.
--
-- **대화에 붙는다.** 메시지가 아니라 대화의 속성인 이유는 출처가 최초 시작 시점에 한 번만
-- 정해지고 이후 소급되지 않기 때문이다(AC-13.3). 질문 출처는 대화를 여는 계기이지 매
-- 발화의 인용이 아니라, 수명이 방의 수명과 같다.
--
-- 발췌는 대화의 일부이므로 본문과 같이 암호화해 저장한다(POL-050). 그래서 두 컬럼이
-- 평문 길이 100자가 아니라 암호문을 담는 TEXT다.
ALTER TABLE community.community_conversation
    ADD COLUMN spot_qa_catalog_id BIGINT,
    ADD COLUMN spot_qa_spot_title VARCHAR(255),
    ADD COLUMN spot_qa_question_id BIGINT,
    -- base64(iv || ciphertext+tag). 평문은 100자로 잘린 질문 본문 앞부분이다(AC-14.4).
    ADD COLUMN spot_qa_question_body_encrypted TEXT,
    ADD COLUMN spot_qa_answer_id BIGINT,
    ADD COLUMN spot_qa_answer_body_encrypted TEXT;

-- 출처는 전부 있거나 전부 없다. 일부만 채워진 상태는 조회 시점에 "출처가 있는데 장소명이
-- 없다" 같은 표현 불가능한 응답이 된다 — GraphQL의 ConversationSpotQaOrigin은 모든 필드가
-- non-null이기 때문이다.
ALTER TABLE community.community_conversation
    ADD CONSTRAINT ck_community_conversation_spot_qa_origin CHECK (
        (spot_qa_catalog_id IS NULL
            AND spot_qa_spot_title IS NULL
            AND spot_qa_question_id IS NULL
            AND spot_qa_question_body_encrypted IS NULL
            AND spot_qa_answer_id IS NULL
            AND spot_qa_answer_body_encrypted IS NULL)
        OR (spot_qa_catalog_id IS NOT NULL
            AND spot_qa_spot_title IS NOT NULL
            AND spot_qa_question_id IS NOT NULL
            AND spot_qa_question_body_encrypted IS NOT NULL
            AND spot_qa_answer_id IS NOT NULL
            AND spot_qa_answer_body_encrypted IS NOT NULL)
    );
