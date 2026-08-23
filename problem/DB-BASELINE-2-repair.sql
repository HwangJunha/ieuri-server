-- ═══════════════════════════════════════════════════════════════════════════
-- [2/3] Flyway 이력 치환 — 개발 클러스터의 두 DB에서 각각 실행
-- ═══════════════════════════════════════════════════════════════════════════
--
-- 마이그레이션 92개를 V1 두 개로 접었다. **개발 DB는 이미 그 92개를 적용한 상태**라,
-- 파일이 사라진 채로 앱을 띄우면 Flyway validate가 두 가지로 막는다:
--
--   1. V2~V80이 이력에 있는데 파일이 없다   → 해결책 없음(무시 설정 대상이 아니다)
--   2. V1의 체크섬이 예전 create_member의 것과 다르다
--
-- 그래서 이력 자체를 갈아 끼운다. **스키마는 손대지 않는다** — 이미 V1이 만들려는
-- 최종 형태 그대로이며(pg_dump로 뜬 것이 그 V1이다), 이력만 "V1을 적용했다"로 고쳐
-- 쓰면 실제 상태와 기록이 일치한다.
--
-- ── 왜 checksum을 NULL로 두는가 ────────────────────────────────────────────
-- Flyway는 **checksum이 NULL인 행의 검증을 건너뛴다**(BASELINE 행과 같은 취급).
-- 값을 계산해 박아 넣으면 한 글자만 틀려도 기동이 막히는데, 그 값은 Flyway 버전과
-- 줄바꿈 처리에 달려 있어 밖에서 정확히 재현하기 어렵다. NULL이면 그 위험이 없고,
-- 다음 마이그레이션(V2)부터는 정상적으로 체크섬이 기록된다.
--
-- **운영 DB에서는 이 파일을 실행하지 않는다.** 빈 DB라 Flyway가 V1을 정상 적용한다.

-- ───────────────────────────────────────────────────────────────────────────
-- [2-A] 메인 DB (ieuri-db/ieuri-pg)
--
--   kubectl -n ieuri-db exec -i ieuri-pg-2 -c postgres -- \
--     psql -U postgres -d ieuri -v ON_ERROR_STOP=1 <<'EOF'
--   ... 아래 블록 ...
--   EOF
-- ───────────────────────────────────────────────────────────────────────────

BEGIN;

-- 지금 무엇이 지워지는지 남긴다(실행 로그로만 남으며 되돌릴 수는 없다).
-- **작성 시점 실측은 91행이다**(2026-08-23). 크게 다르면 다른 DB를 보고 있는 것이니
-- 멈추고 확인한다 — 특히 0이면 이력이 없는 DB이므로 이 파일을 돌릴 이유가 없다.
SELECT count(*) AS "지워질 이력 행 수" FROM public.flyway_schema_history;

DELETE FROM public.flyway_schema_history;

INSERT INTO public.flyway_schema_history
    (installed_rank, version, description, type, script,
     checksum, installed_by, installed_on, execution_time, success)
VALUES
    (1, '1', 'baseline', 'SQL', 'V1__baseline.sql',
     NULL, current_user, now(), 0, true);

SELECT * FROM public.flyway_schema_history;

COMMIT;

-- ───────────────────────────────────────────────────────────────────────────
-- [2-B] 검색 노드 DB (ieuri-db/ieuri-search)
--
-- **별도 Flyway 인스턴스이며 이력 테이블도 따로다**(SearchDataSourceConfig.searchFlyway).
-- 위와 같은 SQL이지만 접속 대상이 다르므로 반드시 따로 실행한다.
--
--   kubectl -n ieuri-db exec -i ieuri-search-1 -c postgres -- \
--     psql -U postgres -d ieuri -v ON_ERROR_STOP=1 <<'EOF'
--   BEGIN;
--   -- 작성 시점 실측은 11행이다(2026-08-23).
--   SELECT count(*) AS "지워질 이력 행 수" FROM public.flyway_schema_history;
--   DELETE FROM public.flyway_schema_history;
--   INSERT INTO public.flyway_schema_history
--       (installed_rank, version, description, type, script,
--        checksum, installed_by, installed_on, execution_time, success)
--   VALUES (1, '1', 'baseline', 'SQL', 'V1__baseline.sql',
--           NULL, current_user, now(), 0, true);
--   SELECT * FROM public.flyway_schema_history;
--   COMMIT;
--   EOF
-- ───────────────────────────────────────────────────────────────────────────
