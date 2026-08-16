-- 컨텍스트별 스키마 5분할(SPEC-0023 R1·R2·R6). 테이블 46개 + 배치 메타 6개를
-- `public` 하나에서 소유 스키마로 옮긴다.
--
-- **`ALTER TABLE ... SET SCHEMA`는 카탈로그 갱신이다** — pg_class.relnamespace만 바뀌고
-- 데이터 재작성이 없다. 인덱스·제약·트리거·FK는 이름이 아니라 OID를 참조하므로 테이블을
-- 그대로 따라온다. 재생성할 것이 없다는 뜻이며, 이 파일이 얇은 이유다.
--
-- **한 파일에 모으는 것이 의도다.** 중간에 끊기면 일부만 옮겨진 상태가 남고, 그 상태는
-- `ddl-auto: validate`가 어느 쪽으로도 통과하지 못한다. Flyway가 마이그레이션을 트랜잭션으로
-- 감싸고 PostgreSQL이 DDL 트랜잭션을 지원하므로, 전부 옮겨지거나 전부 제자리에 남는다.
--
-- **`CREATE EXTENSION`이 여기 없다**(POL-034). 확장 활성화는 CNPG가 소유한다 — V15 헤더의
-- 소유권 경계와 같다. PostGIS `geography`는 `tour`·`community` 양쪽에서 해석되어야 하는데,
-- 그 해결 수단은 매니페스트의 search_path이지 이 파일이 아니다.
--
-- **FK를 새로 만들지 않는다**(R4). 컨텍스트 간 참조는 계속 FK 없는 컬럼으로 남는다 —
-- 스키마를 나누는 작업에서 컨텍스트 간 FK를 만드는 것은 정면으로 모순이다.

-- ── 1. 스키마 생성 ──────────────────────────────────────────────────
--
-- `public`은 지우지 않는다. PostGIS가 거기 설치돼 있어 제거 대상이 아니다.
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS community;
CREATE SCHEMA IF NOT EXISTS tour;
CREATE SCHEMA IF NOT EXISTS category;
CREATE SCHEMA IF NOT EXISTS batch;

-- ── 2~6. 테이블 이동 ───────────────────────────────────────────────
--
-- **재적용에 견뎌야 한다.** 이 슬라이스의 마이그레이션은 아직 병합 전이라 수정이 허용되고,
-- 수정하면 로컬에서 checksum repair 후 다시 돌아간다 — 그때 테이블은 이미 옮겨져 있다.
-- 가드가 없으면 첫 문장이 `relation "member" does not exist`로 죽는다.
-- (검색 노드의 V8이 같은 이유로 같은 가드를 쓴다.)
--
-- 배치표를 (스키마, 테이블) 쌍의 배열로 두고 순회한다. 46개를 나열하는 것과 내용은 같고,
-- 이동 여부를 문장마다 확인한다는 점만 다르다.
DO $$
DECLARE
    target   TEXT[];
    mapping  TEXT[][] := ARRAY[
        -- auth — 11
        ['auth', 'member'], ['auth', 'member_profile'], ['auth', 'member_terms_agreement'],
        ['auth', 'terms'], ['auth', 'email_verification'], ['auth', 'password_reset'],
        ['auth', 'member_audit_log'], ['auth', 'role'], ['auth', 'permission'],
        ['auth', 'role_permission'], ['auth', 'member_role'],

        -- community — 15
        ['community', 'community_post'], ['community', 'community_post_photo'],
        ['community', 'community_post_like'], ['community', 'community_post_hide'],
        ['community', 'community_post_tag'], ['community', 'community_post_spot'],
        ['community', 'community_post_chip'], ['community', 'community_comment'],
        ['community', 'community_block'], ['community', 'community_report'],
        ['community', 'community_conversation'], ['community', 'community_message'],
        ['community', 'community_message_report'], ['community', 'community_message_report_item'],
        ['community', 'community_orphan_photo_object'],

        -- tour — 16
        ['tour', 'spot'], ['tour', 'spot_catalog'], ['tour', 'spot_catalog_translation'],
        ['tour', 'spot_catalog_festival'], ['tour', 'spot_detail'], ['tour', 'spot_detail_image'],
        ['tour', 'spot_detail_intro'], ['tour', 'spot_detail_info'], ['tour', 'spot_festival'],
        ['tour', 'spot_reaction'], ['tour', 'congestion_area'], ['tour', 'district'],
        ['tour', 'kto_visitor_count'], ['tour', 'kto_concentration_rate'],
        ['tour', 'kto_stay_intensity'], ['tour', 'kto_spending_intensity'],

        -- category — 4
        --
        -- tourism 패키지 소속이지만 tour와 분리한다. `app_category`가 `app_key`(어느 앱이냐)로
        -- 키가 잡혀 있어 구조적으로 한 앱의 테이블이 아니고, community가 이미
        -- `CategoryConfigAdapter`로 소비한다.
        --
        -- `category_external_mapping`은 TourAPI 코드 공간이라 성격은 tour에 가깝지만
        -- `category.id`를 참조하는 FK가 있다. 스키마 내부에 FK를 가두려고 여기 둔다 —
        -- 순수성보다 FK 국소성이다.
        ['category', 'category'], ['category', 'category_translation'],
        ['category', 'category_external_mapping'], ['category', 'app_category'],

        -- batch — Spring Batch 메타 6
        --
        -- tourism 잡 6개와 community 잡(messageRetentionJob)이 **공유**하므로 어느 컨텍스트
        -- 스키마에도 넣을 수 없다. 프레임워크 소유 저장소라 독립 스키마가 맞다.
        --
        -- 실제 카탈로그 이름은 소문자다 — Spring Batch의 기본 DDL이 따옴표 없이 만들었다.
        ['batch', 'batch_job_instance'], ['batch', 'batch_job_execution'],
        ['batch', 'batch_job_execution_params'], ['batch', 'batch_job_execution_context'],
        ['batch', 'batch_step_execution'], ['batch', 'batch_step_execution_context']
    ];
BEGIN
    FOREACH target SLICE 1 IN ARRAY mapping LOOP
        IF EXISTS (
            SELECT 1 FROM pg_tables WHERE schemaname = 'public' AND tablename = target[2]
        ) THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA %I', target[2], target[1]);
        END IF;
    END LOOP;
END $$;

-- **이 세 시퀀스는 테이블을 따라가지 않는다.** Spring Batch가 독립 객체로 만들었으므로
-- 명시적으로 옮겨야 하며, 빠뜨리면 배치가
-- `relation "batch.batch_job_instance_seq" does not exist`로 잡 실행 시점에 죽는다.
--
-- 실측 이름이 `batch_job_instance_seq`다(스펙 §4의 `BATCH_JOB_SEQ`가 아니다) — Boot 4의
-- 기본 DDL 스크립트가 그렇게 만든다. 나머지 애플리케이션 시퀀스는 전부
-- `GENERATED ... AS IDENTITY`가 만든 소유(owned) 시퀀스라 테이블과 함께 자동으로 옮겨진다.
DO $$
DECLARE
    seq TEXT;
BEGIN
    FOREACH seq IN ARRAY ARRAY[
        'batch_job_instance_seq', 'batch_job_execution_seq', 'batch_step_execution_seq'
    ] LOOP
        IF EXISTS (
            SELECT 1 FROM pg_sequences WHERE schemaname = 'public' AND sequencename = seq
        ) THEN
            EXECUTE format('ALTER SEQUENCE public.%I SET SCHEMA batch', seq);
        END IF;
    END LOOP;
END $$;

-- ── 7. 트리거 함수 재배치 (R6) ─────────────────────────────────────
--
-- 함수는 트리거가 붙은 테이블의 소유 스키마에 둔다. 세 함수가 거의 같은 코드지만 하나로
-- 합치지 않는다 — 합치면 community가 tour 스키마의 함수를 부르게 되고, 그 결합이 바로 이
-- 스펙이 제거하려는 것이다.
--
-- 트리거 정의는 함수 OID를 들고 있어 함수를 옮겨도 계속 같은 함수를 가리킨다. 즉
-- `search_path`에 의존하지 않으며 트리거를 다시 만들 필요가 없다.
--
-- 테이블과 같은 이유로 가드를 둔다(재적용 시 `public`에 이미 없다).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
               WHERE n.nspname = 'public' AND p.proname = 'spot_sync_location') THEN
        ALTER FUNCTION public.spot_sync_location() SET SCHEMA tour;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
               WHERE n.nspname = 'public' AND p.proname = 'spot_catalog_sync_location') THEN
        ALTER FUNCTION public.spot_catalog_sync_location() SET SCHEMA tour;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
               WHERE n.nspname = 'public' AND p.proname = 'community_post_spot_sync_location') THEN
        ALTER FUNCTION public.community_post_spot_sync_location() SET SCHEMA community;
    END IF;
END $$;

-- 함수 본문의 `ST_MakePoint`·`ST_SetSRID`는 스키마 한정 없이 호출되므로 실행 시점의
-- search_path로 해석된다. PostGIS 설치 스키마(public)가 search_path에 남아 있어야 한다 —
-- 앱은 Hikari `connection-init-sql`로, Flyway는 자체 커넥션의 기본 스키마로 그것을 보장한다.
