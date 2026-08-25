-- SPEC-0032 R31: 닉네임 유니크 제약 제거.
--
-- 닉네임은 어디서도 조회 키가 아니다 — findByNickname이 없고, 멘션·닉네임 검색·닉네임 기반
-- DM 주소 지정도 없다. 전부 표시용 투영이라 참조 무결성 관점에서 제거가 안전하다.
--
-- 제거하는 이유는 provider(카카오 등)가 준 닉네임을 그대로 쓰기 위해서다(R29) — 같은 값이
-- 여러 회원에게 들어오는 것이 정상이 된다. 동명이인 구별은 회원 식별자가 맡는다(R33).
--
-- uq_member_profile_member(member_id)는 남긴다 — 그쪽은 findByMemberId가 한 행을 돌려주는
-- 근거라 성격이 다르다.
ALTER TABLE auth.member_profile
    DROP CONSTRAINT IF EXISTS uq_member_profile_nickname;
