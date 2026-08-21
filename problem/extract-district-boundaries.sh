#!/usr/bin/env bash
# 시군구 경계를 GeoJSON FeatureCollection으로 추출한다(SPEC-0030).
#
# **클라이언트가 좌표→시군구 판정을 로컬에서 하기 위한 에셋이다.** 서버 API로 내려주지
# 않는 이유는 이 데이터가 정적이기 때문이다 — 정부가 배포하고, 사용자마다 다르지 않으며,
# 몇 년에 한 번 바뀐다. 앱 번들에 넣으면 첫 실행 다운로드도 캐시 관리도 필요 없다.
#
# **단순화하지 않는다.** 원본 그대로여야 서버가 하던 판정과 결과가 같다. 약 3.8MB이며
# 앱 에셋으로 부담되는 크기가 아니다.
#
# 실행:
#   IEURI_DB_PASSWORD=... ./problem/extract-district-boundaries.sh
#
# 출력: problem/out/district-boundaries.geojson
set -euo pipefail

# direnv가 로드하는 것과 같은 파일을 읽는다 — 셸 밖에서 실행해도 동작하게.
SECRETS="${IEURI_SECRETS_FILE:-$HOME/.config/ieuri/secrets.env}"
if [ -f "$SECRETS" ]; then
    set -a
    # shellcheck disable=SC1090
    . "$SECRETS"
    set +a
fi

DB_URL="${IEURI_DB_URL:-jdbc:postgresql://localhost:5432/ieuri}"
DB_USER="${IEURI_DB_USERNAME:-ieuri}"
DB_PASS="${IEURI_DB_PASSWORD:?IEURI_DB_PASSWORD가 없습니다 — $SECRETS 를 확인하십시오}"

OUT_DIR="$(cd "$(dirname "$0")" && pwd)/out"
OUT_FILE="$OUT_DIR/district-boundaries.geojson"
mkdir -p "$OUT_DIR"

DRIVER=$(find ~/.gradle/caches -name "postgresql-42*.jar" ! -name "*sources*" 2>/dev/null | head -1)
[ -n "$DRIVER" ] || { echo "postgresql 드라이버를 찾지 못했습니다. ./gradlew build 후 다시 실행하십시오."; exit 1; }

# 한 행에 FeatureCollection 전체를 담아 받는다 — 252개라 메모리에 올려도 문제없다.
#
# properties에 담는 것은 판정에 필요한 최소값이다: 시군구 코드(방문 기록의 저장 단위),
# 이름 4개 언어(확인 화면 표시), 시도 코드(진척 계산).
SQL=$(cat <<'EOSQL'
SELECT json_build_object(
  'type', 'FeatureCollection',
  'generatedFrom', 'ieuri tour.district (KTO 관광지_시군구_코드정보 v1.0 · vuski/admdongkor ver20240401)',
  'features', json_agg(
    json_build_object(
      'type', 'Feature',
      'properties', json_build_object(
        'code', d.code,
        'areaCd', d.area_cd,
        'nameKo', d.name_ko,
        'nameEn', d.name_en,
        'nameJa', d.name_ja,
        'nameZh', d.name_zh
      ),
      'geometry', ST_AsGeoJSON(d.boundary::geometry)::json
    ) ORDER BY d.code
  )
)::text
FROM tour.district d
EOSQL
)

TMP_JAVA=$(mktemp -d)/Extract.java
cat > "$TMP_JAVA" <<'EOJAVA'
import java.sql.*;
import java.nio.file.*;

public class Extract {
    public static void main(String[] a) throws Exception {
        try (Connection c = DriverManager.getConnection(a[0], a[1], a[2]);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(a[3])) {
            if (!rs.next()) throw new IllegalStateException("경계 데이터가 비어 있습니다");
            Files.writeString(Path.of(a[4]), rs.getString(1));
        }
    }
}
EOJAVA

java -cp "$DRIVER" "$TMP_JAVA" "$DB_URL" "$DB_USER" "$DB_PASS" "$SQL" "$OUT_FILE"

SIZE=$(du -h "$OUT_FILE" | cut -f1)
COUNT=$(python3 -c "import json;print(len(json.load(open('$OUT_FILE'))['features']))")
echo "추출 완료: $OUT_FILE ($SIZE, ${COUNT}개 시군구)"
[ "$COUNT" = "252" ] || echo "경고: 252개가 아닙니다 — 경계 데이터를 확인하십시오"
