-- tourism 도메인: 시군구 방문 기록(SPEC-0030 R3~R5).
--
-- **이 표에 없는 컬럼이 설계의 핵심이다.** `created_at`도, 좌표도, 판정 출처(사진/현재위치)도
-- 두지 않는다. 시각이 붙는 순간 "종로구 방문함"이 "8월 21일 14시 32분 종로구"가 되고, 그
-- 연속은 이동 경로가 되어 POL-045가 막으려는 위치 이력 그 자체가 된다.
--
-- 감사·디버깅 목적으로도 시각을 남기지 않는다. 필요해지면 그때 정책 결정을 거친다.
-- 그래서 이 표에는 다른 표라면 당연히 있을 created_at/updated_at이 의도적으로 없다.
--
-- 멱등성(R3)은 자연키 UNIQUE와 `ON CONFLICT DO NOTHING`으로 얻는다 — 애플리케이션이 먼저
-- 조회해 있는지 보는 방식은 동시 요청에서 깨진다.
--
-- **PK는 대리키이고 자연키는 UNIQUE로 둔다.** 복합 PK로도 같은 제약이 서지만, JPA 엔티티가
-- `@IdClass`/`@EmbeddedId`와 식별자 클래스를 요구해 보일러플레이트가 붙고 이 프로젝트의 다른
-- 표(`map_share_link`·`spot_catalog`·`kto_*`)와 스타일이 갈린다. 대가는 인덱스 두 개(PK +
-- UNIQUE)를 유지하는 것이며, 방문 기록은 회원당 최대 252행이라 그 비용이 문제가 되지 않는다.
--
-- **member_id에 FK를 걸지 않는다**(POL: 컨텍스트 간 FK 금지). 회원은 auth 스키마의 소유이며
-- tourism이 물리 FK로 묶으면 도메인 분리가 불가능해진다. 탈퇴 시 삭제는 애플리케이션이 한다.
--
-- district_code는 같은 tourism 컨텍스트라 FK를 건다. 시군구가 사라지면 그 방문 기록도
-- 가리킬 곳이 없다.

CREATE TABLE tour.member_district_visit (
    id            bigint     GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    member_id     bigint     NOT NULL,
    district_code varchar(5) NOT NULL,

    -- 멱등성의 근거. `ON CONFLICT (member_id, district_code)`가 이 제약을 지목한다.
    CONSTRAINT uq_member_district_visit UNIQUE (member_id, district_code),

    CONSTRAINT fk_member_district_visit_district
        FOREIGN KEY (district_code) REFERENCES tour.district (code) ON DELETE CASCADE
);

-- 공유 지도 조회가 "이 회원의 방문 전체"를 읽는다. `uq_member_district_visit`의 선행 컬럼이
-- member_id라 그 조회를 그대로 커버하므로 별도 인덱스를 두지 않는다.
--
-- 반대 방향(이 시군구를 방문한 회원)은 이 스펙에 조회가 없어 인덱스를 만들지 않는다.
-- FK 검증은 참조되는 쪽(district.code = PK)의 인덱스를 쓰므로 여기서는 필요 없다.
