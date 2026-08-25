-- 탈퇴 회원의 DM 메시지를 소프트 삭제로 바꾼다(SPEC-0011 R17, SPEC-0017 AC17-2).
--
-- 물리 삭제하면 상대방이 대화 맥락을 잃는다 — 자기 말만 남고 상대 말이 통째로 사라져
-- 대화가 독백이 된다. 글·댓글과 같은 취급으로 맞춘다.
--
-- **보관 기간은 그대로 물리 삭제다.** 정리 배치는 created_at으로만 고르므로 소프트 삭제된
-- 행도 180일 뒤 같이 사라진다 — 소프트 삭제가 메시지를 영구 보관으로 만들지 않는다.
ALTER TABLE community.community_message
    ADD COLUMN deleted_at timestamp with time zone;

-- 대화 화면의 주 조회 경로(conversation_id, created_at DESC, id DESC)에 삭제 필터가 붙는다.
-- 부분 인덱스로 두어 살아 있는 행만 담는다 — 지워진 행은 어차피 조회 대상이 아니고,
-- 보관 기간이 지나면 물리 삭제되므로 인덱스가 무한히 커지지 않는다.
CREATE INDEX idx_community_message_conversation_live
    ON community.community_message USING btree (conversation_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
