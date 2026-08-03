-- tourism 도메인: 축제 전용 정보 적재(SPEC-0007 R-1·R-2).
--
-- 축제는 이미 areaBasedSyncList2 경로로 spot에 category=FESTIVAL_EVENT로 적재되고 있다. 그러나 그
-- 오퍼레이션은 **시작일·종료일을 주지 않아** "지금 유효한 행사"를 가려낼 수 없다. searchFestival2가
-- 주는 축제 전용 4개 필드를 이 자식 테이블에 담고, 종료된 행사를 매일 정리한다.
--
-- **spot에 컬럼을 늘리지 않는다**(R-1). 축제는 전체 관광지의 일부이므로 4개 컬럼이 나머지 전 행에서
-- null로 남는다. 제목·주소·좌표 등 공통 필드도 여기 중복 적재하지 않는다 — spot에 이미 있다.
--
-- 같은 축제라도 언어별 contentId가 다르므로(V13이 이미 쓰는 전제) 언어마다 독립된 행을 갖는다.
CREATE TABLE spot_festival (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_id       VARCHAR(32) NOT NULL,
    language         VARCHAR(2)  NOT NULL,
    -- **DATE로 저장하고 원본 문자열(YYYYMMDD)로 두지 않는다.** R-8의 종료 판정이 문자열 비교가 되면
    -- 형식이 어긋난 값에서 조용히 틀린 결과가 나온다. 해석 실패는 적재 시점에 드러나야 하며,
    -- 그런 항목은 애초에 적재하지 않는다(R-4).
    event_start_date DATE        NOT NULL,
    -- 정리 판정의 기준이다(R-8): event_end_date < 실행일이면 종료된 축제.
    event_end_date   DATE        NOT NULL,
    -- 진행형태·축제형태. **공식 코드 체계가 존재하지 않으므로 원문 문자열 그대로 적재한다**(R-5,
    -- POL-023). enum으로 만들지 않고 이 값으로 검색·필터·정렬도 만들지 않는다. 값이 없을 수 있어
    -- nullable이며, 빈 문자열은 어댑터가 null로 정규화한다.
    progress_type    VARCHAR(50),
    festival_type    VARCHAR(50),
    -- spot과 같은 관례. 이번 실행이 이 행을 건드렸는지의 기록.
    last_synced_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- upsert 단위(R-7). 같은 축제를 반복 수집해도 행이 늘지 않는다.
    CONSTRAINT uq_spot_festival UNIQUE (content_id, language),
    CONSTRAINT ck_spot_festival_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    -- 도메인 FestivalPeriod가 강제하는 불변식을 DB에서도 지킨다(AC-15). 기간이 뒤집힌 행이 들어오면
    -- 종료 판정이 무의미해지므로 조회 시점이 아니라 쓰기 시점에 드러나게 한다.
    CONSTRAINT ck_spot_festival_period CHECK (event_end_date >= event_start_date)
);

-- 축제 행은 대응하는 관광지 행 없이 존재할 수 없고, 관광지 행이 사라지면 함께 사라진다(R-2).
--
-- **FK를 spot.id가 아니라 (content_id, language)에 건다.** spot의 자연키가 그 조합이고, V17이 이미
-- spot_detail·spot_reaction 등 5개 자식 테이블을 같은 방식으로 연결했다. 같은 축제라도 언어별
-- contentId가 달라 surrogate id를 쓰더라도 결국 언어당 한 행이 되므로 얻는 것이 없고 관례만 깨진다.
--
-- CASCADE는 양방향으로 쓰인다: 관광지 정리(SPEC-0004 R-5)가 spot 행을 지울 때 축제 정보가 따라가고,
-- 종료 축제 삭제(R-8)도 spot 행을 지우는 것으로 구현되어 이 CASCADE를 탄다.
ALTER TABLE spot_festival
    ADD CONSTRAINT fk_spot_festival_spot
    FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language)
    ON DELETE CASCADE;

-- R-8의 종료 축제 조회가 매일 언어별로 수행된다. language 선행 — 정리는 항상 한 언어로 한정된다(AC-22).
CREATE INDEX idx_spot_festival_end_date ON spot_festival (language, event_end_date);

-- ---------------------------------------------------------------------------
-- spot.content_type_id를 NOT NULL로 강제한다.
--
-- **적재 실측이 근거다(2026-08-03).** 전량 적재 후 질의 결과 121,913건(KO 56,897 · EN 21,751 ·
-- JA 21,601 · ZH 21,664) 전부 content_type_id가 채워져 있었고, 언어마다 8종의 값이 나타났다.
-- NULL이 한 건도 없으므로 **백필이 필요 없다.**
--
-- V13은 이 컬럼을 nullable로 두었다. 당시 근거는 "해석 실패 시 폴백"이었으나, 폴백되는 것은
-- 도메인 category이지 원본 코드가 아니다 — 원본이 없으면 폴백 여부를 사후 질의할 수단 자체가
-- 사라지므로(V13이 이 컬럼을 둔 이유) nullable은 목적과 어긋난다.
--
-- **TourAPI가 값을 주지 않는 항목은 이제 적재하지 않는다.** 어댑터(TourApiSyncClient)가 드랍하며,
-- 이 제약은 그 규율이 우회되지 않도록 쓰기 시점에 한 번 더 막는다 — spot의 다른 NOT NULL 컬럼
-- (thumbnail_image_url 등)이 이미 같은 역할을 한다.
--
-- 이 컬럼을 유니크 키에 포함하는 건은 **이 마이그레이션의 범위가 아니다.** SPEC-0004 스펙 문서에
-- 그 요구가 없어 이슈 #35에 [AMEND]로 올렸으며, 키 전략 전환과 함께 별도로 다룬다. 다만 NOT NULL이
-- 그 논의의 선결 조건이었다 — Postgres 유니크 제약은 NULL을 서로 다른 값으로 취급하므로, nullable인
-- 채로 키에 넣으면 같은 (content_id, language) 행이 중복 삽입된다.
ALTER TABLE spot
    ALTER COLUMN content_type_id SET NOT NULL;
