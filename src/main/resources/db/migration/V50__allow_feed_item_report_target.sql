-- 신고 대상에 FEED_ITEM을 허용한다(SPEC-0017 R12).
--
-- community_report.target_type의 CHECK 제약이 V28에서 'POST','COMMENT','MEMBER'로 고정되어 있어,
-- 열거형에 값만 더하면 **런타임 INSERT에서 걸린다.** 애플리케이션·스키마·엔티티가 모두
-- 통과하고 DB만 거부하므로 유닛테스트로는 잡히지 않는다(신고 저장소를 스텁으로 두기 때문).
--
-- 제약을 지우고 다시 만드는 이유는 Postgres가 CHECK 제약의 조건을 in-place로 고치는 문법을
-- 제공하지 않기 때문이다. 기존 행은 전부 새 조건을 만족하므로 재검증이 통과한다.
--
-- **DM 메시지(MESSAGE)는 여기 넣지 않는다.** 다건 신고라 별도 테이블
-- (community_message_report)을 쓰며 이 열거형의 대상이 아니다(POL-049).
ALTER TABLE community_report
    DROP CONSTRAINT ck_community_report_target_type;

ALTER TABLE community_report
    ADD CONSTRAINT ck_community_report_target_type
        CHECK (target_type IN ('POST', 'COMMENT', 'MEMBER', 'FEED_ITEM'));
