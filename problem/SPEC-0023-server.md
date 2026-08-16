> spec: SPEC-0023 · source: `Specs/SPEC-0023/spec.md` (Obsidian) · status: draft

# SPEC-0023 — DB 스키마 5분할 · 서버 설계

요구사항·수용 기준·배치표는 스펙에 있습니다. 이 문서는 ieuri-server 안에서의 설계 판단만 다룹니다.

## 1. 이 변경이 싼 이유

운영 서버가 없습니다. 따라서 아래가 전부 불필요합니다.

- 이중 쓰기(dual-write) 기간
- 백필 윈도우
- 롤백 안무
- 무중단 전환 절차

`ALTER TABLE ... SET SCHEMA`는 pg_class의 `relnamespace`를 갱신하는 카탈로그 연산이라 데이터 재작성도, 테이블 재작성 락 이상의 비용도 없습니다. **단일 마이그레이션 일괄 전환**이 성립하며, 이 스펙은 그 전제 위에 서 있습니다.

## 1.1 왜 DB가 아니라 스키마인가 (2026-08-16 확인)

이 저장소는 최종적으로 MSA를 지향합니다. 그러면 "DB부터 나눠야 하는 것 아닌가"가 자연스러운
질문이고, 실제로 그렇게 진행하지 않은 이유를 남깁니다.

Postgres는 3단입니다 — **서버(`localhost:5432`) → 데이터베이스(URL의 마지막 조각) →
스키마**. `localhost:5432/community`라고 쓰면 그 `community`는 스키마가 아니라 **데이터베이스**이며,
커넥션 하나는 데이터베이스 하나만 봅니다.

**분리의 실질은 이미 확보돼 있습니다.** 실 DB 조회 결과 컨텍스트 경계를 넘는 FK가 **0건**이고
(경계 내부 FK만 존재), 컨텍스트 간 SQL도 0건이며, 카테고리 접근은 in-process 포트를 경유합니다.
MSA에서 지켜야 하는 것은 "DB 파일이 몇 개냐"가 아니라 **"한 컨텍스트가 남의 데이터를 직접
읽지 않느냐"**인데, 그 규칙은 코드가 이미 지키고 있습니다.

**스키마 분리는 DB 분리를 막지 않고 앞당깁니다.** 나중에 auth를 떼어낼 때 `pg_dump -n auth`
한 번이면 됩니다 — `public` 한 덩어리였다면 어느 테이블이 auth 소유인지 골라내는 일부터
시작해야 했습니다.

지금 DB까지 나눌 경우의 대가(커넥션 풀 5개, Flyway 이력 5벌, 공유 중인 `BATCH_*` 위치
재결정, 컨텍스트 간 조인·트랜잭션의 영구 차단)를 함께 검토한 뒤 **스키마 분리 유지**로
결정했습니다(사용자 결정). 스펙 §3의 비범위와 같은 결론입니다.

## 2. 마이그레이션 설계

### 2.1 파일 구성

현재 최신은 `V60__drop_community_feed.sql`입니다. 이번 변경은 **추가분으로만** 이뤄지며 기존 파일을 수정하지 않습니다(마이그레이션 불변 규약).

단일 파일을 권합니다 — 예: `V61__split_schemas.sql`. 스키마 생성·테이블 이동·시퀀스 이동·트리거 함수 재배치가 **한 트랜잭션에서 원자적으로** 끝나야 하기 때문입니다. 중간 실패 시 일부만 이동한 상태가 남으면 `ddl-auto: validate`가 어느 쪽으로도 통과하지 못합니다.

Flyway는 기본적으로 마이그레이션을 트랜잭션으로 감싸며, PostgreSQL은 DDL 트랜잭션을 지원하므로 이 원자성이 실제로 보장됩니다.

### 2.2 순서

1. `CREATE SCHEMA IF NOT EXISTS auth, community, tour, category, batch` (5개)
2. 애플리케이션 테이블 46개 `ALTER TABLE ... SET SCHEMA`
3. Spring Batch 테이블 6개 + 시퀀스 3개 `SET SCHEMA batch`
4. 트리거 함수 재배치 (§4)

FK는 스키마 이동을 자동으로 따라갑니다 — FK는 테이블 이름이 아니라 OID를 참조하므로 재생성이 필요 없습니다. **인덱스·제약·트리거도 마찬가지로 테이블을 따라 이동합니다.** 이것이 이 변경이 얇은 이유입니다.

### 2.3 하지 말아야 할 것

- `CREATE EXTENSION` 금지 (POL-034). 정책이 명시한 사유는 앱 DB 롤이 비-슈퍼유저라는 점이며, 정책 근거는 V15(PostGIS)에서 확립된 관행입니다. 실패 양상을 이 문서가 단정하지는 않습니다 — 금지 자체가 정책 사항입니다
- 기존 V1~V60 수정 금지. 과거 마이그레이션은 `public`에 테이블을 만들고, 이번 V61이 옮깁니다. 이력을 재작성하지 않습니다
- 테이블 이름 변경 금지. 스키마만 바꿉니다 — 두 가지를 같이 하면 복제 갱신이 두 배로 어려워집니다

## 3. JPA 매핑

### 3.1 방식

엔티티마다 `@Table(schema = "...")`을 적는 방식과, Hibernate `default_schema`를 쓰는 방식이 있습니다. **후자는 쓸 수 없습니다** — 스키마가 5개인데 `default_schema`는 하나뿐입니다.

따라서 **엔티티별 `@Table(schema = ...)` 명시**가 유일한 선택지입니다. 대상 패키지:

| 스키마 | 엔티티 패키지 |
|---|---|
| `auth` | `org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity` |
| `community` | `org.around.ieuri.community.infrastructure.adapter.out.persistence.entity` |
| `tour` · `category` | `org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity` |

**tourism 패키지가 두 스키마로 갈립니다.** 카테고리 4개 엔티티만 `category`, 나머지는 `tour`입니다. 패키지 위치로는 구분되지 않으므로 엔티티별로 확인해야 합니다. 이 어긋남은 감수합니다 — 패키지 이동은 이번 범위가 아닙니다.

스키마 이름을 리터럴로 흩뿌리지 말고 상수로 모아두는 편이 낫습니다. 오타가 나면 `validate` 단계에서야 드러나고, 그때 어느 엔티티인지 찾는 비용이 큽니다.

### 3.2 부팅 검증

`spring.jpa.hibernate.ddl-auto: validate`이므로 매핑과 실제 스키마가 어긋나면 부팅이 실패합니다. 이는 결함이 아니라 **이번 작업의 안전망**입니다 — 매핑 누락이 런타임까지 숨어들지 않습니다.

마이그레이션과 엔티티 변경은 **같은 배포에 함께** 들어가야 합니다. 순서를 나누면 어느 쪽을 먼저 배포해도 부팅이 실패합니다.

## 4. 트리거 함수와 PostGIS

### 4.1 함수 배치

현재 세 함수가 있고, 전부 `public`에 있습니다.

| 함수 | 트리거 대상 | 이동할 스키마 |
|---|---|---|
| `spot_sync_location()` | `spot` | `tour` |
| `spot_catalog_sync_location()` | `spot_catalog`, `spot_catalog_translation` | `tour` |
| `community_post_spot_sync_location()` | `community_post_spot` | `community` |

`ALTER FUNCTION ... SET SCHEMA`로 옮기거나, `community` 쪽은 이미 별도 함수가 존재하므로 그대로 옮기면 됩니다. 세 함수가 거의 동일한 코드라는 이유로 하나로 합치지 마십시오 — 합치면 community가 tour 스키마의 함수에 의존하게 되고, 그것이 이 스펙이 제거하려는 결합입니다.

트리거 본문이 함수를 스키마 한정 없이 부르면 `search_path`에 의존하게 됩니다. **트리거 정의에서 함수를 스키마 한정으로 호출**하는 편이 안전합니다.

### 4.2 PostGIS

`community_post_spot.location`이 살아 있으므로 **PostGIS는 tour 전용이 될 수 없습니다.** community도 geography 컬럼과 자체 트리거 함수를 갖고, `PostSpatialQueryAdapter`가 `ST_DWithin`·`ST_Distance`·`ST_MakePoint(...)::geography`를 실행합니다.

`geography` 타입이 `tour`·`community` 양쪽에서 해석되어야 하며, 해결 수단은 **매니페스트의 `search_path`**입니다(POL-034 — 확장 활성화는 CNPG 매니페스트 소유). 마이그레이션에서 다루지 않습니다.

PostGIS가 설치된 스키마(관례상 `public`)를 `search_path`에서 빼지 마십시오. 스키마 5개를 만들었다고 `public`을 정리하고 싶어지지만, PostGIS가 거기 있다면 제거 대상이 아닙니다.

## 5. Spring Batch

`BATCH_*`는 tourism 잡 6개와 community 잡 1개(`messageRetentionJob`)가 공유하므로 독립 스키마입니다.

- 스키마 지정은 `spring.batch.jdbc.table-prefix`로 하며 **설정 변경이지 마이그레이션이 아닙니다**
- 시퀀스 3개(`BATCH_JOB_SEQ`, `BATCH_JOB_EXECUTION_SEQ`, `BATCH_STEP_EXECUTION_SEQ`)가 테이블과 같은 스키마에 있어야 합니다. 이 프로젝트에서 시퀀스는 이것뿐입니다
- 잡 이름은 바꾸지 않습니다. `application-batch.yaml`의 `IEURI_TOUR_SYNC_JOB` 선택 방식도 그대로입니다

## 6. search-node

별도 데이터소스이고 Flyway 이력도 별개입니다(`db/search-node/`, V1~V7). 복제 대상 6개가 전부 tourism이므로 **`tour` 스키마 하나만** 만듭니다.

- 읽기 어댑터 3개(`SpotSearchJdbcAdapter`, `PopularSpotQueryJdbcAdapter`, `FestivalQueryJdbcAdapter`)는 `@Qualifier("searchJdbcTemplate")`로 접근합니다. 이들의 SQL에 스키마 한정이 필요한지 확인하십시오 — 현재는 `public` 전제입니다
- BM25 인덱스(`idx_spot_bm25`, `idx_spot_catalog_translation_bm25`)는 테이블을 따라 이동합니다
- 원본과 스키마 이름을 맞추면 Subscription 설정이 단순해집니다

## 7. 복제 — 가장 위험한 부분

**이 스펙에서 실제로 깨질 수 있는 유일한 지점입니다.**

논리 복제 publication은 테이블 이름으로 정의되며 `ALTER TABLE ... SET SCHEMA`를 자동으로 따라가지 않습니다. 원본에서 6개 테이블이 `tour`로 옮겨가면 publication이 가리키던 `public.<table>`이 사라집니다.

POL-036이 요구하는 복제 양립성 확인 대상이며, **SPEC-0013에서 확장 도입 후 replica 2개가 죽은 전례**가 있습니다. 그 실패는 배포 후에야 드러났습니다.

### 매니페스트 변경 요청

Publication/Subscription은 `ieuri-manifest` 저장소의 CNPG CRD입니다. **서버 요구사항에 의한 변경이므로 서버 측에서 요청**합니다. 요청에 담을 내용:

1. 원본 publication의 테이블 목록을 `tour` 스키마 한정으로 갱신
2. search-node subscription을 이에 맞춰 갱신
3. `search_path`에 PostGIS 설치 스키마 포함 (§4.2)
4. **누락 확인** — `V3__spot_catalog_replication.sql:11-14`과 `problem/SPEC-0019-server.md:440`은 `spot_catalog`·`spot_catalog_translation`·`spot_catalog_festival`이 publication에 아직 없다고 기록합니다. 실제 반영 여부를 이때 함께 확인합니다

원본 스키마 이동과 매니페스트 갱신은 **동시에** 반영되어야 합니다. 어느 한쪽만 나가면 복제가 끊기고, 끊긴 사실은 조회 결과가 비기 전까지 드러나지 않습니다.

## 8. 테스트

테스트 범위 규약대로 **단위 테스트(애플리케이션·도메인 계층)만** 둡니다. 스키마 배치의 실제 반영은 DB를 세워야 관찰 가능하므로 단위 테스트 대상이 아니며, 이는 인프라 관심사입니다.

단위 테스트로 지킬 수 있는 것은 **매핑 메타데이터의 정합성**입니다.

| 단위 | 검증 |
|---|---|
| 엔티티 매핑 검사 (AC-1, AC-2) | 모든 JPA 엔티티가 스키마를 명시하고, 그 (스키마, 테이블) 쌍이 스펙 §4 배치표와 일치 |
| 마이그레이션 정적 검사 (AC-3, AC-4, AC-5) | 신규 마이그레이션에 FK 신설 0건, `CREATE EXTENSION` 0건, 트리거 함수 스키마가 대상 테이블 소유 스키마와 일치 |
| 배치표 완전성 (AC-6) | 46개 전량 배정, 중복 0, 미배정 0 |

AC-2와 AC-6은 배치표를 테스트 안에 상수로 두면 자연스럽게 만족됩니다. 배치표가 곧 명세이므로, 표를 코드로 옮기고 엔티티 메타데이터와 대조하는 것이 가장 값싼 검증입니다.

R11(dangling 정리)은 애플리케이션 계층 로직이라 **이 스펙에서 유일하게 동작을 검증하는 단위 테스트**를 갖습니다 — §10.4를 보십시오.

**부팅 성공(R3), 복제 연속성(R9), PostGIS 해석(R5)은 단위 테스트로 증명할 수 없습니다.** 인프라에서만 관찰되는 관심사이므로 테스트를 만들지 않고 배포 확인 사항으로 남깁니다. 이를 위해 testcontainers나 DB 시딩 전략을 도입하지 마십시오.

## 9. 확인된 전제

착수 전 조사에서 확인된 사실입니다. 이 중 하나라도 틀리면 설계가 달라지므로, 구현 중 반증이 나오면 `[AMEND]`로 보고하십시오.

- **컨텍스트 간 FK 0건** — 모든 경계 교차 컬럼에 FK가 없고, 마이그레이션 주석에 의도로 기록됨
- **컨텍스트 간 SQL 0건** — `@Query(nativeQuery = true)` 부재. raw SQL은 JdbcTemplate뿐이며 community의 `PostSpatialQueryAdapter`는 community 테이블만, tourism의 raw SQL은 tourism 테이블만 접근
- **community의 category 접근은 in-process 포트 경유** — `CategoryConfigAdapter` → `tourism...SpotQueryUseCase`. SQL이 아니므로 스키마 분리가 깨뜨리지 않음
- **스키마 한정이 코드에 전무** — `@Table(schema=...)`, `default_schema`, `search_path`, `currentSchema` 모두 부재. 되돌릴 하드코딩은 없지만 기반도 없음
- **`community_post_spot`은 `map_x`/`map_y`/`location`을 비정규화 보유** — community 공간 쿼리가 tourism과 조인하지 않도록 한 결정. 이 상태를 유지해야 분리가 성립

## 10. dangling catalog_id 정리 (R11)

`community_post_spot.catalog_id`는 `NOT NULL`이면서 FK가 없어, `spot_catalog` 행이 삭제되면 매칭 불가능한 참조가 조용히 남습니다. 스키마 분리가 만든 결함은 아니지만 분리가 이를 영구화하므로 이번 스펙에서 함께 해결합니다(이슈 #86 통합).

### 10.1 FK로 풀지 않는 이유

`ON DELETE CASCADE`를 거는 것이 가장 짧은 해법으로 보이지만 **선택지가 아닙니다.** R4가 컨텍스트 간 FK를 금지하며, 그 결정은 마이그레이션 주석에 의도로 기록돼 있습니다. 스키마를 분리하는 작업에서 컨텍스트 간 FK를 새로 만드는 것은 이 스펙의 목적과 정면으로 충돌합니다.

### 10.2 이벤트 구독

`MemberWithdrawn`과 **같은 패턴**을 씁니다 — 이미 이 코드베이스에 있는 방식이라 새 구조를 도입하지 않습니다.

1. tourism이 `spot_catalog` 삭제 시 도메인 이벤트를 발행
2. community가 구독해 자신의 `community_post_spot` 참조를 정리
3. 전달은 현재 Spring ApplicationEvent (POL-028, 추후 Kafka)

구독 핸들러는 community의 애플리케이션 계층에 두고, 저장소 접근은 포트를 통합니다. tourism이 community 테이블을 직접 건드리지 않습니다.

### 10.3 정리 방식 — 연결 행 삭제로 확정

`catalog_id`가 `NOT NULL`이라 null 처리가 불가능하므로, **연결 행(`community_post_spot`)을 삭제**합니다. **글(`community_post`)은 삭제하지 않습니다.**

무효화 표시 컬럼을 추가하는 방안은 택하지 않았습니다. 모든 조회 경로에 필터 의무가 생기는 반면 얻는 것은 "삭제된 장소였다"는 이력뿐이고, 그 이력을 소비할 화면이 현재 없습니다.

**알려진 결과 — 반드시 확인하고 구현하십시오.** `PostSpatialQueryAdapter`가 `community_post` → `community_post_spot`을 조인하므로 연결이 끊긴 글은 **공간 검색 결과에서 빠집니다.** 글 자체는 목록·상세에 그대로 남습니다. 장소 기반 검색의 의미상 타당한 동작이지만, 구현 중 이것이 곤란하다고 판단되면 `[AMEND]`로 보고하십시오.

정리는 이벤트당 여러 행에 걸칠 수 있습니다(한 장소를 여러 글이 참조). 단건 처리로 가정하지 마십시오.

### 10.4 테스트

애플리케이션 계층이므로 **단위 테스트 대상입니다**(§8의 인프라 항목과 다릅니다). 저장소 포트를 스텁으로 두고 핸들러만 검증합니다.

| 단위 | 검증 | 대응 |
|---|---|---|
| 카탈로그 삭제 이벤트 핸들러 | 해당 카탈로그 참조에 정리 1회 호출, 다른 카탈로그 참조는 불변 | AC-8 |
| 〃 | 정리 대상 없을 때 무동작·무예외 | AC-9 |
| 마이그레이션 정적 검사 | `catalog_id`에 FK 신설 0건 | AC-10 |

## 11. 구현 중 확정·수정된 사항 (2026-08-16)

구현하면서 §9의 전제와 어긋난 것, 스펙이 지정한 수단이 실제로는 동작하지 않는 것이 나왔습니다.
전부 여기에 기록합니다.

### 11.1 `[AMEND]` R11 — 삭제 경로가 없다

**스펙의 전제가 코드와 어긋납니다.** R11은 "`spot_catalog` 행이 삭제되면"을 전제하지만,
**카탈로그를 삭제하는 경로가 코드베이스에 0건입니다.** `SpotCatalogStorePort`에 delete가 없고
(`save`·`assign`·`upsertTranslation`·`refreshFestivalPeriod`·`refreshTranslationVisibility`가
전부), 유일한 `DELETE`는 자식 행인 `spot_catalog_festival` 대상입니다.

이는 누락이 아니라 **의도된 설계**입니다 — `SpotCatalogStorePort` 주석이 밝히듯, 번역 행을
지우면 배정 배치의 KO 제목 색인이 카탈로그를 놓쳐 같은 장소가 새 카탈로그로 다시 생기고
좋아요와 글 연결이 고아가 됩니다. 그래서 삭제 대신 `refreshTranslationVisibility()`로 숨깁니다.

**사용자 결정: 그대로 구현.** 발행 지점이 없어도 이벤트·구독자·테스트를 만들어 두고, 삭제
경로가 생기는 시점에 발행만 추가하면 동작하도록 했습니다. 오늘 이 핸들러는 호출되지 않습니다 —
`SpotCatalogDeleted`와 리스너 주석에 그 사실을 남겼습니다.

### 11.2 `[AMEND]` R2 — `spring.batch.jdbc.table-prefix`는 동작하지 않는다

스펙 §5와 AC-7이 지정한 `spring.batch.jdbc.table-prefix`는 **Boot 4에 없는 키라 조용히
무시됩니다.** `application.yaml`의 기존 주석이 이미 그렇게 기록하고 있었습니다.

실제 접두사는 `@EnableJdbcJobRepository(tablePrefix = "batch.BATCH_")`가 소유합니다
(`BatchInfrastructureConfig`). AC-7의 검증 대상을 이 애노테이션 값으로 바꿔 `BatchSchemaConfigTest`가
확인합니다.

### 11.3 `[AMEND]` R2 — 시퀀스 이름이 스펙과 다르다

스펙 §4는 `BATCH_JOB_SEQ`라 적었으나 실측 이름은 **`batch_job_instance_seq`**입니다.
나머지 둘(`batch_job_execution_seq`·`batch_step_execution_seq`)은 스펙과 같습니다.

또한 **애플리케이션 시퀀스는 옮길 필요가 없습니다** — 전부 `GENERATED ... AS IDENTITY`가 만든
소유(owned) 시퀀스라 테이블과 함께 자동으로 이동합니다. 명시적 이동이 필요한 것은 Spring Batch가
독립 객체로 만든 위 세 개뿐입니다.

### 11.4 `[AMEND]` R5 — `search_path`는 매니페스트가 줄 수 없다

§4.2와 R5는 `search_path`를 CNPG 매니페스트의 몫으로 지정했지만, **CNPG에 그 필드가
없습니다.** CRD 정의 확인 결과:

- `managed.roles`의 `RoleConfiguration` — per-role SET 파라미터 필드 없음
  (`name`/`ensure`/`login`/`replication`/`inRoles`/`connectionLimit`… 뿐)
- `Database` CRD — `schemas`는 있으나 `search_path`는 없음

그래서 **서버가 Hikari `connection-init-sql`로 겁니다**:

- 메인: `SET search_path TO auth, community, tour, category, batch, public` (`application.yaml`)
- 검색 노드: `SET search_path TO tour, public, paradedb` (`SearchDataSourceConfig`)

URL이 아니라 여기 두는 이유는 URL이 환경변수로 통째로 주입되기 때문입니다 — `currentSchema`를
URL에 붙이면 주입값마다 다시 붙여야 하고, 한 곳만 빠뜨리면 그 환경에서만 조용히 틀립니다.

매니페스트는 **스키마라는 그릇까지만** 책임집니다(`database.schemas`). `public`은 PostGIS가
설치된 곳이라(실측: `extnamespace=public`) 두 경로 모두에 남겼습니다.

### 11.5 R10 확인 결과 — publication 누락은 이미 해소돼 있었다

`V3__spot_catalog_replication.sql:11-14`와 `problem/SPEC-0019-server.md:440`은
`spot_catalog`·`spot_catalog_translation`·`spot_catalog_festival`이 publication에 없다고
기록하지만, **매니페스트 실물에는 6개가 전부 있습니다**(`infra/cnpg/cluster/values.yaml`,
커밋 `9ba51ba` "search 테이블 추가"). 두 문서의 기록이 낡은 것입니다.

### 11.6 이벤트 위치 — `application/event`가 아니라 `domain/event`

`SpotCatalogDeleted`를 tourism의 기존 이벤트와 나란히 `application/event`에 두었더니
`LayeredArchitectureTest`의 도메인 격리 규칙이 막았습니다. 도메인 간 구독은 `domain/event`
타입으로만 허용되며(`MemberWithdrawn`이 auth의 `domain/event`에 있는 이유), 규칙이 맞습니다.

`TranslationTargetsFound`처럼 **tourism 내부에서만 소비되는** 이벤트는 계속
`application/event`에 남습니다. 구분 기준은 "구독자가 다른 도메인인가"입니다.

### 11.7 스키마를 가로지르는 raw SQL — 추출 시 확인 대상

리뷰에서 나온 지적이며, 이번 범위에서 고치지 않고 **기록만** 남깁니다.

검색 어댑터들(`PopularSpotQueryJdbcAdapter`·`FestivalQueryJdbcAdapter`·
`SpotSpatialQueryAdapter`·`MapSpatialQueryAdapter`)은 `spot_catalog`(→`tour`)를 읽으면서
`cat.category` 값으로 분류를 다룹니다. 카테고리 마스터 테이블은 `category` 스키마로 갔으므로,
이들 질의가 두 스키마에 걸치는 순간이 오면 `search_path` 덕분에 **조용히 동작합니다.**

`MigrationRuleTest`는 컨텍스트 간 **FK**가 없음을 증명하지만 **질의**가 경계를 넘지 않음은
증명하지 않습니다. 스키마 분리 이후 경계를 넘는 질의는 오류가 아니라 무증상이 되므로,
컨텍스트를 별도 DB로 들어낼 때 이 어댑터들이 확인 지점입니다.

정적 검사로 강제할 수는 있습니다(어댑터의 raw SQL 테이블 이름을 `SchemaPlacementTest`의
배치표와 대조). 이번에는 넣지 않았습니다 — 추출 계획이 없는 시점에 규칙부터 만들면 정당한
질의까지 막게 됩니다.

### 11.8 Flyway는 `connection-init-sql`이 닿지 않는다

리뷰에서 나온 지적입니다. 메인 Flyway 빈은 **Hikari 풀을 쓰지 않고 자체 커넥션을
만듭니다**(풀을 넘기면 정리 단계에서 `This connection has been closed`가 찍히기 때문).
따라서 `spring.datasource.hikari.connection-init-sql`이 마이그레이션에는 적용되지 않습니다.

`defaultSchema("public")`으로 이력 테이블 위치를 고정했고, **V62부터는 마이그레이션이 스키마
한정으로 쓰여야 한다**는 사실을 `DataSourceConfig`의 그 자리에 남겼습니다. V61까지는 테이블이
전부 `public`에 있어 한정 없이 써도 맞았으므로, 다음 작성자가 기존 61개를 보고 같은 방식을
따르기 쉽습니다.

`schemas(...)`로 5개를 나열하지는 않았습니다 — 그 목록은 Flyway가 소유를 주장하는 대상이라
없으면 만들고 `clean`에서 지웁니다. 스키마 생성자는 V61과 CNPG 매니페스트 둘로 충분합니다.

### 11.9 엔티티 없는 테이블 6개

배치표의 46개 중 JPA 엔티티가 있는 것은 40개입니다. 나머지 6개
(`congestion_area`·`district`·`kto_*` 4개)는 raw SQL 전용이라 `@Table(schema=...)`로 한정되지
않고 `search_path`로 해석됩니다. `SchemaPlacementTest`가 이 목록을 고정해, 새 엔티티가 생겼는데
스키마를 빠뜨리는 경우를 잡습니다.
