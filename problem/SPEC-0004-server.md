> spec: SPEC-0004 · source: `specs/SPEC-0004/spec.md` (Obsidian) · status: draft

# SPEC-0004 · 관광지 데이터 자체 적재 — 서버 설계

요구사항·수용 기준·컨트랙트는 스펙에 있다. 이 문서는 **서버 레포에 국한된 설계 결정**만 다루며,
요구사항은 번호(R-n)로, 수용 기준은 AC-n으로 인용한다.

이슈: [#25](https://github.com/HwangJunha/ieuri/issues/25) · 브랜치: `feature/SPEC-0004-spot-persistence` (`dev`에서 컷)

---

## 0. 이 문서를 읽기 전에

이 스펙은 **SPEC-0002가 세운 "관광 데이터는 적재하지 않는다"를 번복한다.** 다만 SPEC-0002는 그 번복을
예견하고 이음매를 남겨 뒀다 — `TourInfoPort`의 주석이 *"향후 DB 적재로 전환 = 이 포트의 구현체 교체만.
도메인·application·GraphQL 무변경"* 이라고 명시하고 있다. 이 설계는 그 약속을 실제로 이행하는 작업이다.

세 가지가 동시에 신규 도입된다. **현재 레포에 셋 다 없다:**

| 항목 | 현황 | 확인 근거 |
|---|---|---|
| Spring Batch | 없음 | `build.gradle.kts`에 `spring-boot-starter-batch` 없음, `BATCH_*` 테이블 없음 |
| 스케줄링 | 없음 | `@Scheduled`·`@EnableScheduling`·`TaskScheduler` 전무 |
| 비동기·큐 | 없음 | `@Async`·`ExecutorService`·`BlockingQueue` 전무. 이벤트는 auth 도메인의 Spring ApplicationEvent뿐 |
| PostGIS | 없음 | V1~V12 어디에도 `CREATE EXTENSION` 없음. Hibernate Spatial 등 공간 의존성도 없음 |

셋 다 처음이므로 각각의 도입 자체가 설계 결정이며, 기존 패턴을 따라갈 선례가 없다.

---

## 1. 아키텍처 배치

DDD + 헥사고날 + CQRS 구조를 유지한다. 신규 구성 요소의 계층 배치:

```
tourism/
├── domain/
│   ├── aggregate/Spot.kt            (기존, 변경 없음 — JPA 애노테이션 금지, AC-51)
│   ├── aggregate/SpotDetail.kt      (기존, 변경 없음 — 순수 Kotlin 유지)
│   └── vo/Distance.kt               (기존 — §4.3 참조)
├── application/
│   ├── port/out/client/TourInfoPort.kt      (기존 인터페이스, 변경 없음)
│   ├── port/out/cache/SpotCachePort.kt      (기존, 변경 없음 — 제거하지 않는다)
│   ├── port/out/persistence/SpotStorePort.kt         (신규 — 목록 읽기·쓰기)
│   ├── port/out/persistence/SpotDetailStorePort.kt   (신규 — 상세 읽기·쓰기)
│   ├── port/out/queue/SpotDetailWriteQueuePort.kt    (신규 — write-behind 투입구)
│   └── service/SpotQueryService.kt          (수정 — 폴백 순서 재구성)
└── infrastructure/
    ├── adapter/out/tourapi/TourApiClient.kt          (기존 — 배치가 재사용)
    ├── adapter/out/cache/ValkeySpotCache.kt          (기존, 변경 없음)
    ├── adapter/out/persistence/SpotJpaAdapter.kt     (신규)
    ├── adapter/out/persistence/entity/SpotEntity.kt  (신규 — JPA 엔티티, 도메인 모델과 별개)
    ├── adapter/out/queue/InMemorySpotDetailWriteQueue.kt  (신규)
    └── batch/                                        (신규 — 동기화 잡)
```

**AC-52(의존 방향)의 실질적 의미.** application이 infrastructure를 직접 참조하지 않는다는 것은,
`SpotQueryService`가 `SpotJpaAdapter`나 `SpotEntity`를 절대 import하지 않는다는 뜻이다. 포트 인터페이스만
본다. 배치 잡은 infrastructure에 있으므로 어댑터를 직접 써도 무방하다.

**AC-51(JPA 미침투).** `SpotEntity`(JPA)와 `Spot`(도메인)은 별개 클래스이며 어댑터가 매핑한다. 이는
`spot_reaction`이 이미 따르는 패턴이다(`SpotReactionEntity` ↔ 도메인). 도메인 모델에 `@Entity`를 붙여
한 클래스로 합치려는 유혹을 거부할 것 — `Spot.kt` 주석이 이를 명시적으로 금지하고 있다.

---

## 2. `spot` 테이블 (R-1)

### 2.1 마이그레이션 순서

최신 마이그레이션이 `V12__index_spot_reaction_like_count.sql`이므로 `V13`부터다.

```
V13__enable_postgis.sql          CREATE EXTENSION postgis
V14__create_spot.sql             spot 테이블 + 인덱스
V15__create_spot_detail.sql      상세 계열 테이블 ([TBD-7] 확정 후)
```

**`V13`을 별도 파일로 분리하는 이유.** 확장 활성화는 DB 슈퍼유저 권한을 요구할 수 있고, 운영 인스턴스에서
실패할 가능성이 `[TBD-3]`로 열려 있다. 테이블 생성과 한 파일에 묶으면 확장 실패 시 테이블까지 롤백되어
원인 파악이 흐려진다.

### 2.2 컬럼 설계

`(content_id, language)` 유니크는 **국문·영문·일문·중문의 `contentId`가 서로 다르다**는 사실에서 나온다
(R-1 근거). 이 패턴은 신규가 아니라 `V11__create_spot_reaction.sql`이 이미 쓰고 있다.

필요한 컬럼은 `TourApiClient`의 현재 매핑에서 역산한다. `locationBasedList2` → `NearbySpotData` → `Spot`
경로가 실제로 소비하는 업스트림 키:

| 도메인 필드 | 업스트림 키 | 적재 컬럼 | 비고 |
|---|---|---|---|
| `id` | `contentid` | `content_id` | 유니크 키 구성요소 |
| `title` | `title` | `title` | 없으면 항목 드롭 |
| `address` | `addr1` + `addr2` | `address` | 공백 조인, 둘 다 비면 null |
| `thumbnailImageUrl` | `firstimage` | `thumbnail_image_url` | 없으면 항목 드롭, `https://` 정규화 |
| `coordinates.latitude` | `mapy` | — | §3 참조 |
| `coordinates.longitude` | `mapx` | — | §3 참조 |
| `category` | `contenttypeid` | `category` | §2.3 참조 |
| `copyright` | `cpyrhtDivCd` | `copyright` | §2.4 참조 |
| `distance` | *(업스트림 아님)* | — | 조회 시 계산, 적재 대상 아님 |
| `attributionText` | *(파생)* | — | `copyright`에서 파생, 적재 불필요 |

`distanceMeters`는 **요청 좌표에 종속**되므로 적재할 수 없다. 이는 명백하지만, `areaBasedSyncList2`가
`dist`를 준다고 해서 그걸 적재하려는 실수를 방지하기 위해 적어 둔다.

`attributionText`도 `copyright`에서 결정론적으로 파생되므로 컬럼으로 두지 않는다 — 두면 둘이 어긋날 수 있다.

### 2.3 `category` 적재 방식 — 원본 코드를 함께 보관한다

**위험 4(스펙)**: `TourApiClient.category()`가 코드 해석 실패 시 `?: SpotCategory.TOURIST_SPOT`으로 조용히
떨어진다. 요청 단위에서는 간헐적이지만 배치에서는 대량으로 적용될 수 있다.

**결정: `category`(해석된 enum)와 `content_type_id`(원본 코드)를 둘 다 적재한다.**

이유는 두 가지다.

1. **폴백 감지.** 원본 코드가 남아 있으면 "몇 건이 폴백으로 `TOURIST_SPOT`이 되었는가"를 사후에 질의할 수
   있다. 원본을 버리면 대량 오분류가 일어나도 알 방법이 없다.
2. **설정 변경 반영.** 현재는 `properties.categoryCodes`를 고치면 다음 요청부터 반영된다. 적재 후에는
   다음 배치까지 반영되지 않는데, 원본 코드가 있으면 재해석 마이그레이션으로 배치를 기다리지 않고
   교정할 수 있다.

원본 코드는 **적재 전용**이다. `TourApiClient`가 이미 원본 필드명·코드를 응답·로그에 노출하지 않도록
지키고 있으므로(SPEC-0002 R8·AC-21), 이 컬럼이 GraphQL 응답이나 로그로 새어 나가서는 안 된다.

배치는 폴백 발생 건수를 실행 이력에 남긴다(R-7과 연계). 정상 실행에서 폴백이 수천 건 발생하면 그것은
설정 오류의 신호다.

### 2.4 `copyright`와 멱등 upsert의 상호작용 (R-4, 위험 5)

**위험 5(스펙)**: `UNSPECIFIED`가 일시적 분류에서 영구 분류가 된다. R-4의 멱등성을 해시 비교로 구현하면서
해시에 `cpyrhtDivCd`를 포함하지 않으면 낡은 분류가 무기한 남는다.

**결정: 변경 감지에 해시를 쓰지 않는다.** `INSERT ... ON CONFLICT (content_id, language) DO UPDATE SET ...`로
전체 컬럼을 무조건 덮어쓴다.

해시 비교는 쓰기 I/O를 아끼려는 최적화인데, 하루 1회 배치에서 그 최적화의 가치는 낮고 위험은 명확하다.
전량 덮어쓰기는 AC-10(행 수 불변)·AC-11(연속 실행 동일 결과)을 자명하게 만족시킨다.

`updated_at`을 무조건 갱신하면 "실제로 바뀐 것"을 구분할 수 없어지므로, 필요하다면 `ON CONFLICT`의
`WHERE` 절로 실제 변경 시에만 `updated_at`을 올린다. 다만 이는 관측 편의일 뿐 R-4의 요구는 아니다.

### 2.5 `last_synced_at` — `[TBD-1]`의 선택지를 열어 두는 컬럼

R-5(사라진 항목 미노출)는 `[TBD-1]`로 방식이 미정이다. 세 선택지 중 어느 것을 택하든 **마지막 동기화 시각
컬럼은 필요하거나 최소한 무해**하므로, 테이블 생성 시점에 넣어 둔다. 나중에 추가하면 전 행 백필이 필요하다.

| 선택지 | `last_synced_at` 활용 |
|---|---|
| (a) 물리 삭제 | 배치 완료 후 `WHERE last_synced_at < :runStartedAt` 삭제 |
| (b) soft delete | 같은 조건으로 `deleted_at` 마킹 |
| (c) 만료 | 조회 시 `WHERE last_synced_at > now() - interval` |

**(b) soft delete를 택할 경우의 계약 영향** — 스펙의 열린 질문에 있는 대로, `spots`에는 안 뜨는데
`spotDetail`은 낡은 데이터를 반환하는 상태가 되어 두 오퍼레이션의 가용성 의미가 갈린다. 현재는 둘 다 같은
라이브 TourAPI 상태를 보므로 이런 괴리가 없다. (b)를 택한다면 `spotDetail`도 soft delete를 존중할지
결정해야 하며, 그 결정은 `SPOT_NOT_FOUND`의 의미를 바꾸므로 `[AMEND]` 대상이다.

### 2.6 AC-14 — 빈 응답과 "실제로 없음"의 구분

**AC-14는 이 테이블 설계에서 가장 놓치기 쉬운 요구다.** 업스트림이 일시 오류로 빈 목록을 반환했을 때
기존 데이터를 전량 삭제하면 안 된다.

`last_synced_at` 기반 삭제(§2.5)는 이 함정에 정확히 빠진다 — 배치가 0건을 수집하고 정상 종료하면
**모든 행이 `last_synced_at < runStartedAt` 조건에 걸려 전량 삭제된다.**

방어는 삭제 조건이 아니라 **삭제 실행 여부의 게이트**에 둔다:

- 해당 언어의 수집 건수가 0이면 정리 단계를 건너뛴다.
- 수집 건수가 직전 성공 실행 대비 급감하면(임계 비율은 서버 하네스 판단) 정리를 건너뛰고 실행 이력에
  경고를 남긴다.
- 정리는 **모든 페이지가 성공한 실행에서만** 수행한다. R-3이 부분 실패를 허용하므로, 부분 실패 실행의
  수집 결과로 정리하면 실패한 페이지의 항목이 전부 "사라진 것"으로 오인된다.

마지막 항목이 특히 중요하다. R-3(부분 실패 허용)과 R-5(사라진 항목 정리)는 순진하게 조합하면 서로를
망가뜨린다.

---

## 3. PostGIS와 공간 조회 (R-9, R-10)

### 3.1 도입 근거를 정확히 할 것

**거리 계산의 정확도 때문이 아니다.** 현재도 TourAPI의 `dist` 필드를 쓰지 않는다 — `Distance.between()`이
Haversine으로 직접 계산하며, `TourApiClient` 주석에도 *"거리·정렬은 application이 담당한다"* 고 적혀 있다.

PostGIS가 필요한 이유는 **반경 필터링을 인덱스로 처리**하기 위해서다. 지금은 TourAPI가 반경 안의 결과만
주므로 서버가 필터링할 일이 없었다. 전량 적재하면 "반경 20km 안"을 DB가 풀어야 하고, Haversine을 SQL
표현식으로 전 행에 계산하면 인덱스를 못 탄다(AC-24 위반).

### 3.2 컬럼과 인덱스

```sql
location geography(Point, 4326) NOT NULL
```

`geometry`가 아니라 `geography`를 쓴다. `geography`는 미터 단위 거리를 직접 다루고 `ST_DWithin`이 구면
거리로 동작하므로 SRID 변환 없이 R-9·R-10을 만족시킨다. `geometry(Point, 4326)`을 쓰면 도(degree) 단위가
되어 미터 반경 조건을 표현하려면 변환이 필요하다.

```sql
CREATE INDEX idx_spot_location ON spot USING GIST (location);
```

**`mapx`/`mapy`를 별도 컬럼으로도 보관한다.** `Spot.mapX`/`mapY`가 GraphQL 응답 필드이므로 `location`에서
`ST_X`/`ST_Y`로 매번 추출하는 것보다 원본을 그대로 두는 편이 단순하고, 적재 시 좌표 이상을 검증하기도 쉽다.
`location`은 이 둘에서 파생되는 조회 전용 컬럼으로 취급한다.

### 3.3 조회 쿼리와 AC-24·AC-25

```sql
SELECT ...
FROM spot
WHERE language = :language
  AND ST_DWithin(location, :center, :radiusMeters)
  AND (:category IS NULL OR category = :category)
ORDER BY ST_Distance(location, :center), id
LIMIT :size OFFSET :offset
```

**`ORDER BY`의 `, id`를 빠뜨리지 말 것.** 위험 9(스펙)가 지적하는 회귀다. 현재
`SpotQueryService`는 `compareBy({ it.distance.meters }, { it.id })`로 타이브레이크를 **이미** 적용하고 있으며
POL-019(정렬은 계약)를 준수하는 상태다. `ORDER BY ST_Distance(...)`만 쓰고 "인덱스 순서가 어차피 안정적"이라
가정하면 등거리 두 행의 순서가 동일 요청 간에 달라진다. SDL에는 보이지 않는 회귀다.

`ST_DWithin`이 GiST 인덱스를 타야 AC-24를 만족한다. `ST_Distance(...) < :radius`로 쓰면 인덱스를 못 타므로
**필터는 반드시 `ST_DWithin`, 정렬만 `ST_Distance`** 로 분리한다. 이 구분이 AC-24의 실질적 내용이다.

### 3.4 거리 값 드리프트 (R-10, 위험 2, `[TBD-4]`)

Haversine은 구체 근사(`EARTH_RADIUS_METERS = 6_371_000.0`), PostGIS `geography`는 WGS84 타원체다. 값이
다르며 고위도·장거리에서 차이가 커진다. 한국 위도대·20km 반경에서는 실용상 무시할 수준이지만
`[TBD-4]`가 허용 오차를 미정으로 남겼으므로 AC-26을 판정하려면 기준이 필요하다.

**제안: 상대 오차 0.5% 또는 절대 오차 50m 중 큰 값.** 근거는 두 모델의 이론적 차이가 이 범위 안에 들고,
클라이언트가 거리를 표시할 때 이 정도 차이는 표시 단위(보통 100m 단위 반올림)에 흡수된다는 점이다.
확정은 서버 하네스가 실측 후 판단한다.

**`Distance.between()`을 제거하지 말 것.** 조회 경로가 PostGIS를 쓰더라도 이 함수는 도메인 값 객체의
일부이며 테스트·검증에서 기준값 역할을 한다. AC-26의 "변경 전후 비교" 자체가 이 함수를 필요로 한다.

### 3.5 `[TBD-3]` 대비 — PostGIS를 못 쓰는 경우

운영 인스턴스에서 확장 설치가 불가하면 대안이 필요하다. 우선순위:

1. **`cube` + `earthdistance`** — Postgres 기본 contrib이라 PostGIS보다 설치 문턱이 낮을 수 있다.
   `earth_box`로 바운딩 박스 인덱스 조회가 가능하다.
2. **바운딩 박스 선필터 + 애플리케이션 Haversine** — `mapx`/`mapy`에 B-tree 복합 인덱스를 걸고 위경도
   범위로 후보를 좁힌 뒤 정확한 거리를 애플리케이션에서 계산한다. 확장이 전혀 필요 없고, `Distance.between()`을
   그대로 재사용하므로 위험 2(값 드리프트)가 아예 발생하지 않는다는 부수적 이점이 있다. 대신 후보 집합이
   반경보다 크므로 페이지네이션 계산이 복잡해진다.

2번을 택하면 AC-24("공간 인덱스 사용")의 문구가 맞지 않게 되므로 `[AMEND]` 대상이다.

---

## 4. Spring Batch 동기화 잡 (R-2 ~ R-7)

### 4.1 잡 구조

```
spotSyncJob
└── Step: syncLanguageStep  ×4 (KO, EN, JA, ZH)
    ├── ItemReader   : areaBasedSyncList2 페이지 리더
    ├── ItemProcessor: 응답 → SpotEntity 매핑 + 필터
    └── ItemWriter   : upsert
└── Step: cleanupStep (조건부 — §2.6의 게이트 통과 시에만)
```

**언어별 Step 분리 이유.** AC-7(부분 커밋)·AC-8(재개)이 실패 지점 재개를 요구하는데, 4개 언어를 한 Step에
넣으면 재개 지점 표현이 복잡해진다. Step 단위 분리는 Spring Batch가 기본 제공하는 재시작 지점과 맞물린다.

언어 간 병렬 실행은 하지 않는다. TourAPI serviceKey가 4개 언어 공용 단일 키이므로(SPEC-0002 §0 B-4 실측)
병렬 실행이 호출 한도를 4배 속도로 소모한다.

### 4.2 `areaBasedSyncList2` 호출 파라미터

**주의: 이 오퍼레이션은 이 레포에서 한 번도 호출된 적이 없다.** vault·레포 전체에 언급이 없으며,
SPEC-0002의 실측(2026-07-31)은 `locationBasedList2` 대상이었다.

확정된 것:

- `arrange=Q` — 수정일순, **대표이미지 필수**. TourAPI Swagger 기준 arrange 체계는 두 계열 대칭이며
  4개 언어 동일: 일반 `A`(제목순)·`C`(수정일순)·`D`(생성일순), 대표이미지 필수 `O`·`Q`·`R`.
  이것이 R-6(이미지 없는 항목 미노출)을 수집 단계에서 보장한다.
- `numOfRows=1000` — 사용자 지정. `locationBasedList2`의 `maxPageSize=100`과 다른 값이므로
  `TourApiProperties`의 기존 상한을 그대로 쓰면 안 된다. 배치 전용 페이지 크기 설정이 필요하다.
- `baseUrls[locale]` — 기존 설정 재사용. locale별 서비스 라우팅은 이미 동작한다.
- `serviceKey`, `mobileOs`, `mobileApp` — 기존 설정 재사용.

**미확정이라 실측이 필요한 것:**

- `numOfRows=1000`이 실제로 허용되는지. `locationBasedList2`는 100이 상한으로 실측되어 있다.
  1000이 거부되면 페이지 수가 10배가 되어 실행 시간 설계가 달라진다.
- 전체 건수와 페이지 수. 실행 시간·호출 한도 소모량 추정의 근거다.
- `arrange=Q` 정렬이 페이지 진행 중 갱신에 어떻게 반응하는지 — §4.4 참조.

### 4.3 `excludeLclssystm2`는 여전히 빈 목록

`application.yaml`에 `[TBD] 실데이터 표본으로 확정 — 초기 빈 목록`이라 적힌 채 비관광 항목 제외 기준이
미정이다. SPEC-0002에서 넘어온 미해결 항목이며 이 스펙이 해소하지 않는다.

**다만 전량 적재는 표본 확보를 오히려 쉽게 만든다.** 적재 후 `content_type_id`·`lcls_systm2` 분포를
질의하면 실데이터 표본이 즉시 나온다. 이 스펙의 부수 효과로 SPEC-0002의 TBD를 닫을 수 있으므로,
배치 도입 후 분포를 확인해 볼 것.

`lclsSystm2`를 제외 필터로 쓰려면 그 값도 적재해야 한다 — 컬럼 추가를 고려할 것.

### 4.4 R-3(부분 실패)과 `arrange=Q`의 상호작용

**이것이 이 배치 설계에서 가장 미묘한 지점이다.**

`arrange=Q`는 수정일순 정렬이다. 페이지 1을 읽고 페이지 2를 읽는 사이에 업스트림에서 어떤 항목이 수정되면
그 항목이 정렬 앞쪽으로 이동하고, 뒤의 항목들이 한 칸씩 밀린다. 결과적으로 **페이지 경계의 항목이
누락되거나 중복 수집된다.**

중복은 무해하다 — upsert가 멱등이므로(R-4). **누락이 문제**다. 누락된 항목은 `last_synced_at`이 갱신되지
않아 §2.5의 정리 단계에서 "사라진 항목"으로 오인된다.

완화 방안:

- **정리 단계를 전량 성공 실행에서만 수행**(§2.6)하는 것으로는 부족하다. 전량 성공해도 정렬 이동에 의한
  누락은 발생할 수 있다.
- `last_synced_at` 기준을 "이번 실행"이 아니라 **"최근 N회 실행 중 한 번이라도"** 로 완화하면 일시적 누락이
  삭제로 이어지지 않는다. N=3(3일) 정도가 합리적이다.
- 또는 정렬을 `arrange=O`(제목순, 대표이미지 필수)로 바꾸면 페이지 이동이 크게 줄어든다. 제목은 수정일보다
  훨씬 덜 변한다. **다만 이 경우 증분 동기화의 이점을 잃는다** — 수정일순은 "최근 바뀐 것이 앞에" 오므로
  향후 앞부분만 훑는 증분 전략이 가능하지만, 제목순은 그렇지 않다.

**제안: 초회는 `arrange=Q`로 전량 적재하되, 정리 기준을 최근 3회 실행으로 완화한다.** 증분 전략의 여지를
남기면서 누락 삭제를 막는다. 서버 하네스가 실측 후 조정할 것.

> **이 제안은 AC-13과 충돌한다.** AC-13은 *"동기화 완료 후"* 사라진 항목이 결과에서 빠질 것을 허용 시간
> 없이 요구한다. 3회 완화를 채택하면 실제로 사라진 항목이 최대 3일 노출되므로 AC-13이 문자 그대로는
> 성립하지 않는다.
>
> §2.6의 게이트와 이 완화는 서로 충돌하지 않고 **합성된다** — 게이트는 정리를 *할지*, 완화는 *얼마나
> 공격적으로 할지*를 정한다. 다만 게이트만으로는 부족하다. 전량 성공한 실행에서도 정렬 이동에 의한
> 누락은 발생하기 때문이다.
>
> 즉 AC-13을 문자 그대로 지키려면 오삭제를 감수해야 하고, 오삭제를 막으려면 AC-13을 완화해야 한다.
> 셋째 길은 `arrange=O`(제목순, 대표이미지 필수)로 전환해 페이지 이동 자체를 줄이는 것인데, 이 경우
> 수정일순이 주던 증분 동기화의 여지를 잃는다.
>
> 스펙은 이를 `[TBD-1a]`로 등록하고 런타임 계약 위험 10번에 논증을 남겼다. **`[TBD-1]`과 함께 결정할
> 것이며, 완화를 채택한다면 AC-13 개정은 `[AMEND]` 대상이다.**

### 4.5 R-7 — 실행 이력

Spring Batch의 `BATCH_JOB_EXECUTION`·`BATCH_STEP_EXECUTION` 메타 테이블이 AC-18·AC-19의 대부분을 자동으로
제공한다(시작·종료 시각, 상태, 실패 지점). 별도 이력 테이블을 만들 이유가 없다.

메타 테이블이 담지 못하는 것은 `ExecutionContext`에 남긴다:

- 언어별 처리 건수 (AC-18)
- 카테고리 폴백 발생 건수 (§2.3)
- 정리 단계 스킵 여부와 그 사유 (§2.6)

**Spring Batch 메타 테이블 도입은 마이그레이션 하나를 더 요구한다.** `BATCH_*` 테이블 DDL을 Flyway
마이그레이션으로 넣을지, `spring.batch.jdbc.initialize-schema`에 맡길지 결정해야 한다. **Flyway를 쓰는
프로젝트에서는 전자를 권한다** — 후자는 Flyway 이력에 남지 않아 스키마 형상 관리가 이원화된다.

### 4.6 AC-6 — 중복 실행 방지

단일 인스턴스라면 Spring Batch의 `JobInstance` 유일성이 자동으로 막는다(동일 파라미터의 잡은 재실행 불가).
잡 파라미터에 실행 일자를 넣으면 하루 1회가 자연스럽게 강제된다.

**다중 인스턴스로 배포된다면 이것으로 부족하다.** 두 인스턴스가 동시에 스케줄을 발화시키면 서로 다른
`JobExecution`이 생길 수 있다. Valkey를 이미 쓰고 있으므로 분산 락으로 게이트하는 것이 가장 적은 추가
의존이다. 현재 배포 형상이 단일인지 다중인지 확인 후 결정할 것.

---

## 5. 3단 폴백과 write-behind (R-13 ~ R-18)

### 5.1 조회 순서

현재 `SpotQueryService`의 패턴:

```kotlin
spotCachePort.getDetail(contentId, locale)
    ?: tourInfoPort.findDetail(query(contentId, locale))
        .also { spotCachePort.putDetail(contentId, locale, it) }
```

여기에 DB 단계를 끼운다:

```kotlin
spotCachePort.getDetail(contentId, locale)
    ?: spotDetailStorePort.findDetail(contentId, locale)
        ?.also { spotCachePort.putDetail(contentId, locale, it) }   // AC-35: Valkey 충전
    ?: tourInfoPort.findDetail(query(contentId, locale))
        .also {
            spotCachePort.putDetail(contentId, locale, it)
            writeQueuePort.enqueueDetail(contentId, locale, it)      // AC-39: 비동기
        }
```

이 형태가 AC-34~37을 직접 만족시킨다. `gallery`·`intro`·`infoItems`도 동일한 3단 구조를 각각 갖는다
— 이것이 R-18(지연 조회 구조 유지)의 실질적 내용이다.

**`likeCount`는 이 경로에 넣지 않는다**(AC-38). `SpotQueryPort.countLikes()`는 완전히 다른 포트이며
`spot_reaction` 테이블 기반이다. 스펙의 위험 6이 지적하듯 R-1~R-20 어디에도 이것이 직접 명시되어 있지
않으므로, 코드에 주석으로 남길 것.

### 5.2 큐 구현 (R-14, R-15)

`BlockingQueue` + 전용 `ExecutorService`. Spring `@Async`도 가능하지만 큐 상한과 거부 정책(`[TBD-5]`)을
명시적으로 제어하려면 `ThreadPoolExecutor`를 직접 구성하는 편이 낫다.

**`[TBD-5]`에 대한 제안: 유계 큐 + `DiscardPolicy`.**

R-15(가용성 유지)·AC-42(조회 미차단)를 만족시키려면 블로킹은 배제된다. `CallerRunsPolicy`는 호출 스레드가
적재를 수행하므로 AC-39(응답 비지연)를 위반한다. 남는 것은 폐기이며, write-behind는 **캐시 충전이지
데이터 보존이 아니므로** 폐기해도 정합성이 깨지지 않는다 — 다음 조회 때 다시 큐에 들어간다.

폐기 시 카운터를 증가시켜 관측 가능하게 할 것(AC-43의 "기록된다").

### 5.3 AC-41 — 동시 조회 시 중복 행

같은 항목을 여러 요청이 동시에 조회하면 큐에 같은 항목이 여러 번 들어간다. 유니크 제약 +
`ON CONFLICT DO UPDATE`로 흡수한다. 큐 단계에서 중복 제거를 시도하는 것보다 DB 제약에 맡기는 편이 단순하고
확실하다.

### 5.4 AC-44·AC-45 — 종료 안전성

**현재 레포에 graceful shutdown 패턴이 전무하므로 신규 도입이다.**

- `spring.lifecycle.timeout-per-shutdown-phase` 설정과 `server.shutdown=graceful`
- Executor에 `setWaitForTasksToCompleteOnShutdown(true)` + 유한 대기
- 각 적재 작업은 **단일 트랜잭션**이어야 한다. AC-44("부분 기록된 손상 데이터 없음")는 종료 시점에 절반만
  쓰인 상태가 없어야 한다는 뜻이며, 트랜잭션 경계가 이를 보장한다.
- AC-45(종료 중 조회 실패 금지) — 종료 중 큐 투입이 거부되어도 조회 응답 경로는 영향받지 않아야 한다.
  §5.2의 폐기 정책이 이를 자연스럽게 만족시킨다.

### 5.5 `[TBD-6]` — DB 상세 데이터의 신선도

Valkey는 TTL 24h인데 DB가 무기한이면 **"Valkey 만료 → DB 조회 → 6개월 된 데이터"** 가 정상 경로가 된다.
이는 R-17이 막으려는 상태 그 자체다.

**제안: `fetched_at` 컬럼 + 조회 시 나이 확인.** 임계(예: 7일)를 넘으면 DB 값을 반환하되 큐에 갱신 요청을
넣는다. 사용자는 즉시 응답을 받고, 다음 조회부터 신선한 값이 나온다. write-behind와 같은 메커니즘을
재사용하므로 추가 구조가 필요 없다.

임계값은 서버 하네스 판단. Valkey TTL(24h)보다는 충분히 길어야 의미가 있다.

### 5.6 `[TBD-7]` — 테이블 분할

사용자가 구현 중 명시하기로 한 사항이다. **다만 이 결정이 스펙의 위험 7(요청 내 소스 혼재)의 발생 여부를
좌우한다**는 점을 결정 시 고려할 것.

| 방식 | 위험 7 | 비고 |
|---|---|---|
| (a) 본문과 하위 필드를 항상 함께 기록 | 대부분 소멸 | 지연 조회의 이점을 적재 단계에서 잃는다 — 요청되지 않은 필드까지 TourAPI를 쳐야 하므로 SPEC-0003 R7과 충돌 |
| (b) 오퍼레이션별 독립 기록 | 현실화 | 현재 Valkey 키 분리 구조(`detail`/`gallery`/`intro`/`info`)와 자연스럽게 대응 |

**(b)가 구조적으로 일관되지만 위험을 안고 간다.** (a)는 지연 조회 자체를 무력화하므로 R-18과 충돌한다.
실질적으로 (b)를 택하고 위험 7을 감수하는 것이 유일한 선택지에 가깝다 — 그렇다면 그 사실을 명시적으로
기록하고 `[AMEND]`로 회신하는 편이 낫다.

`intro`·`infoItems`는 카테고리별로 응답이 갈리므로 Valkey 키가 카테고리를 포함한다. DB 테이블도 같은
구분이 필요하다.

---

## 6. 계약 무변경의 실제 강제 (R-19, R-20)

### 6.1 AC-49 — SDL 동일성 검증

수동 확인에 맡기지 말 것. `tourism.graphqls`의 해시를 테스트로 고정하거나, 스키마 스냅샷 비교 테스트를
추가한다. 이 스펙의 핵심 주장이 "스키마가 안 바뀐다"이므로 그것을 검증하는 테스트가 있어야 한다.

### 6.2 AC-50 — 클라이언트 문서 실행

k-trip-spot의 `spot_operations.graphql`에 있는 4개 오퍼레이션(`Spots`, `AvailableSpotCategories`,
`SpotDetail`, `ReactToSpot`)을 그대로 서버 테스트에 넣어 실행한다. 클라이언트 레포를 건드리지 않고
서버에서 계약 회귀를 잡을 수 있다.

### 6.3 `UPSTREAM_UNAVAILABLE`의 처리

`TourismExceptions.kt`의 해당 주석에 *"DB 폴백 없음"* 이라 적혀 있는데 **R-8 이후 거짓이 된다.**
`spots`에서 TourAPI가 요청 경로에서 완전히 빠지므로 이 코드는 `spots`에 대해 도달 불가능해진다
(`spotDetail`의 TourAPI 폴백 단계에서는 여전히 유효).

최소한 주석은 갱신할 것. 코드를 형식적으로 퇴역시킬지는 판단 사항이며, 클라이언트가 `spots` +
`UPSTREAM_UNAVAILABLE` 조합을 특수 처리하고 있다면 그 경로가 죽는다는 점을 알릴 것.

### 6.4 신규 오류 코드를 만들 경우

`[TBD-8]`(배치 미실행·테이블 빔)·`[TBD-9]`(PostGIS·DB 장애)에서 새 코드를 만들기로 결정하면
**클라이언트가 target이 아닌 상태에서 새 코드가 나간다.** 에러 코드가 `String!`이라 SDL 변경도 codegen
파손도 아니지만, k-trip-spot에 그 코드를 처리할 작업이 계획되어 있지 않다.

기본 권장은 두 경우 모두 **전송 계층 실패(5xx)** 로 두는 것이다. 백킹 스토어 부재는 계약 오류가 아니라
인프라 장애이며, tourism 도메인에 그런 코드의 선례가 없다. 새 코드를 만들기로 한다면 `[AMEND]`로 회신할 것.

---

## 7. 작업 순서 제안

1. `V13` PostGIS 확장 — **가장 먼저.** `[TBD-3]`이 여기서 판명되며, 실패하면 §3.5의 대안으로 설계가
   바뀌므로 뒤의 작업이 헛수고가 된다.
2. `V14` `spot` 테이블 + GiST 인덱스
3. `SpotEntity`·`SpotJpaAdapter`·`SpotStorePort` — 적재 없이 읽기 경로만 먼저
4. Spring Batch 의존성 + 메타 테이블 마이그레이션
5. `spotSyncJob` — 언어 1개(KO)로 먼저 검증, 이때 §4.2의 미확정 항목을 실측
6. 4개 언어 확장 + 정리 단계(§2.6의 게이트 포함)
7. `SpotQueryService` 조회 경로를 DB로 전환 — AC-20·AC-21 검증
8. `V15` 상세 계열 테이블 (`[TBD-7]` 확정 후)
9. 큐 + write-behind + 3단 폴백
10. graceful shutdown
11. AC-49·AC-50 회귀 테스트

7번까지가 목록 기능의 완결이며, 여기까지만 해도 이 스펙의 주요 목적(응답 지연 해소)이 달성된다.
8~10번은 상세의 개선이며 독립적으로 진행 가능하다.

---

## 8. 이 문서가 결정하지 않은 것

| # | 내용 | 이 문서의 입장 |
|---|---|---|
| `[TBD-1]` | 사라진 항목 처리 방식 | §2.5에 세 선택지의 컬럼 요구를 정리. soft delete 시 계약 영향을 경고. 선택은 미결 |
| `[TBD-1a]` | 정리 시점의 허용 창 | §4.4에서 "최근 3회 완화"를 제안하되 **AC-13과 충돌함을 명시**. `[TBD-1]`과 함께 결정해야 하며 완화 채택 시 AC-13 개정은 `[AMEND]` |
| `[TBD-3]` | PostGIS 설치 가능 여부 | §3.5에 대안 2종 제시. 작업 순서 1번으로 배치해 조기 판명 |
| `[TBD-4]` | 거리 허용 오차 | §3.4에서 "상대 0.5% 또는 절대 50m 중 큰 값" 제안. 실측 후 확정 |
| `[TBD-5]` | 큐 상한·초과 동작 | §5.2에서 "유계 큐 + 폐기" 제안. 블로킹은 R-15와 충돌하므로 배제 |
| `[TBD-6]` | DB 상세 갱신 기준 | §5.5에서 "`fetched_at` + 나이 확인 후 큐 갱신" 제안. 임계값 미정 |
| `[TBD-7]` | 상세 테이블 분할 | §5.6에서 (b) 독립 기록이 사실상 유일한 선택지임을 논증. 위험 7 감수 시 `[AMEND]` 권고 |
| `[TBD-8]` `[TBD-9]` | 신규 실패 모드 표면 | §6.4에서 전송 계층 실패 권장. 새 코드 생성 시 `[AMEND]` 필요 |
| — | 배포 형상 (단일/다중 인스턴스) | §4.6의 중복 실행 방지 방식이 여기에 종속. 확인 필요 |
| — | `numOfRows=1000` 실제 허용 여부 | §4.2. 미실측이며 거부되면 실행 시간 설계가 바뀜 |
