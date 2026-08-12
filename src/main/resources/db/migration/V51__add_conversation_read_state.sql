-- 대화별 읽음 지점(SPEC-0017 R8 보강). 안 읽은 메시지 수를 세기 위한 기준점이다.
--
-- **별도 테이블이 아니라 대화 행의 컬럼이다.** 참여자가 둘로 고정돼 있어(member_low,
-- member_high) 각자의 읽음 지점도 두 컬럼이면 된다 — 읽음 상태 테이블을 두면 대화 목록
-- 조회마다 조인이 하나 더 붙는데, 1:1에서는 그 대가로 얻는 것이 없다.
-- 그룹 대화가 생기면 그때 참여자 테이블과 함께 옮긴다(대화 테이블 자체가 재설계 대상이다).
--
-- **메시지 id를 기준으로 삼는다.** 시각(timestamp)을 쓰면 같은 밀리초에 도착한 메시지가
-- 읽음 경계에 걸릴 때 셋 중 무엇이 읽힌 것인지 정할 수 없다. id는 단조 증가라 경계가 하나다.
ALTER TABLE community_conversation
    -- member_low가 마지막으로 읽은 메시지. NULL은 "아직 아무것도 읽지 않았다"이며,
    -- 0이 아니다 — 0으로 두면 "id 0까지 읽었다"와 구분되지 않는다.
    ADD COLUMN last_read_message_id_low  BIGINT,
    ADD COLUMN last_read_message_id_high BIGINT;

-- 안 읽은 수는 "이 대화에서 내가 읽은 지점 이후, 내가 보내지 않은 메시지"를 센다.
-- 대화당 세므로 (conversation_id, id) 순서가 필요하고, sender_id는 커버링으로 붙여
-- 본문(암호화 컬럼)까지 읽지 않게 한다.
CREATE INDEX idx_community_message_unread
    ON community_message (conversation_id, id, sender_id);
