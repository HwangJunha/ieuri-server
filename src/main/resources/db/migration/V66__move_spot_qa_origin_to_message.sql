-- DM 출처 스냅샷을 대화에서 **메시지로 옮긴다**(SPEC-0026 R13·R14 개정).
--
-- V64는 대화에 붙였다. 근거는 "출처가 최초 시작 시점에 한 번 정해지고 소급되지 않는다"였고
-- 그 자체는 지금도 맞다. 그런데 실사용에서 **한 대화 안에서 다른 질문을 또 인용하는** 상황이
-- 나왔다(같은 상대에게 방은 하나인데 물어볼 질문은 여럿이다). 방에 자리가 하나뿐이라
-- 클라이언트가 "'릴랙스위크' 질문 관련해서 문의드려요."처럼 **평문 메시지로 우회**했고,
-- 그 문자열에서는 질문 id를 되찾을 수 없어 이동·복원·다국어가 전부 불가능했다.
--
-- 그래서 인용은 메시지의 속성이 된다 — V49가 피드 인용에 내린 결론과 같다.
--
-- **대화 쪽 컬럼은 남기지 않는다.** 두 곳에 두면 같은 카드가 화면에 둘 뜨고 저장이 어긋날
-- 자리가 생긴다. "이 대화가 무엇에서 시작됐나"는 가장 오래된 인용 메시지가 답한다.

-- ── 1. 메시지에 출처 컬럼 ────────────────────────────────────────────
--
-- 값 복사이며 FK가 아니다 — 원본이 지워져도 맥락이 남아야 하고, 그것이 이 컬럼들의 존재
-- 이유다. question_id·answer_id도 값으로만 남으며 살아있는 참조를 보장하지 않는다.
ALTER TABLE community.community_message
    ADD COLUMN spot_qa_catalog_id BIGINT,
    ADD COLUMN spot_qa_spot_title VARCHAR(255),
    ADD COLUMN spot_qa_question_id BIGINT,
    -- base64(iv || ciphertext+tag). 평문은 100자로 자른 질문 본문 앞부분이다(AC-14.4).
    -- 발췌도 대화의 일부라 본문과 같이 암호화한다(POL-050).
    ADD COLUMN spot_qa_question_body_encrypted TEXT,
    ADD COLUMN spot_qa_answer_id BIGINT,
    ADD COLUMN spot_qa_answer_body_encrypted TEXT;

-- 전부 있거나 전부 없다. 일부만 채워지면 조회 시점에 "출처가 있는데 장소명이 없다" 같은
-- 표현 불가능한 응답이 된다 — GraphQL의 ConversationSpotQaOrigin은 모든 필드가 non-null이다.
ALTER TABLE community.community_message
    ADD CONSTRAINT ck_community_message_spot_qa_origin CHECK (
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

-- "이 대화가 무엇에서 시작됐나"를 답하는 경로: 대화의 출처 있는 메시지를 오래된 순으로
-- 하나 읽는다. 부분 인덱스라 출처 없는 일반 메시지는 색인에 들어가지 않는다.
CREATE INDEX idx_community_message_spot_qa_origin
    ON community.community_message (conversation_id, created_at, id)
    WHERE spot_qa_catalog_id IS NOT NULL;

-- ── 2. 대화의 출처 컬럼 제거 ─────────────────────────────────────────
--
-- **기존 데이터를 메시지로 옮기지 않는다.** 대화의 출처는 "대화를 연 계기"라 어느 메시지에
-- 속한다고 말할 수 없고(첫 메시지 전에도 존재했다), 이 기능은 아직 배포 전이라 보존할
-- 실사용 데이터가 없다. 옮기는 코드를 쓰면 그 자체가 검증 대상이 되는데 지킬 값이 없다.
ALTER TABLE community.community_conversation
    DROP CONSTRAINT IF EXISTS ck_community_conversation_spot_qa_origin;

ALTER TABLE community.community_conversation
    DROP COLUMN spot_qa_catalog_id,
    DROP COLUMN spot_qa_spot_title,
    DROP COLUMN spot_qa_question_id,
    DROP COLUMN spot_qa_question_body_encrypted,
    DROP COLUMN spot_qa_answer_id,
    DROP COLUMN spot_qa_answer_body_encrypted;
