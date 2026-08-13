-- community 도메인: 1:1 다이렉트 메시지(SPEC-0017 R7~R9, R15~R17).
--
-- 그룹 대화는 비범위라 참여자를 별도 테이블로 두지 않고 두 컬럼으로 고정한다. 참여자
-- 테이블은 N:M을 위한 구조인데, 1:1에서는 조회마다 조인이 하나 더 붙을 뿐이다.
CREATE TABLE community_conversation (
    id             BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- **정렬된 쌍이다.** member_low < member_high를 CHECK로 강제해 (A,B)와 (B,A)가 같은
    -- 행이 되게 한다. 그래야 유일 제약 하나로 "이미 있는 대화"를 판정할 수 있다(AC7-2) —
    -- 정렬하지 않으면 두 방향을 각각 조회해야 하고 경합에서 중복 행이 생긴다.
    member_low     BIGINT      NOT NULL,
    member_high    BIGINT      NOT NULL,
    -- **대화가 시작된 피드를 방에 두지 않는다.** 같은 상대에게는 방이 하나뿐인데 피드는
    -- 여러 개라, 방에 고정하면 B가 A의 새 피드를 보고 말을 걸어도 상단에는 첫 피드가 남아
    -- **틀린 맥락**을 보여준다. 맥락은 메시지에 붙는다(community_message.feed_item_id) —
    -- 인스타그램 스토리 답장과 같은 방식이며, 대화가 이어지면 자연스럽게 위로 흘러간다.
    --
    -- 정렬 키(최근 활동순). 메시지가 없으면 생성 시각 그대로다 — 빈 대화가 존재할 수 있다.
    last_message_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 자기 자신과의 대화는 만들지 않는다(AC7-4). 정렬 규약도 여기서 함께 강제한다.
    CONSTRAINT ck_community_conversation_members CHECK (member_low < member_high),
    -- 같은 쌍에는 대화가 하나뿐이다. 경합 시 삽입 실패를 조회로 흡수한다(AC7-2).
    CONSTRAINT uq_community_conversation_pair UNIQUE (member_low, member_high)
);

-- 내 대화 목록. 두 컬럼 어느 쪽에도 내가 올 수 있어 인덱스를 둘 둔다 —
-- OR 조건은 단일 인덱스로 커버되지 않는다.
CREATE INDEX idx_community_conversation_low
    ON community_conversation (member_low, last_message_at DESC, id DESC);
CREATE INDEX idx_community_conversation_high
    ON community_conversation (member_high, last_message_at DESC, id DESC);

-- 메시지. 본문은 암호화해 저장한다(R16, POL-012).
--
-- **본문으로 검색할 수 없다.** 암호화 컬럼은 LIKE·전문 검색의 대상이 아니다. 대화 검색은
-- 이번 스펙 비범위이며, 필요해지면 재설계 대상이다 — 검색을 위해 평문 사본을 두면
-- 암호화가 무의미해지므로 그 선택은 하지 않는다.
CREATE TABLE community_message (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    sender_id       BIGINT      NOT NULL,
    -- base64(iv || ciphertext+tag). 평문 길이 판정은 도메인이 소유한다(1~1000자).
    --
    -- **첨부만 있는 메시지는 본문이 빈 문자열이다.** NULL이 아니라 빈 문자열인 이유는
    -- "본문 없음"과 "복호화 실패"를 구분하기 위해서다 — NULL을 허용하면 매퍼가 두 경우를
    -- 같은 자리에서 만나게 된다.
    body_encrypted  TEXT        NOT NULL,

    -- 인용한 피드(인스타 스토리 답장과 같은 방식). **방이 아니라 메시지에 붙는다** —
    -- 같은 상대와의 방은 하나인데 피드는 여러 개이기 때문이다.
    -- 피드가 지워져도 메시지는 남아야 하므로 SET NULL이다(대화 기록을 지우지 않는다).
    feed_item_id    BIGINT,

    -- 첨부 사진의 오브젝트 키. 텍스트 없이 사진만 보낼 수 있다.
    -- 글·피드와 같은 업로드 경로(presigned URL + 확정)를 쓴다(POL-042).
    photo_object_key VARCHAR(512),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES community_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_community_message_feed_item
        FOREIGN KEY (feed_item_id) REFERENCES community_feed_item (id) ON DELETE SET NULL,
    -- **빈 메시지를 만들지 않는다.** 본문·인용·사진이 전부 비면 보낼 것이 없는 전송이다.
    CONSTRAINT ck_community_message_not_empty CHECK (
        body_encrypted <> '' OR feed_item_id IS NOT NULL OR photo_object_key IS NOT NULL
    )
);

-- 대화의 메시지를 최신순으로 페이징하는 경로(R8). 커서가 (created_at, id)라 그대로 커버한다.
CREATE INDEX idx_community_message_conversation
    ON community_message (conversation_id, created_at DESC, id DESC);

-- 6개월 경과 메시지를 고르는 배치의 경로(R16)와, 탈퇴 회원의 메시지를 지우는 경로(R17).
CREATE INDEX idx_community_message_created ON community_message (created_at);
CREATE INDEX idx_community_message_sender ON community_message (sender_id);

-- DM 신고(R15). 여러 메시지를 **한 건의 신고로** 접수한다.
--
-- 기존 community_report와 나누는 이유는 다건이기 때문이다 — targetId가 단수인 그쪽에
-- 억지로 끼우면 같은 신고가 N건으로 접수되어 운영자에게 한 사건이 N건으로 보인다.
--
-- **누적 자동 숨김을 적용하지 않는다**(POL-049). 1:1 대화에는 서로 다른 신고자 5명이라는
-- 개념이 성립하지 않고, 임계값을 1로 낮추면 신고 한 번으로 상대를 침묵시키는 도구가 된다.
CREATE TABLE community_message_report (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id     BIGINT      NOT NULL,
    -- 한 신고는 한 대화에 대한 것이다(AC15-4).
    conversation_id BIGINT      NOT NULL,
    reason          VARCHAR(30) NOT NULL,
    -- 상세 사유는 로그·에러 메시지에 싣지 않는다(POL-013).
    detail          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_message_report_conversation
        FOREIGN KEY (conversation_id) REFERENCES community_conversation (id) ON DELETE CASCADE
);

-- 신고에 포함된 메시지. **운영자의 열람 범위가 이 표로 한정된다**(POL-049) —
-- 전후 맥락이나 대화 전체를 여는 경로를 만들지 않는다.
CREATE TABLE community_message_report_item (
    id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id  BIGINT      NOT NULL,
    message_id BIGINT      NOT NULL,
    -- 신고자별로 같은 메시지를 두 번 신고할 수 없다(AC15-6). reporter_id를 함께 들고 있는
    -- 이유는 이 판정이 신고자 단위이기 때문이다 — report_id로는 다른 신고 건의 중복을 못 본다.
    reporter_id BIGINT     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_community_message_report_item_report
        FOREIGN KEY (report_id) REFERENCES community_message_report (id) ON DELETE CASCADE,
    -- 메시지가 보관 기간(6개월)으로 지워지면 신고 항목도 함께 사라진다. 신고분만 남겨 두면
    -- 지웠다고 하면서 데이터가 남는 셈이라 보관 기간을 정한 의미가 없어진다.
    CONSTRAINT fk_community_message_report_item_message
        FOREIGN KEY (message_id) REFERENCES community_message (id) ON DELETE CASCADE,
    CONSTRAINT uq_community_message_report_item UNIQUE (reporter_id, message_id)
);

CREATE INDEX idx_community_message_report_item_report
    ON community_message_report_item (report_id);
