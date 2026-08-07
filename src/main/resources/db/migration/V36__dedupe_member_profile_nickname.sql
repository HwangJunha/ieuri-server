-- 닉네임 유니크 제약(V37) 준비. 중복이 남아 있으면 제약 생성이 실패하므로 먼저 해소한다.
-- 가장 먼저 만들어진 프로필이 이름을 지키고, 나중 것에 member_id를 붙여 갈라 준다.
-- member_id는 유일하므로 이 변환의 결과도 유일하다.
UPDATE member_profile p
SET nickname = left(p.nickname, 50 - (length(p.member_id::text) + 1)) || '-' || p.member_id
WHERE EXISTS (
    SELECT 1
    FROM member_profile earlier
    WHERE earlier.nickname = p.nickname
      AND (earlier.created_at, earlier.id) < (p.created_at, p.id)
);
