-- community 도메인: 상단 카테고리 칩 구성(플랫폼화).
--
-- 커뮤니티 상단의 가로 칩 목록을 **앱마다 다르게** 내려주기 위한 테이블이다.
-- 앱이 여러 개 출시되면 상단 구성이 서로 달라야 한다 — 관광 앱은 맛집·축제를, 다른 앱은
-- 쇼핑·숙소를 앞세울 수 있어야 하고, 그 차이가 서버 배포 없이 데이터로 조정돼야 한다.
--
-- **표시 문구는 저장하지 않는다.** category enum 값과 순서만 내려주고 "맛집"·"Food"·"グルメ"는
-- 앱이 매핑한다(SpotCategory·PostTopic과 같은 방식, POL-023). 문구를 서버가 들면 언어별
-- 레코드와 어드민 화면이 따라붙는데, 칩이 쓰는 카테고리는 관광지가 이미 갖고 있는 축이라
-- 그럴 이유가 없다.
--
-- app_key는 이 저장소에서 앱을 가리키는 **첫 식별자**다. 지금은 헤더(X-App-Key)로 받아
-- 이 컬럼과 맞춘다. 회원·인증과 무관하므로 비로그인 사용자에게도 칩이 보인다.
CREATE TABLE community_chip (
    id       BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- 어느 앱의 구성인가. 헤더로 받은 값과 대조한다.
    app_key  VARCHAR(50) NOT NULL,
    -- **어느 언어의 구성인가.** 카테고리는 언어마다 가용 집합이 다르다 — TRAVEL_COURSE(여행코스)는
    -- 국문 서비스에만, TRANSPORT(교통)는 외국어 서비스에만 있다(TourAPI contentTypeId 체계).
    -- 언어 없이 한 벌만 두면 어느 한쪽 사용자에게 존재하지 않는 카테고리 칩이 뜬다.
    language VARCHAR(2)  NOT NULL,
    -- 칩이 거르는 대상 카테고리. TargetCategory(=SpotCategory)의 값이다.
    category VARCHAR(30) NOT NULL,
    -- 표시 순서. 작은 값이 왼쪽이다.
    position SMALLINT    NOT NULL,
    -- 잠시 내리고 싶을 때 행을 지우지 않고 끌 수 있게 한다 — 순서를 그대로 둔 채
    -- 다시 켤 수 있어야 시즌 운영이 편하다.
    enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 한 앱에 같은 카테고리가 두 번 뜨지 않는다.
    CONSTRAINT uq_community_chip UNIQUE (app_key, language, category),
    CONSTRAINT ck_community_chip_language CHECK (language IN ('EN', 'KO', 'JA', 'ZH')),
    CONSTRAINT ck_community_chip_category
        CHECK (category IN ('TOURIST_SPOT', 'CULTURAL_FACILITY', 'FESTIVAL_EVENT',
                            'TRAVEL_COURSE', 'LEISURE_SPORTS', 'LODGING',
                            'SHOPPING', 'RESTAURANT', 'TRANSPORT')),
    CONSTRAINT ck_community_chip_app_key_not_blank CHECK (btrim(app_key) <> '')
);

-- 조회는 언제나 "이 앱·이 언어의 켜진 칩을 순서대로":
--   WHERE app_key = ? AND language = ? AND enabled ORDER BY position, id
CREATE INDEX idx_community_chip_app_language
    ON community_chip (app_key, language, position, id);
