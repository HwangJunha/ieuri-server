-- 닉네임은 회원 간 고유하다(SPEC-0011 R5). 중복 해소는 V36이 선행한다.
-- 확인-후-저장은 경합에서 두 요청을 함께 통과시키므로, 이 제약이 고유성의 최종 판정자다.
-- 애플리케이션은 제약 위반을 잡아 NICKNAME_ALREADY_TAKEN으로 옮긴다.
ALTER TABLE member_profile
    ADD CONSTRAINT uq_member_profile_nickname UNIQUE (nickname);
