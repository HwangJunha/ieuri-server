-- 탈퇴 회원의 DM 메시지를 소프트 삭제로 바꾼다(SPEC-0011 R17, SPEC-0017 AC17-2).
--
-- 물리 삭제하면 상대방이 대화 맥락을 잃는다 — 자기 말만 남고 상대 말이 통째로 사라져
-- 대화가 독백이 된다. 글·댓글과 같은 취급으로 맞춘다.
--
-- **보관 기간은 그대로 물리 삭제다.** 정리 배치는 created_at으로만 고르므로 소프트 삭제된
-- 행도 180일 뒤 같이 사라진다 — 소프트 삭제가 메시지를 영구 보관으로 만들지 않는다.
ALTER TABLE community.community_message
    ADD COLUMN deleted_at timestamp with time zone;

-- 읽기 경로 전부에 `deleted_at IS NULL`이 붙으므로 두 인덱스를 **부분 인덱스로 교체한다.**
-- 조건을 담지 않은 인덱스를 그대로 두면 어느 질의도 쓰지 않으면서 INSERT마다 갱신되고
-- 테이블과 함께 자란다. 지워진 행은 조회 대상이 아니고 보관 기간이 지나면 물리 삭제되므로
-- 부분 인덱스가 무한히 커지지도 않는다.

-- 1) 대화 화면의 주 조회 경로(findMessages·findLastMessagesOf).
DROP INDEX IF EXISTS community.idx_community_message_conversation;
CREATE INDEX idx_community_message_conversation
    ON community.community_message USING btree (conversation_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

-- 2) 안 읽은 수(countUnreadOf). **대화 목록을 그릴 때마다 도는 경로다** — 조건을 담지 않으면
--    후보 행마다 heap을 뒤져 deleted_at을 확인하게 되어 커버링 스캔을 잃는다.
DROP INDEX IF EXISTS community.idx_community_message_unread;
CREATE INDEX idx_community_message_unread
    ON community.community_message USING btree (conversation_id, id, sender_id)
    WHERE deleted_at IS NULL;

-- `idx_community_message_created`(created_at)는 그대로 둔다 — 보관 정리 배치가 쓰는데,
-- 그쪽은 **지워진 행도 골라야 하므로** 조건을 담으면 안 된다.
