-- 관광지 반경 필터·거리 정렬을 위한 공간 컬럼과 GiST 인덱스(SPEC-0004 R-9·R-10).
--
-- **CREATE EXTENSION postgis는 여기 없다.** 확장 활성화는 CNPG가 소유한다
-- (ieuri-manifest: infra/cnpg/cluster/templates/database.yaml). 앱 롤(ieuri)에 확장 설치
-- 권한을 주지 않으려는 선택이며, 오퍼레이터가 슈퍼유저로 CREATE EXTENSION을 수행한다.
-- 소유권 경계: 확장 활성화(DB의 능력) = CNPG / 테이블·컬럼·인덱스(스키마) = Flyway.
--
-- 확장이 없으면 이 마이그레이션이 부팅 시 실패하는데, 그것이 의도된 동작이다 —
-- 공간 조회가 불가능한 상태로 앱이 뜨는 것보다 낫다(fail-fast).

-- 경도(map_x)·위도(map_y)에서 파생되는 **조회 전용** 컬럼.
-- geometry가 아니라 geography를 쓴다: geography는 미터 단위로 구면 거리를 다뤄
-- ST_DWithin(반경 미터)·ST_Distance(거리 미터)를 SRID 변환 없이 그대로 쓸 수 있다.
-- geometry(Point,4326)이면 도(degree) 단위라 미터 반경을 표현하려면 변환이 필요하다.
ALTER TABLE spot ADD COLUMN location geography(Point, 4326);

-- 기존 행 백필. ST_MakePoint는 (경도, 위도) 순서다 — (위도, 경도)가 아니다.
-- 이 순서를 뒤집으면 한국 좌표가 지구 반대편으로 가는데, 위경도 범위가 겹쳐서
-- 에러 없이 조용히 틀린다. V13의 CHECK 제약이 이 실수를 잡아주지 못하는 지점이다.
UPDATE spot SET location = ST_MakePoint(map_x, map_y)::geography;

-- 백필 후 NOT NULL. 좌표 없는 관광지는 반경 조회에서 의미가 없고, map_x/map_y가
-- 이미 NOT NULL이라 이 컬럼도 항상 채워질 수 있다.
ALTER TABLE spot ALTER COLUMN location SET NOT NULL;

-- 적재 시 location을 함께 쓰지 않아도 자동으로 채워지게 한다.
-- 배치 upsert(SpotJpaAdapter)는 map_x/map_y만 다루고 location을 모른다 — 파생 컬럼을
-- 애플리케이션이 관리하면 둘이 어긋날 수 있으므로 DB가 보장한다.
CREATE OR REPLACE FUNCTION spot_sync_location() RETURNS trigger AS $$
BEGIN
    NEW.location := ST_MakePoint(NEW.map_x, NEW.map_y)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_spot_sync_location
    BEFORE INSERT OR UPDATE OF map_x, map_y ON spot
    FOR EACH ROW EXECUTE FUNCTION spot_sync_location();

-- 반경 필터가 전체 스캔 없이 동작하기 위한 공간 인덱스(AC-24).
-- ST_DWithin이 이 인덱스를 타고 후보를 압축한 뒤, ST_Distance가 후보에만 정밀 거리를
-- 계산한다. 필터에 ST_Distance를 쓰면 전 행 계산이 되어 인덱스를 못 탄다 —
-- **필터는 ST_DWithin, 정렬만 ST_Distance**가 AC-24의 실질적 내용이다.
CREATE INDEX idx_spot_location ON spot USING GIST (location);
