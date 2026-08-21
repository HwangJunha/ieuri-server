-- tourism 도메인: 원본 행정구역 2계층 정규화 + 전국 지도의 표시 칸(SPEC-0030 R7·R8).
--
-- 세 층으로 나눈다. **원본과 우리 것을 섞지 않는 것이 이 구조의 요지다.**
--
--   ① tour.area      17행   KTO 원본. areaCd/areaNm 그대로
--   ② tour.district  252행  KTO 원본. sigunguCd/sigunguNm + 경계
--   ③ tour.map_cell  161행  **우리가 만든 표시 단위.** 원본이 아니다
--
-- 원본은 한국관광공사 `관광지_시군구_코드정보_v1.0`이며 `areaCd(17) → sigunguCd(252)`
-- 2계층이다. V44가 그 두 계층을 한 표에 담으면서 시도 이름 4개 언어가 252행에 중복 저장됐다
-- (서울은 같은 값이 25번). 여기서 그것을 되돌린다.
--
-- **③이 별도인 이유는 그것이 우리 결정이기 때문이다.** "광역시는 통째로 1칸, 구를 가진
-- 일반시는 시 단위 1칸"은 KTO가 정한 것이 아니라 이 서비스의 지도 표현이다. 원본에 섞으면
-- 규칙을 바꿀 때 원본을 고쳐야 하고, 나중에 "서울을 25칸으로 쪼개 보여주자"가 되면 ③만
-- 다시 만들면 된다 — ①②는 그대로다.
--
-- **칸의 식별자는 대리키다.** 행정구역 코드를 재활용하면 같은 문자열이 문맥에 따라 두 뜻이
-- 되고(41111 = 수원시 전체? 장안구?), 접두를 붙여 피하려 들면 문자열에 의미를 넣게 된다.
-- 칸은 우리가 만든 표시 단위라 대응하는 공식 코드가 애초에 없다 — 묶는 규칙이 바뀌면 새로
-- 만드는 값이므로 의미를 담을 이유가 없다.

-- ---------------------------------------------------------------------------
-- 1. tour.area — KTO 원본의 상위 계층 17행.
--
-- ja·zh는 nullable이다 — 원본이 그렇고(V44), 빈 칸을 EN으로 채우면 실제 번역이 들어왔는지
-- 구분할 수 없다. 폴백은 조회 시점에 활성 언어 → EN → KO로 한다(POL-039).
-- ---------------------------------------------------------------------------
CREATE TABLE tour.area (
    area_cd varchar(2)   PRIMARY KEY,
    name_ko varchar(128) NOT NULL,
    name_en varchar(128) NOT NULL,
    name_ja varchar(128),
    name_zh varchar(128)
);

-- district가 이미 갖고 있던 값에서 뽑는다. 17행이 나온다.
INSERT INTO tour.area (area_cd, name_ko, name_en, name_ja, name_zh)
SELECT DISTINCT province_code, province_name_ko, province_name_en, province_name_ja, province_name_zh
FROM tour.district;

-- ---------------------------------------------------------------------------
-- 2. tour.district — 시도 이름 중복을 걷어내고 area를 참조하게 한다.
--
-- `province_code`는 `area_cd`로 이름을 바꾼다 — 원본 컬럼명(areaCd)에 맞춘다.
-- ---------------------------------------------------------------------------
ALTER TABLE tour.district RENAME COLUMN province_code TO area_cd;

ALTER TABLE tour.district
    ADD CONSTRAINT fk_district_area FOREIGN KEY (area_cd) REFERENCES tour.area (area_cd);

-- 중복 저장이던 4컬럼을 지운다. 조회는 area를 조인해 읽는다.
ALTER TABLE tour.district
    DROP COLUMN province_name_ko,
    DROP COLUMN province_name_en,
    DROP COLUMN province_name_ja,
    DROP COLUMN province_name_zh;

CREATE INDEX idx_district_area_cd ON tour.district (area_cd);

-- ---------------------------------------------------------------------------
-- 3. tour.map_cell — 우리가 만든 표시 단위 161행.
--
-- 이름을 적재해 두는 이유는 시군구 이름에서 유도할 수 없기 때문이다. 광역시 칸은 시도
-- 이름을 쓰면 되지만 일반시 칸은 원본에 '수원시 장안구'밖에 없고 칸 이름은 '수원시'여야
-- 한다. KO는 공백 앞, EN은 쉼표 뒤('Jangan-gu, Suwon-si')를 자르면 되지만 **JA/ZH는
-- 구분자가 없다** — '水原市長安区'는 한 덩어리다.
-- ---------------------------------------------------------------------------
CREATE TABLE tour.map_cell (
    id      bigint       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    name_ko varchar(128) NOT NULL,
    name_en varchar(128) NOT NULL,
    name_ja varchar(128),
    name_zh varchar(128),

    -- 상위 시도. 시도 단위로 묶어 진척을 계산할 때 쓴다(R21).
    area_cd varchar(2)   NOT NULL,

    CONSTRAINT fk_map_cell_area FOREIGN KEY (area_cd) REFERENCES tour.area (area_cd)
);

/**
 * 칸이 묶는 시군구. 한 칸이 1~25개를 묶는다(광명시 1개, 서울 25개).
 *
 * **district는 칸을 모른다** — 참조가 이 표에서 한 방향으로만 나간다. 칸 규칙이 바뀌어도
 * 원본 두 표는 그대로다.
 */
CREATE TABLE tour.map_cell_district (
    cell_id       bigint     NOT NULL,
    district_code varchar(5) NOT NULL,

    PRIMARY KEY (cell_id, district_code),

    CONSTRAINT fk_map_cell_district_cell
        FOREIGN KEY (cell_id) REFERENCES tour.map_cell (id) ON DELETE CASCADE,
    CONSTRAINT fk_map_cell_district_district
        FOREIGN KEY (district_code) REFERENCES tour.district (code) ON DELETE CASCADE
);

-- 시군구 → 칸 역방향 조회(좌표 판정 결과를 칸에 대응). PK 선행이 cell_id라 따로 만든다.
CREATE INDEX idx_map_cell_district_district ON tour.map_cell_district (district_code);

-- ---------------------------------------------------------------------------
-- 4. 칸 적재와 매핑. **칸과 매핑을 한 문장에서 함께 만든다** — 대리키라 INSERT 전에는
-- id를 알 수 없으므로, `RETURNING`으로 받은 id를 그대로 매핑에 쓴다. 칸을 먼저 넣고 나중에
-- 이름으로 다시 찾아 매핑하면 동명이인(예: 고성군)에서 어긋난다.
--
-- 세 규칙이 겹치지 않고 252개 시군구를 모두 덮는다.
-- ---------------------------------------------------------------------------

-- 4-1. 광역시·특별시·특별자치시 → 시도 전체가 1칸. 이름은 시도 이름을 쓴다.
WITH inserted AS (
    INSERT INTO tour.map_cell (name_ko, name_en, name_ja, name_zh, area_cd)
    SELECT a.name_ko, a.name_en, a.name_ja, a.name_zh, a.area_cd
    FROM tour.area a
    WHERE a.name_ko ~ '(광역시|특별시|특별자치시)$'
    RETURNING id, area_cd
)
INSERT INTO tour.map_cell_district (cell_id, district_code)
SELECT i.id, d.code
FROM inserted i
JOIN tour.district d ON d.area_cd = i.area_cd;

-- 4-2. 구를 가진 일반시 12곳 → 시 단위 1칸.
--
-- 묶음 키는 **시 이름 + 시도**다. '고성군'처럼 다른 시도에 같은 이름이 있을 수 있어 시도를
-- 함께 봐야 한다(일반시에는 현재 그런 사례가 없지만 규칙이 그래야 한다).
--
-- 이름은 값으로 적는다(위 헤더 참조). ZH는 번체다 — 우리가 서비스하는 ZH 독자는 대만·홍콩이다.
WITH city AS (
    SELECT split_part(d.name_ko, ' ', 1) AS city_name, d.area_cd
    FROM tour.district d
    JOIN tour.area a ON a.area_cd = d.area_cd
    WHERE a.name_ko !~ '(광역시|특별시|특별자치시)$'
      AND d.name_ko LIKE '% %구'
    GROUP BY split_part(d.name_ko, ' ', 1), d.area_cd
),
named AS (
    SELECT c.city_name, c.area_cd, n.name_en, n.name_ja, n.name_zh
    FROM city c
    JOIN (
        VALUES
            ('수원시', 'Suwon-si',    '水原市', '水原市'),
            ('성남시', 'Seongnam-si', '城南市', '城南市'),
            ('안양시', 'Anyang-si',   '安養市', '安養市'),
            ('부천시', 'Bucheon-si',  '富川市', '富川市'),
            ('안산시', 'Ansan-si',    '安山市', '安山市'),
            ('고양시', 'Goyang-si',   '高陽市', '高陽市'),
            ('용인시', 'Yongin-si',   '龍仁市', '龍仁市'),
            ('청주시', 'Cheongju-si', '清州市', '清州市'),
            ('천안시', 'Cheonan-si',  '天安市', '天安市'),
            ('포항시', 'Pohang-si',   '浦項市', '浦項市'),
            ('창원시', 'Changwon-si', '昌原市', '昌原市'),
            ('전주시', 'Jeonju-si',   '全州市', '全州市')
    ) AS n(name_ko, name_en, name_ja, name_zh) ON n.name_ko = c.city_name
),
inserted AS (
    INSERT INTO tour.map_cell (name_ko, name_en, name_ja, name_zh, area_cd)
    SELECT city_name, name_en, name_ja, name_zh, area_cd FROM named
    RETURNING id, name_ko, area_cd
)
INSERT INTO tour.map_cell_district (cell_id, district_code)
SELECT i.id, d.code
FROM inserted i
JOIN tour.district d
  ON d.area_cd = i.area_cd
 AND split_part(d.name_ko, ' ', 1) = i.name_ko
 AND d.name_ko LIKE '% %구';

-- 4-3. 나머지 시군 → 시군구 1개가 1칸. 이름도 자기 이름 그대로다.
--
-- 여기서는 칸과 시군구가 1:1이라 `name_ko`로 되찾아도 어긋나지 않는다 — 같은 시도 안에
-- 같은 이름의 시군구는 없다.
WITH inserted AS (
    INSERT INTO tour.map_cell (name_ko, name_en, name_ja, name_zh, area_cd)
    SELECT d.name_ko, d.name_en, d.name_ja, d.name_zh, d.area_cd
    FROM tour.district d
    JOIN tour.area a ON a.area_cd = d.area_cd
    WHERE a.name_ko !~ '(광역시|특별시|특별자치시)$'
      AND d.name_ko NOT LIKE '% %구'
    RETURNING id, name_ko, area_cd
)
INSERT INTO tour.map_cell_district (cell_id, district_code)
SELECT i.id, d.code
FROM inserted i
JOIN tour.district d ON d.area_cd = i.area_cd AND d.name_ko = i.name_ko;

-- ---------------------------------------------------------------------------
-- 6. 검증. **어긋나면 기동을 막는다** — 칸 수와 덮개는 계약(R7)이고, 경계 데이터가 바뀌면
-- 소리 없이 틀리는 대신 여기서 실패해야 한다.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    cell_count    integer;
    mapped_count  integer;
    orphan_count  integer;
BEGIN
    SELECT count(*) INTO cell_count FROM tour.map_cell;
    IF cell_count <> 161 THEN
        RAISE EXCEPTION '표시 칸이 161개가 아닙니다: %개', cell_count;
    END IF;

    -- 모든 시군구가 정확히 한 칸에 속해야 한다. 겹치면 PK가 막고, 빠지면 여기서 걸린다.
    SELECT count(*) INTO mapped_count FROM tour.map_cell_district;
    IF mapped_count <> 252 THEN
        RAISE EXCEPTION '칸에 매핑된 시군구가 252개가 아닙니다: %개', mapped_count;
    END IF;

    -- 시군구가 하나도 없는 칸은 지도에서 빈 칸이 된다.
    SELECT count(*) INTO orphan_count
    FROM tour.map_cell mc
    WHERE NOT EXISTS (SELECT 1 FROM tour.map_cell_district x WHERE x.cell_id = mc.id);
    IF orphan_count > 0 THEN
        RAISE EXCEPTION '시군구가 없는 칸이 %개 있습니다', orphan_count;
    END IF;
END $$;
