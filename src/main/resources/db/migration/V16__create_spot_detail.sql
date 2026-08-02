-- tourism 도메인: 관광지 상세 write-behind 적재(SPEC-0004 R-13~R-18).
--
-- **오퍼레이션별로 테이블을 나눈다**([TBD-7] = (b), 이슈 #25에서 확정).
-- 본문·갤러리·운영정보·이용안내를 한 테이블에 묶으면(=(a)) 요청되지 않은 필드까지 TourAPI를 쳐야
-- 하므로 SPEC-0003 R7(필드별 지연 조회)과 충돌한다. 우리가 데이터 원본이 아닌 이상 "필요할 때만
-- 가져온다"를 적재 단계에서 깨뜨릴 이유가 없다. 현재 Valkey 키 분리(detail/gallery/intro/info)와
-- 그대로 대응된다.
--
-- 그 대가로 **요청 내 소스 혼재**(런타임 계약 위험 7번)를 감수한다 — 본문은 DB에서 즉시 오는데
-- gallery는 라이브 TourAPI로 나가는 조합이 생긴다.
--
-- spot 테이블과 **FK를 걸지 않는다.** 상세는 조회된 것만 누적되므로 spot에 없는 contentId도 적재될
-- 수 있고(배치 미동기화 항목), 반대로 spot 정리로 행이 사라져도 상세는 남아야 한다. 두 적재 경로의
-- 수명이 다르다.
--
-- 공통 규약:
--   - (content_id, language[, category, display_order]) 유니크 = upsert 단위. 동시 조회로 같은 항목이
--     여러 번 큐에 들어가도 중복 행이 생기지 않는다(AC-41). 갤러리·이용안내는 "전량 교체"라
--     DELETE 후 삽입하는데, 그 삽입도 ON CONFLICT로 둬야 동시 교체가 제약 위반으로 실패하지 않는다.
--   - fetched_at = upstream에서 가져온 시각. 신선도 판정(R-17·[TBD-6])의 기준이다.

-- 상세 본문(detailCommon2 1콜에 대응).
CREATE TABLE spot_detail (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_id          VARCHAR(32)  NOT NULL,
    language            VARCHAR(2)   NOT NULL,
    title               VARCHAR(255) NOT NULL,
    category            VARCHAR(30)  NOT NULL,
    thumbnail_image_url VARCHAR(500) NOT NULL,
    -- 관광지 설명. 길이 제한이 없어 TEXT로 둔다.
    overview            TEXT,
    address             VARCHAR(500),
    homepage            TEXT,
    tel                 VARCHAR(255),
    copyright           VARCHAR(20)  NOT NULL,
    -- likeCount는 적재하지 않는다(AC-38) — spot_reaction에서 매번 실시간 집계한다.
    fetched_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot_detail UNIQUE (content_id, language),
    CONSTRAINT ck_spot_detail_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH'))
);

-- 갤러리(detailImage2 1콜). 한 관광지에 여러 장이므로 행이 여럿이다.
-- display_order로 upstream 반환 순서를 보존한다 — 순서가 바뀌면 사용자에게 다른 대표 이미지가 보인다.
CREATE TABLE spot_detail_image (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_id    VARCHAR(32)  NOT NULL,
    language      VARCHAR(2)   NOT NULL,
    display_order INTEGER      NOT NULL,
    image_url     VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    caption       VARCHAR(500),
    copyright     VARCHAR(20)  NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot_detail_image UNIQUE (content_id, language, display_order),
    CONSTRAINT ck_spot_detail_image_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH'))
);

-- 운영 정보(detailIntro2 1콜). **category가 키에 포함된다** — upstream 응답이 카테고리별로 갈리고
-- Valkey 키도 카테고리를 포함하기 때문이다.
--
-- 이 테이블은 "조회했으나 값이 없음"도 행으로 남긴다(전 컬럼 null). 그러지 않으면 값 없는 항목을
-- 매 조회마다 미스로 판정해 TourAPI를 계속 두드린다 — Valkey의 CachedIntro 래퍼와 같은 이유다.
CREATE TABLE spot_detail_intro (
    id                   BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_id           VARCHAR(32) NOT NULL,
    language             VARCHAR(2)  NOT NULL,
    category             VARCHAR(30) NOT NULL,
    business_hours       TEXT,
    closed_days          TEXT,
    parking              TEXT,
    inquiry_phone        TEXT,
    fee                  TEXT,
    pet_allowed          TEXT,
    credit_card_accepted TEXT,
    fetched_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot_detail_intro UNIQUE (content_id, language, category),
    CONSTRAINT ck_spot_detail_intro_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH'))
);

-- 이용안내 항목(detailInfo2 1콜). intro와 마찬가지로 category가 키에 포함된다.
-- 항목이 여럿이라 display_order로 순서를 보존한다.
CREATE TABLE spot_detail_info (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content_id    VARCHAR(32)  NOT NULL,
    language      VARCHAR(2)   NOT NULL,
    category      VARCHAR(30)  NOT NULL,
    display_order INTEGER      NOT NULL,
    name          VARCHAR(255) NOT NULL,
    text          TEXT         NOT NULL,
    fetched_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spot_detail_info UNIQUE (content_id, language, category, display_order),
    CONSTRAINT ck_spot_detail_info_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH'))
);

-- 갤러리·이용안내는 (content_id, language[, category])로 **여러 행을 한 번에** 읽는다.
-- 유니크 인덱스가 그 조합을 선행으로 포함하므로 조회는 커버되지만, "빈 목록"과 "미조회"를 구분하려면
-- 존재 여부 판정이 필요하다 — 그 판정도 같은 인덱스를 탄다.
CREATE INDEX idx_spot_detail_image_lookup ON spot_detail_image (content_id, language);
CREATE INDEX idx_spot_detail_info_lookup ON spot_detail_info (content_id, language, category);
