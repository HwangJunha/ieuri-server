-- tourism 도메인: 방문 지도 공유 링크(SPEC-0030 R12~R14).
--
-- **만료 시각을 두지 않는다**(스펙 §11 결정). 담긴 정보가 방문 칸 목록과 닉네임뿐이라 시간이
-- 지나도 위험이 커지지 않고, 만료를 두면 "친구가 링크를 나중에 열었더니 안 보인다"가 되어
-- 공유의 목적을 해친다. 무효화 수단은 폐기 하나다.
--
-- **폐기를 행 삭제가 아니라 revoked_at으로 표현한다.** 삭제하면 "폐기된 링크"와 "처음부터
-- 없던 토큰"을 구분할 수 없는데, 둘 다 SHARE_LINK_NOT_FOUND로 같게 응답하더라도 서버가
-- 그 둘을 구분하지 못하는 것과 응답을 같게 하는 것은 다른 문제다.
--
-- **회원당 유효한 링크는 하나다.** 발급을 반복해도 같은 토큰을 돌려주며(계약 §Mutation),
-- 부분 UNIQUE 인덱스가 그것을 강제한다 — 애플리케이션 조회로만 막으면 동시 발급에서 깨진다.
--
-- member_id에 FK를 걸지 않는 이유는 member_district_visit와 같다(컨텍스트 간 FK 금지).
-- 탈퇴 시 링크 무효화는 애플리케이션이 한다.

CREATE TABLE tour.map_share_link (
    id         bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    member_id  bigint       NOT NULL,

    -- 불투명 토큰. 추측 불가능해야 하므로 애플리케이션이 CSPRNG로 만든다.
    token      varchar(64)  NOT NULL,

    created_at timestamptz  NOT NULL DEFAULT now(),

    -- NULL이면 유효, 값이 있으면 폐기됨.
    revoked_at timestamptz,

    CONSTRAINT uq_map_share_link_token UNIQUE (token)
);

-- 회원당 유효한 링크 1개. 폐기된 행은 제약을 받지 않으므로 재발급이 가능하다.
CREATE UNIQUE INDEX uq_map_share_link_active_member
    ON tour.map_share_link (member_id)
    WHERE revoked_at IS NULL;
