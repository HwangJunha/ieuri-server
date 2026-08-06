-- 글의 좌표. **반경 조회에서 spot 목록을 경유하지 않기 위한 값이다.**
--
-- 이전에는 좌표 → tourism에 spot id 목록을 묻고(상한 2000) → 그 목록을 `IN`에 넣어 글을
-- 찾았다. 실측하면 기본 반경 20km 안에 spot이 12,000개가 넘어 **83%가 조용히 잘렸다** —
-- 반경 안의 최신 글인데 2001번째 spot에 붙었다는 이유로 영원히 보이지 않았고,
-- hasNextPage는 false로 와서 클라이언트는 "다 봤다"고 판단했다.
--
-- 좌표를 여기 복사해 두면 글 조회 한 번으로 끝난다. 상한이 사라지고, spot 순위를 SQL의
-- CASE WHEN 2000줄로 옮기던 코드도 함께 사라진다.
--
-- **중복 저장은 의도한 것이다.** 값(좌표)을 복사할 뿐 id로 참조하는 관계는 그대로이며,
-- 오히려 도메인 분리에 유리하다 — tourism에 묻지 않고도 커뮤니티가 반경 조회를 한다.
-- 관광지 좌표는 사실상 바뀌지 않고, 바뀌더라도 **글이 쓰인 시점의 위치**가 남는 편이
-- 자연스럽다(장소 정보가 나중에 수정돼도 글은 그 자리에 있었다).
--
-- 장소를 고르지 않은 글은 이 테이블에 행 자체가 없다 — 좌표도 당연히 없고, 지역 피드의
-- 거리순에서 빠진다(최신순에는 나온다).
--
-- **spot 테이블(V15)과 같은 방식이다**: 애플리케이션은 map_x/map_y만 다루고 geography
-- 컬럼은 트리거가 파생시킨다. 파생을 애플리케이션이 관리하면 둘이 어긋날 수 있고,
-- JPA 엔티티가 PostGIS 타입을 알 필요도 없어진다.
ALTER TABLE community_post_spot
    ADD COLUMN map_x DOUBLE PRECISION,
    ADD COLUMN map_y DOUBLE PRECISION,
    ADD COLUMN location geography(Point, 4326);

-- 기존 행 백필. spot에서 좌표를 가져온다.
UPDATE community_post_spot ps
SET map_x = s.map_x, map_y = s.map_y, location = s.location
FROM spot s
WHERE s.id = ps.spot_id;

-- 백필 뒤 NOT NULL로 조인다. 좌표 없는 연결 행이 생기면 그 글은 거리순에서 조용히
-- 사라지므로, 그 상태를 애초에 만들지 못하게 한다.
--
-- **spot이 사라져 백필되지 못한 행이 있으면 이 문장이 실패한다.** 그것이 의도다 —
-- 조용히 넘기면 어느 글이 거리순에서 빠지는지 아무도 모른 채로 배포된다.
ALTER TABLE community_post_spot
    ALTER COLUMN map_x SET NOT NULL,
    ALTER COLUMN map_y SET NOT NULL,
    ALTER COLUMN location SET NOT NULL;

-- 좌표 유효 범위. spot 테이블과 같은 불변식을 여기서도 지킨다.
ALTER TABLE community_post_spot
    ADD CONSTRAINT ck_community_post_spot_map_x CHECK (map_x BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_community_post_spot_map_y CHECK (map_y BETWEEN -90 AND 90);

-- 적재 시 location을 함께 쓰지 않아도 자동으로 채워지게 한다(spot의 trg_spot_sync_location과 동일).
CREATE OR REPLACE FUNCTION community_post_spot_sync_location() RETURNS trigger AS $$
BEGIN
    NEW.location := ST_MakePoint(NEW.map_x, NEW.map_y)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_community_post_spot_sync_location
    BEFORE INSERT OR UPDATE OF map_x, map_y ON community_post_spot
    FOR EACH ROW EXECUTE FUNCTION community_post_spot_sync_location();

-- 반경 조회·거리 정렬의 인덱스. ST_DWithin과 `<->` 둘 다 이 GiST 인덱스를 탄다.
CREATE INDEX idx_community_post_spot_location
    ON community_post_spot USING GIST (location);
