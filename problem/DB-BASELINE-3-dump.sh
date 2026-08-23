#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# [3/3] 운영 이관용 데이터 덤프 — 정리(1단계)를 마친 개발 DB에서 뜬다
# ═══════════════════════════════════════════════════════════════════════════
#
# **1단계(DB-BASELINE-1-cleanup.sql)를 먼저 실행하고 COMMIT까지 마친다.** 이 스크립트는
# 남은 데이터를 그대로 담으므로, 정리 전에 뜨면 지우기로 한 것이 운영으로 따라간다.
# 1단계는 파일에 COMMIT을 담지 않으니(확인 표를 보고 사람이 친다), 커밋했는지 확인한다 —
# 롤백된 채로 여기 오면 아무것도 지워지지 않은 덤프가 나온다.
#
# 운영에서의 순서:
#   1) CNPG가 클러스터·DB·확장(postgis)을 만든다        ← 매니페스트
#   2) 앱이 뜨면서 Flyway가 V1__baseline.sql을 적용한다   ← 빈 스키마 + 기준 데이터
#   3) 이 덤프를 넣는다                                  ← 아래 복원 절차
#
# **2)와 3)의 순서가 뒤바뀌면 안 된다.** 덤프는 데이터만 담아 테이블을 만들지 않는다.
#
# 사용:
#   ./DB-BASELINE-3-dump.sh [출력디렉터리]     (기본: ./dump-<날짜>)

set -euo pipefail

NS=ieuri-db
MAIN_POD=${MAIN_POD:-ieuri-pg-2}          # primary. `kubectl -n ieuri-db get cluster`로 확인
OUT=${1:-"./dump-$(date +%Y%m%d)"}

mkdir -p "$OUT"

# ── 덤프 대상 스키마 ────────────────────────────────────────────────────────
# `batch`는 뺀다 — Spring Batch 실행 이력이며 1단계에서 비웠다. 운영은 자기 이력을
# 처음부터 쌓는다. `public`도 뺀다 — flyway_schema_history가 거기 있고, 그것은 운영
# Flyway가 스스로 쓴다(덤프로 넣으면 V1을 적용하지 않은 DB에 "적용했다"는 기록만 생긴다).
SCHEMAS=(auth community tour category alarm)

SCHEMA_ARGS=()
for s in "${SCHEMAS[@]}"; do SCHEMA_ARGS+=(--schema="$s"); done

echo "▶ 덤프 대상: ${SCHEMAS[*]}"
echo "▶ 출력: $OUT"

# ── 본 덤프 ─────────────────────────────────────────────────────────────────
#
# `--disable-triggers`를 쓴다. **이것 없이 넣으면 좌표가 조용히 다시 계산된다** —
# spot·spot_catalog·spot_catalog_translation·community_post_spot에 BEFORE INSERT
# 트리거가 걸려 있어 `location`을 map_x/map_y로 덮어쓴다. 값이 같아 보이지만 덤프의
# location을 신뢰하지 않게 되고, 무엇보다 8만 행마다 트리거가 도는 만큼 느리다.
# FK 검사도 함께 미뤄져 테이블 순서에 걸리지 않는다.
#
# **이 옵션은 슈퍼유저로 복원해야 동작한다**(ALTER TABLE ... DISABLE TRIGGER 권한).
# 앱 롤(ieuri)로 복원하려면 이 옵션을 빼고, 대신 아래 "앱 롤로 복원" 절을 따른다.
#
# `--column-inserts`를 쓰지 않는다 — 8만 행에 컬럼 이름을 매번 붙이면 파일이 몇 배가
# 되고 복원도 느리다. 기본 COPY 형식이 빠르다. 대신 컬럼 순서가 바뀌면 깨지므로,
# **덤프와 같은 V1으로 만든 스키마에만 넣는다**(위 순서 2번이 그 보장이다).
echo "▶ 메인 DB 데이터 덤프 중..."
kubectl -n "$NS" exec "$MAIN_POD" -c postgres -- \
    pg_dump -U postgres -d ieuri \
        --data-only \
        --disable-triggers \
        --no-owner --no-privileges \
        "${SCHEMA_ARGS[@]}" \
    > "$OUT/main-data.sql"

echo "  → $OUT/main-data.sql ($(du -h "$OUT/main-data.sql" | cut -f1))"

# ── 검색 노드는 뜨지 않는다 ─────────────────────────────────────────────────
#
# **논리 복제가 채우기 때문이다.** 구독이 붙는 순간 메인의 spot 계열 6개 테이블이
# 초기 동기화로 통째로 복사된다 — 덤프를 넣으면 같은 행을 두 번 넣어 PK 충돌이 난다.
#
# 운영 검색 노드는 스키마(V1__baseline.sql)만 Flyway가 만들고, 데이터는 구독을
# 만드는 순간부터 알아서 들어온다.
echo "▶ 검색 노드는 덤프하지 않는다 (논리 복제가 채운다)"

# ── 검증 ────────────────────────────────────────────────────────────────────
echo "▶ 행 수 기록 중..."
kubectl -n "$NS" exec "$MAIN_POD" -c postgres -- psql -U postgres -d ieuri -t -A -F'|' -c "
SELECT 'auth.member' AS t, count(*) FROM auth.member
UNION ALL SELECT 'auth.member_profile', count(*) FROM auth.member_profile
UNION ALL SELECT 'auth.member_role', count(*) FROM auth.member_role
UNION ALL SELECT 'auth.member_terms_agreement', count(*) FROM auth.member_terms_agreement
UNION ALL SELECT 'auth.role', count(*) FROM auth.role
UNION ALL SELECT 'auth.permission', count(*) FROM auth.permission
UNION ALL SELECT 'auth.terms', count(*) FROM auth.terms
UNION ALL SELECT 'tour.spot', count(*) FROM tour.spot
UNION ALL SELECT 'tour.spot_catalog', count(*) FROM tour.spot_catalog
UNION ALL SELECT 'tour.spot_catalog_translation', count(*) FROM tour.spot_catalog_translation
UNION ALL SELECT 'tour.district', count(*) FROM tour.district
UNION ALL SELECT 'tour.map_cell', count(*) FROM tour.map_cell
UNION ALL SELECT 'tour.map_cell_image', count(*) FROM tour.map_cell_image
UNION ALL SELECT 'category.category', count(*) FROM category.category
UNION ALL SELECT 'community.community_spot_catalog_language', count(*) FROM community.community_spot_catalog_language
ORDER BY 1" > "$OUT/rowcounts-source.txt"

echo "  → $OUT/rowcounts-source.txt"
cat "$OUT/rowcounts-source.txt"

cat > "$OUT/RESTORE.md" <<'MD'
# 운영 복원 절차

## 전제

1. CNPG가 클러스터·DB·`postgis` 확장을 만들어 두었다 (매니페스트).
2. 앱이 한 번 떠서 **Flyway V1이 적용됐다** — 스키마와 기준 데이터(역할·권한·약관)가 있다.

2번을 건너뛰면 `relation does not exist`로 전부 실패한다.

## 복원 (슈퍼유저)

`--disable-triggers`로 뜬 덤프라 슈퍼유저가 필요하다.

    kubectl -n ieuri-db exec -i <운영-primary-pod> -c postgres -- \
        psql -U postgres -d ieuri -v ON_ERROR_STOP=1 < main-data.sql

**기준 데이터가 충돌한다.** V1이 이미 넣은 role·permission·role_permission·terms를
덤프도 담고 있어 PK/UNIQUE 위반이 난다. 둘 중 하나를 고른다:

- 덤프에서 그 4개 테이블을 빼고 넣는다 (권장 — V1이 넣은 것과 같은 값이다):

      grep -v "^COPY auth\.\(role\|permission\|role_permission\|terms\) " main-data.sql > ...

  COPY 블록은 여러 줄이라 grep 한 줄로는 못 지운다. 아래 스크립트를 쓴다:

      python3 - <<'EOF'
      import re, sys
      SKIP = {'auth.role','auth.permission','auth.role_permission','auth.terms'}
      out, skipping = [], False
      for line in open('main-data.sql', encoding='utf-8'):
          m = re.match(r'COPY ([\w.]+) ', line)
          if m:
              skipping = m.group(1) in SKIP
          if skipping:
              if line.strip() == r'\.':
                  skipping = False
              continue
          out.append(line)
      open('main-data-nofixed.sql','w',encoding='utf-8').writelines(out)
      EOF

- 또는 넣기 전에 운영에서 그 4개 테이블을 비운다. **role/permission을 지우면
  member_role·role_permission이 CASCADE로 함께 지워진다** — 회원 데이터를 넣기
  전이어야 한다.

## 확인

    psql -U postgres -d ieuri -f <아래 질의>

`rowcounts-source.txt`와 대조한다. 한 줄이라도 다르면 멈추고 원인을 찾는다.

## 시퀀스

`--data-only` 덤프는 시퀀스 현재값을 함께 담는다(`setval`). 별도 조치가 필요 없지만,
복원 후 한 번 확인해 둔다 — 값이 낮으면 다음 INSERT가 PK 충돌로 실패한다:

    SELECT schemaname, sequencename, last_value
    FROM pg_sequences WHERE schemaname IN ('auth','community','tour','category','alarm')
    ORDER BY 1,2;
MD

echo "▶ 복원 안내: $OUT/RESTORE.md"
echo "완료."
