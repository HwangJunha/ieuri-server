# PERF-20260921 — `SpotCategory` 필드 리졸버 N+1 (론칭 차단)

> 2026-09-20~21 운영·개발 부하 테스트에서 발견. **재현 가능하고, 앱이 재시작되는 수준이다.**
> 측정 도구: k6, Grafana Cloud(Tempo/Prometheus), `EXPLAIN (ANALYZE, BUFFERS)`.

## 요약

`recommendedSpots` 같은 목록 쿼리가 `category { label iconUrl }`을 포함하면, **카테고리 종류마다
DB 조회가 따로 나간다.** 그 조회가 `ConcurrentHashMap.computeIfAbsent` 매핑 함수 **안에서**
일어나, 커넥션 풀이 마르면 같은 키를 기다리는 스레드가 연쇄로 묶인다.

결과: 커넥션 고갈 → 스레드 마비 → readiness/liveness 실패 → **503 + 파드 재시작**.

대상 파일: `tourism/infrastructure/adapter/in/graphql/CategoryDisplayResolver.kt`

---

## 실측

### 부하 테스트

| | 운영 (2 OCPU, 2파드) | 개발 (6 CPU, 1파드) |
|---|---|---|
| 붕괴 시작 | **5 RPS** | 50~100 RPS 구간 |
| TPS | 1.36 | 9.94 |
| p95 | 20.73s | 26.92s |
| VU 폭증 | 97 | 600 (상한) |
| 에러 | 0% (전부 대기) | 30.6% `503 no available server` |
| 파드 | — | **RESTARTS +1** |

**같은 코드가 양쪽에서 동일하게 무너졌다.** 운영이 먼저 터진 것은 하드웨어가 6배 느려
커넥션을 6배 오래 점유하기 때문이지, 운영 고유의 결함이 아니다.

### 원인이 아닌 것 (전부 배제됨)

| 후보 | 실측 | 판정 |
|---|---|---|
| 공간 쿼리(PostGIS) | 운영 127ms / dev 20ms, 인덱스 정상 | ❌ |
| pgvector 유사도 | 170ms (반응 9건 기준) | ❌ |
| 반응 조회 | 0.064ms | ❌ |
| 인덱스 누락 | `idx_spot_catalog_location` 정상 사용, 실행계획 dev=운영 동일 | ❌ |
| CPU throttling | `nr_throttled/nr_periods` = **1.0%** | ❌ |
| 트레이싱 오버헤드 | dev가 샘플링 100%인데 운영(10%)보다 78배 빨랐음 | ❌ |

**DB 총합은 약 170~300ms인데 앱 응답은 부하 시 20초를 넘었다.**

### 트레이스 (Tempo, 61 spans)

```
graphql field label   (5.45s)
  └ connection (5.44s)          ← 커넥션 획득 대기
      └ ieuri query (13.34ms)   ← 쿼리 자체는 13ms
      └ result-set (58.76µs)
graphql field iconUrl (5.45s)
graphql field label   (5.45s)
  └ connection (5.44s)
      └ ieuri query (1.25ms)
...
```

**모든 필드가 똑같이 5.45초다.** 각자 일한 것이 아니라 같은 락/커넥션을 기다렸다는 뜻이다.
`connection` 스팬이 요청 하나에 여러 개 나타난다.

### HikariCP (Grafana, 20:05~20:06)

```
HikariPool-1  active  10      (= maximum-pool-size 기본값, 상한)
HikariPool-1  idle    10 → 0
HikariPool-1  pending 65
Max Acquire Time      18.8s
Connection Timeout Rate  0 ops/s   ← 타임아웃 없이 끝까지 대기
search-pool   active  0, pending 0 ← 검색 풀은 무관
```

---

## 원인

### 코드

```kotlin
private fun display(dfe: DgsDataFetchingEnvironment, code: String): CategoryDisplay {
    val cache = dfe.graphQlContext.computeIfAbsent(CACHE_KEY) {
        ConcurrentHashMap<String, CategoryDisplay>()
    } as ConcurrentHashMap<String, CategoryDisplay>

    return cache.computeIfAbsent(code) {
        spotQueryUseCase.categoryDisplays(setOf(it), dfe.domainLocale())[it]   // ← 락 안에서 DB I/O
            ?: CategoryDisplay.fallback(SpotCategory(it))
    }
}
```

**문제 세 가지**

1. **`computeIfAbsent` 매핑 함수 안에서 DB를 친다.** 이 메서드는 매핑 함수가 끝날 때까지 해당
   버킷을 잠근다. 커넥션 대기가 5초면 락도 5초 유지되고, 같은 카테고리를 쓰는 다른 카드의
   필드 리졸버(가상 스레드)가 전부 그 락에서 멈춘다. Java 문서도 매핑 함수는 짧고 부작용이
   없어야 한다고 명시한다 — I/O는 넣으면 안 되는 자리다.

2. **배치 조회를 만들어 두고 1건씩 호출한다.** 어댑터 `AppCategoryQueryAdapter.findDisplays`는
   `codes: Set<String>`을 받아 `WHERE code IN (...)`으로 한 번에 읽도록 되어 있다. 그런데
   호출부는 항상 `setOf(it)` — 단건이다. 카테고리 5종이면 쿼리가 5번 나간다.

3. **캐시가 요청 범위다.** 대상 데이터는 `category` 9행 + `category_translation` 36행,
   **총 45행의 거의 불변 데이터**인데 매 요청 다시 읽는다.

### `seed()`는 한 곳만 막고 있다

`CategoryDisplayResolver.seed()` 호출부는 **`SpotQueryResolver.availableSpotCategories` 단 한 곳**이다.
`SpotCategory`를 반환하는 GraphQL 필드는 7곳이고, 클라이언트(`ieuri-k-trip-spot`)는 그중
대부분에서 `label`·`iconUrl`을 요청한다.

```
schema/tourism.graphqls:249, 301, 391, 446, 465
schema/map.graphqls:113
schema/nationwide-map.graphqls:202
```

즉 `recommendedSpots`·`searchSpots`·`popularSpots`·`spotDetail`·지도 쿼리 전부 무방비다.

---

## 해법 — DataLoader

**이 저장소에 이미 올바른 예시가 있다.** `NationwideMapDataLoaders.kt`의 주석:

> 이전에는 `CellImageBatchLoader`라는 이름의 요청 범위 메모(`graphQlContext.computeIfAbsent`)였다.

**같은 안티패턴을 한 번 걷어낸 이력이 있고, 이 리졸버만 이전 상태로 남아 있다.**
`community` 쪽에도 DataLoader 기반 배치가 있다(`CommunityQueryPort` 주석: "20번 붙는다(N+1) —
DataLoader가 글 id를 모아 이 메서드를 한 번 부른다").

### 바꿀 형태

```kotlin
@DgsDataLoader(name = CategoryDisplayDataLoader.NAME)
class CategoryDisplayDataLoader(
    private val spotQueryUseCase: SpotQueryUseCase,
) : MappedBatchLoader<CategoryKey, CategoryDisplay> {
    override fun load(keys: Set<CategoryKey>): CompletionStage<Map<CategoryKey, CategoryDisplay>> =
        CompletableFuture.supplyAsync {
            // 언어별로 묶어 findDisplays(codes, locale)를 언어 수만큼만 호출
        }
    companion object { const val NAME = "tourism.categoryDisplay" }
}
```

```kotlin
@DgsData(parentType = "SpotCategory", field = "label")
fun label(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> =
    dfe.loader<CategoryDisplay>(NAME).load(key(dfe)).thenApply { it.label }
```

**DataLoader가 해결하는 것**

- GraphQL 실행 엔진이 같은 틱의 키를 모아 `load(Set)`을 **한 번** 호출 → 요청당 DB 조회 N → 1
- 어댑터의 `findDisplays(codes: Set)` 배치 기능이 비로소 제 역할을 한다
- `computeIfAbsent` 락이 사라진다 → 연쇄 차단 없음
- `CACHE_KEY`·`@Suppress("UNCHECKED_CAST")`·`ConcurrentHashMap`·`seed()` 전부 불필요

> `graphQlContext` 수동 캐시를 고르는 바람에 키 상수·캐스팅·동시성 맵·원자적 채움·seed가
> 줄줄이 따라붙었다. 뿌리를 바꾸면 부속이 함께 없어진다.

⚠️ **키에 언어가 들어가야 한다.** 현재는 리졸버가 `dfe.domainLocale()`을 읽는데, DataLoader는
요청 범위이므로 `(code, locale)` 복합 키로 만들거나 컨텍스트에서 로케일을 꺼내야 한다.
`category_translation`이 언어별 행이므로 이걸 빠뜨리면 다국어가 섞인다.

### 함께 할 것

**HikariCP `maximum-pool-size`를 명시한다.** 현재 `application.yaml`에 값이 없어 기본값 10이
쓰이고 있다(Grafana `active 10`이 그 증거). 의도한 값이 아니라 설정 누락이다.

```
운영 CNPG max_connections = 100 (dev 실측; 운영도 확인할 것)
현재 소비: 앱 메인 2×10 + search 2×10 + 복제 2 + 구독 1 + 오퍼레이터/모니터링 ~8 + superuser 예약 3 ≈ 54
여유: 약 46
```

파드당 20이면 총 40 — 배치 CronJob 여유를 남기고도 들어간다. **다만 DataLoader로 조회 횟수를
먼저 줄이는 것이 순서다.** 쿼리가 커넥션을 오래 잡는 상태에서 풀만 늘리면 DB 동시 실행만 늘어난다.

---

## 검증 방법

수정 후 같은 스크립트로 재측정한다(`~/loadtest/`).

```bash
# 개발
k6 run -e EMAIL=... -e PASSWORD=... -e CF_ID=... -e CF_SECRET=... dev-100rps.js
# 운영 (Cloudflare Skip 규칙 필요, 테스트 후 규칙 삭제할 것)
k6 run -e EMAIL=... -e PASSWORD=... recommended.js
```

**기대치**

| | 현재 | 목표 |
|---|---|---|
| 요청당 카테고리 쿼리 | 카테고리 종류 수만큼 | **1회** |
| 트레이스 `connection` 스팬 | 요청당 여러 개 | 1~2개 |
| 운영 붕괴 지점 | 5 RPS | 최소 수십 RPS |
| 파드 재시작 | 발생 | 없음 |

**트레이스에서 `graphql field label` 스팬들이 서로 다른 시간을 갖는지** 확인한다. 지금은 전부
동일한 값인데, 그것이 락 대기의 징표다.

---

## 결정

브랜치 `fix/category-display-n1`, dev에서 cut. SPEC 번호가 없는 긴급 건이라 planner를 거치지
않았고, 이 문서가 설계 기록을 겸한다.

| id | 결정 | 근거 / 무엇을 버렸는지 |
|---|---|---|
| P-01 | `computeIfAbsent` 메모를 **DataLoader로 교체**한다 | `NationwideMapDataLoaders`가 같은 안티패턴을 이미 한 번 걷어냈고 이 리졸버만 이전 상태로 남아 있었다. 락이 사라지는 동시에 어댑터의 `findDisplays(codes: Set)` 배치 기능이 비로소 쓰인다. **버린 것**: 락만 밖으로 빼는 최소 수정(`조회 → putIfAbsent`). 락 연쇄는 풀리지만 종류 수만큼 쿼리는 그대로라 증상만 지운다. |
| P-02 | 언어는 키가 아니라 **keyContext**로 싣는다 | `category_translation`이 언어별 행이라 빠뜨리면 다국어가 섞인다(POL-039). `PostSpotDataLoader`가 같은 이유로 같은 형태다. 부재 시 **폴백하지 않고 실패**시킨다: 기본 언어로 흘리면 JA 사용자에게 EN 라벨이 나가는 그럴듯한 오답이 된다. **"아무 keyContext나 집어 쓴다"의 근거는 실행(execution) 단위 스코프다** — 레지스트리가 `ExecutionInput`마다 구성되고 한 실행의 `Accept-Language`는 하나다. 리뷰에서 지적된 대로 "요청당 하나"는 더 약한 문장이라(한 HTTP 요청이 여러 실행을 담으면 요청 단위 가정은 참인 채로 이 로더만 틀린다) KDoc을 실행 단위로 고쳤다. |
| P-03 | 로더는 **클래스로 꺼낸다** — `dfe.getDataLoader(CategoryDisplayDataLoader::class.java)` | 처음에는 이름(`NAME` 상수)으로 꺼내고, 미등록 시 실패시키는 확장 함수까지 만들었다. **과도한 설계였고 지적받았다.** DGS의 `getDataLoader(Class)`는 클래스의 `@DgsDataLoader`를 읽어 `DataLoaderNameUtil`로 이름을 푸는데, 등록도 같은 유틸을 쓴다 — 등록과 조회가 같은 출처에서 이름을 얻으므로 어긋날 수가 없다. 그래서 null 체크도, 에러 문구도, `NAME` 상수도, 그것을 감싼 확장 함수도 전부 필요 없다. 이름 기반을 고른 탓에 부속이 줄줄이 따라붙었던 것이고, 뿌리를 바꾸니 함께 없어졌다(P-01과 같은 패턴). |
| P-04 | `seed()`의 수동 메모는 걷어내되, **`DataLoader.prime()`으로 대체**한다 | 처음에는 seed를 통째로 지웠다. **그 판단은 틀렸고 리뷰에서 잡혔다** — 근거로 삼은 "DataLoader가 칩 8개를 1회로 접는다"가 비교 대상을 잘못 잡았다. seed가 있던 상태의 비용은 8이 아니라 **0**이었다. graphql-java는 명시적 페처가 등록돼 있으면 소스 프로퍼티로 폴백하지 않으므로, `availableSpotCategories`가 `findCategories`로 이미 읽어 둔 라벨·아이콘을 필드 리졸버가 **버리고** 다시 조회한다. 즉 삭제는 이 쿼리를 0 → 1로 **악화**시켰다. `prime()`은 seed가 하던 일의 라이브러리 기본형이라, 수동 메모(`CACHE_KEY`·`UNCHECKED_CAST`·`ConcurrentHashMap`)는 그대로 사라지면서 0회가 유지된다. 부수 효과로 **앱 범위 값이 권위를 갖는다** — `findCategories`는 `app_category`로 거르지만 로더의 `findDisplays`는 전역이다. 실측(2026-09-21) `app_category`에는 라벨·아이콘 컬럼이 없어 **지금은 두 조회가 갈릴 수 없다**. 앱별 override가 생기면 열릴 자리를 비용 0으로 미리 닫아 두는 것이고, 현재의 결함을 고친 것은 아니다. |
| P-05 | 전역 캐시(45행 준불변)는 **이번에 하지 않는다** | classpath에 Spring Cache가 없어 인프라를 새로 들이는 결정이고, 무효화 시점(카테고리 추가·번역 수정)을 설계해야 한다. 요청당 1회까지 내려간 뒤에도 부족한지를 먼저 재측정으로 확인한다 — 관측 없이 기계를 만들지 않는다. |
| ~~P-07~~ | ~~공유 커널 `DataLoaderSupport`의 주석을 고친다~~ **철회** | P-03이 이름 기반을 버리면서 `DataLoaderSupport`를 언급할 일 자체가 없어졌다. 그 파일은 이 PR에서 **손대지 않는다**. (원래 근거는 주석의 *"모든 로더가 `Long` 키"*가 거짓이 된다는 것이었는데, 그 문장은 `loader()` 헬퍼를 쓰는 로더들 얘기로 읽히고 이 로더는 그 헬퍼를 쓰지 않는다 — 고칠 이유가 사라졌다.) |
| P-06 | 풀 상한 20은 **DataLoader 수정 다음**이다 | 조회가 커넥션을 오래 잡는 상태에서 풀만 늘리면 DB 동시 실행만 늘어난다. 같은 PR이되 커밋을 갈라 순서를 남긴다. dev 프로파일에는 두지 않는다 — 그 파일은 환경 차이가 있는 값만 갖기로 되어 있고(헤더 주석) 공통값과 같다. |

| P-08 | 배치 프로파일의 풀을 **8로 낮춘다** | P-06의 예산 주석이 배치를 "남는 몫"으로만 적었는데, `application-batch.yaml`에 `hikari` 블록이 없어 실제로는 공통값 **20을 상속**하고 있었다. CronJob은 API 파드가 떠 있는 채로 뜨므로 더해지는 몫이고, 그대로 두면 100 중 여유가 6까지 줄었다. 8인 근거는 가장 넓게 쓰는 상세 수집 잡이 `SpotDetailPart` 4갈래를 병렬로 돌고(`SpotDetailPrefetchJobConfig`) 거기에 잡 리포지터리 몫이 붙는 것이다. 예산 주석도 항목별로 다시 적었다(82/100). |

### 이번 범위 아님

전역 캐시(P-05) · `search-datasource` 풀(실측상 `search-pool active 0`으로 무관) ·
k6 재측정과 Cloudflare Skip 규칙(사용자 실행 영역) · `pg_stat_statements` 활성화(인프라 저장소).

**`PostSpotDataLoader`의 KDoc도 "언어는 요청당 하나"로 적혀 있다.** P-02가 너무 약하다고 판정한
바로 그 문장이고, 실행 단위로 고쳐야 맞다. 이 슬라이스의 진단 대상이 아니라 건드리지 않았다 —
별건으로 정리한다.

**풀 상한이 `max_connections`를 넘지 않는지는 어디서도 검증되지 않는다.** prod 값을 단독으로
올려도 기동은 성공하고, 커넥션이 거절될 때에야 드러난다. 주석이 유일한 방어선이라는 것은
받아들인 비용이다.

---

## 참고

- 부하 시 **에러율이 0%**였다(운영). 전부 대기였고 실패가 아니었다 — 에러율만 보는 모니터링은
  이 상황을 놓친다. 응답시간 분포(p95/p99)를 함께 봐야 한다.
- liveness가 DB를 보지 않도록 해 두었는데도 **커넥션 대기로 스레드가 고갈되어 liveness 자체가
  타임아웃**났다. DB 장애가 아닌데 재시작이 일어나는 경로다.
- 운영은 `pg_stat_statements`가 없어 부하 중 실제 쿼리 통계를 사후에 볼 수 없었다. 활성화를
  검토할 것.
