> spec: SPEC-0035 · source: `Specs/SPEC-0035/spec.md` (Obsidian) · status: approved

# SPEC-0035 — 서버 설계: 내 주변 추천 피드

요구사항·수용 기준·계약은 스펙 문서에 있다. 이 문서는 이 저장소가 그것을 **어떻게** 실현하는지만 적는다. 요구사항은 번호로 인용한다.

## 이 변경의 성격

가산 변경이다. 새 루트 필드 `Query.location`과 맥락 타입 `Location`, 두 보기(`spots`·`recommendedSpots`), 공용 `SpotConnection`을 더한다. 기존 `Query.spots`는 리졸버·서비스·SQL이 그대로이고 스키마에 `@deprecated`만 붙는다(R1, AC-1.3). 배포된 앱은 영향이 없다.

## 반드시 먼저 읽을 것 — 이 스펙이 버린 길

| 하지 않는 것 | 이유 |
|---|---|
| `spots`의 정렬을 서버가 바꾸는 것 | POL-055 — 순서는 클라이언트가 고른다. 그래서 보기가 필드로 나뉜다 |
| MMR·DPP 결과를 offset으로 자르는 것 | 상위 k개 도구에는 페이지가 없다. 자르려면 풀·캐시·재계산이 숨은 로직이 된다 |
| Valkey 묶음 캐시 | 처음엔 복사본 캐시를 두었으나 **캐시를 없앴다**(2026-09-12 사용자 결정). 이어 받기는 카드 약 90장마다 한 번이라 재계산(조회 4번 + DPP 수 ms)이 캐시 키·직렬화·예외 흡수보다 싸다. 커서만으로 재계산한다 |
| 종류(군집) 라벨로 하드 스캐터링 | 장소 간 유사도를 쓰지 않아 항목 단위 개인화로 못 간다(사용자 결정) |
| 루트 필드 둘 / 모드 인자 | 인자를 공유하는 변형은 한 맥락 객체의 두 보기다(POL-new-D) |

## 도메인 배치

전부 `tourism`이다. JPA 엔티티는 도메인 모델이 아니다(기존 규칙).

| 단위 | 계층 | 역할 | 인용 |
|---|---|---|---|
| `Location` | — | 별도 VO를 두지 않는다 — 검증은 기존 `Coordinates`가 하고, 리졸버 소스는 read DTO `LocationView(latitude, longitude)`다(S1 결정) | R1 |
| `ScoreFactors` (VO) | domain | 요인별 `Double?` — null은 "없음"이지 0이 아니다 | R6 |
| `RecommendationScore` (도메인 서비스, 순수) | domain | 가중합·분모 축소·면세 배율 | R6~R12 |
| `TaxRefundTitleRule` (도메인 함수) | domain | 제목 패턴 판정. `SpotExclusionRule`과 같은 모양(설정 패턴 목록) | R11 |
| `AffinityCentroid` (VO) | domain | LIKE 벡터 평균, 코사인 | R10 |
| `SpotSimilarity` (도메인 함수) | domain | 벡터 거리, 없으면 카테고리 대체 거리 | R14 |
| `DppReranker` (도메인 서비스, 순수) | domain | 커널 L 구성, 창 k 그리디, 결정적 동점 규칙 | R13, R15, R16 |
| `RecommendedSpotsCursor` (VO) | domain | 위치·발급시각. 인코딩·만료 판정. **서명도 조건 지문도 없음**(2026-09-12 결정 — 아래 "커서 형식") | R4 |
| `DistanceCursor` (기존 VO 재사용) | domain | `Location.spots`의 (거리, id) 키셋. `SearchCursors.kt`의 `d` 접두 커서를 그대로 쓴다 | R3 |
| `SpotEmbedding` (VO) | domain | `spotId`, `model`, `dimension`, `textHash`, `vector` | R18~R20 |
| `EmbeddingTarget` (도메인 함수) | domain | 대상 판정 한 규칙 — 미보유·텍스트 변경·모델 불일치 | R18 |
| `DppParameterGrid` (도메인 서비스, 순수) | domain | 사례 집합에 대한 로그 가능도, 격자 최대 | R22 |
| `LocationQueryService` | application | `location` 진입 검증(DB 없음) + `Location.spots`(기존 `SpotQueryService.spots`의 후보 경로 + 키셋 커서). 둘을 한 서비스에 둔다 — 검증 한 줄짜리 서비스를 따로 두지 않는다(S1 결정) | R1, R2, R3, R5 |
| `RecommendedSpotsQueryService` | application | 첫 요청·이어 받기. 요청마다 후보 N건을 점수·재배열하고 구간을 자른다 | R4~R16 |
| `SpotEmbeddingSyncService` | application | 임베딩 단계의 명령. 대상 선정 → 제공자 호출 → 저장 | R18~R20 |
| `DppParameterLearningService` | application | 로그 → 사례 → 격자 → 저장/미적용 | R22~R24 |

## 조회 경로

### GraphQL 어댑터

- `LocationQueryResolver` — `@DgsQuery location(latitude, longitude)`는 유스케이스가 돌려준 `LocationView` 경량 read DTO만 넘긴다. DB를 건드리지 않는다(AC-1.1). **위치 조회 감사 이벤트(`LocationAccessed`)는 리졸버가 아니라 `LocationQueryService.location`이 발행한다** — 제어는 서비스에 둔다(2026-09-12 결정). 기존 리졸버 6곳의 발행은 별도 정리 대상이다.
- `@DgsData(parentType = "Location", field = "spots")`·`"recommendedSpots"`는 `dfe.getSource<GqlLocation>()`로 좌표를 받는다. `CategoryDisplayResolver`가 하는 방식 그대로이고, `error(...)`로 계약 위반을 잡는다. 가상 스레드에서 두 형제 필드가 동시에 풀릴 수 있으므로 공유 가변 상태를 두지 않는다.
- `PageInfo`는 `common/pagination/CursorPage.kt`의 `cursorPageOf`/`keysetPageOf`로 만든다. `first + 1` 초과 조회로 `hasNextPage`를 판정하는 기존 규약을 따른다.

### `Location.spots` (R2, R3, R5)

`SpotQueryService.spots`의 후보 경로를 그대로 쓰되 페이지만 키셋이다.

- `SpotSpatialQueryPort`에 키셋 조건을 받는 메서드를 하나 더한다: `searchNearbyAfter(search, afterDistance, afterId, limit)`. 어댑터 SQL은 기존 `ST_DWithin` + `ORDER BY ST_Distance, id`에 `AND (dist > ? OR (dist = ? AND id > ?))`를 더한다. `ST_DWithin`을 유지해야 GiST 인덱스를 탄다(기존 주석).
- 커서는 `DistanceCursor`(기존). 손상은 도메인 `IllegalArgumentException` → `InvalidCursorException`(`INVALID_CURSOR`) 승격 — 기존 경로.
- 반응 제외는 `findReactedSpotIds(memberId, language)` 그대로(AC-3.2).

### `Location.recommendedSpots` — 첫 요청 (R4~R16)

| 단계 | 포트 | 비고 |
|---|---|---|
| 1 후보 | `SpotSpatialQueryPort.searchNearby(limit = N, offset = 0, excludedSpotIds = 반응)` | 기존 SQL. 거리 가까운 순 N건 |
| 2 집계 | `SpotQueryPort.countReactionsIn(ids, language): Map<Long, ReactionCounts>` **신설(벌크)** — 기존 `countLikes` 옆에 둔다(같은 테이블·같은 읽기 포트라 포트를 새로 내지 않는다, S1 결정) | R8. 어댑터는 요청한 id 전부에 값을 채운다(반응 없음 = 0). map에 없는 id = 요인 없음은 서비스 규칙이다 |
| 3 활동 | `CommunityActivityCountPort.countByCatalogIds(ids): Map<Long, ActivityCounts(posts, questions, answers)>` **신설(벌크)** — 어댑터가 community의 `CommunityQueryUseCase.postCountsByCatalogIds`·`SpotQaQueryUseCase.qaCountsByCatalogIds`(둘 다 신설)를 소비한다 | R9. 방향은 tourism → community 읽기 전용(`out/client` → `port/in`). 숨김·삭제는 빼고 답글은 세지 않는다 |
| 4 벡터 | `SpotEmbeddingPort.findByCatalogIds(ids, model, dimension): Map<Long, FloatArray>` | S2. R10, R14, R20. 모델·차원 불일치는 어댑터가 걸러 없는 것으로 |
| 5 취향 | `SpotReactionPort.findLikedCatalogIds(memberId, language): Set<Long>` **신설(LIKE만)** + 4의 벡터로 `AffinityCentroid` | S2. R10. 비로그인·최소량 미만은 건너뜀 |
| 6 유행 | `ExternalTrendScorePort.findByCatalogIds(ids): Map<Long, Double>` — 어댑터는 **NoOp**(빈 map) | R12. 출처가 정해지면 어댑터만 추가 |
| 7 점수 | `RecommendationScore` | 순수 |
| 8 재배열 | `DppReranker(alpha, sigma, k)` — 학습값 있으면 그것(`LearnedDppParametersPort.latest()`, S3), 없으면 설정 | R13, R24. 후보 N건 전부를 창 k씩 배치한 순열 하나 |
| 9 로그 | `FeedBatchLogPort.append(memberId, batchId, windows)` — 로그인만. 예외는 삼킴 | S3. R21 |
| 10 응답 | 순열의 `[position, position + first)` 카드 → `SpotEdge` + `RecommendedSpotsCursor(position + i + 1, issuedAt)`. `first + 1`건을 잘라 `hasNextPage`를 판정한다 | |

집계·유행 포트의 실패는 오류가 아니라 요인 없음이다(서비스가 삼키고 기록).

### 이어 받기

1. `RecommendedSpotsCursor.decode(after)` — 형식 실패 → `InvalidCursorException`, 수명 초과 → `CursorExpiredException("CURSOR_EXPIRED", BAD_REQUEST)` **신설**.
2. 첫 요청과 같은 1~8을 다시 수행해 같은 순열을 만들고 커서 위치부터 자른다. **묶음도 캐시도 없다** — 재계산이 같은 결과를 내려면 아래 "결정성"을 지켜야 한다.
3. 조건(좌표·반경·카테고리·언어)이 바뀌었는지는 서버가 보지 않는다. 클라이언트가 조건을 바꾸면 커서를 버리고 새로 시작한다(R29). 옛 커서를 새 조건에 넘기면 새 순열의 그 위치부터 받을 뿐 오류가 아니다.

### 결정성 (AC-13.3, AC-4.1)

- 후보는 (거리, id) 순으로 고정 순서로 받는다.
- 점수 계산은 `Double`이되 동점은 id 오름차순으로 가른다. DPP 그리디의 인자 최대 선택도 같다.
- 커널 계산은 같은 순서로 같은 부동소수 연산을 하므로 같은 입력에 같은 결과다. 다만 **이어 받기 사이에 반응이 기록되면** 후보가 바뀌어 순열이 어긋날 수 있다 — 한두 장의 중복·누락이며 스펙 §5가 명시한다. 클라이언트가 id 겹침을 걷어낸다(R28).

### 커서 형식 (R4)

`r1|{position}|{issuedAtEpochSec}` → Base64 URL-safe 무패딩. 기존 커서(`SearchCursors.kt`)와 같은 모양이다.

초안은 HMAC 서명과 조건 해시(좌표·반경·카테고리·언어·호출자의 SHA-256)를 함께 담았다. **둘 다 뺐다**(2026-09-12 사용자 결정, Simple is best). 서명은 서버가 커서 값을 신뢰하지 않아 지키는 자산이 없었고 환경마다 secret 하나를 요구했다. 조건 해시는 조건 불일치 검출(옛 AC-4.2)과 캐시 키에 쓰였는데, 조건이 바뀌면 클라이언트가 어차피 새로 시작하고(R29) 캐시는 없앴다. 발급 시각은 이어 받아도 갱신하지 않는다 — 수명은 세션 전체에 걸린다.

### 캐시 (없음)

없다. 2026-09-12 결정으로 Valkey 묶음 캐시(`RecommendedSpotsBatchCachePort`·`ValkeyRecommendedSpotsBatchCache`·`CachedSpotCard`)와 묶음 크기 B를 지웠다. 요청마다 후보 N건을 다시 계산한다.

## DPP 구현 노트 (R13)

- 커널: `L_ii = q_i²`, `L_ij = α·q_i·q_j·exp(−D_ij / 2σ²)`. `D`는 벡터 코사인 거리, 없으면 `SpotSimilarity`의 카테고리 대체 거리.
- 그리디: 창 k개를 고를 때마다 "추가 시 행렬식이 최대"인 후보. 나이브하게 매번 행렬식을 계산하면 k·N·k³이라 N=300, k=12에서 문제없지만, 증분 Cholesky(Chen et al. 2018의 fast greedy MAP)로 N·k²까지 줄일 수 있다. 구현자가 고르되 **결과가 같은지 테스트로 고정**한다.
- 창 k를 채우면 선택된 것을 후보에서 빼고 남은 것으로 다음 창. 순서는 창 순 → 창 안 선택 순.
- 점수가 없는 후보(AC-6.2)는 커널에 넣지 않고 순열 맨 뒤에 id 순으로 붙인다.

## 임베딩 (R18~R20)

| 항목 | 설계 |
|---|---|
| 포트 | `TextEmbeddingPort.embed(texts: List<String>): List<FloatArray>` |
| 어댑터 | `VoyageEmbeddingClient` — `infrastructure/adapter/out/client/voyage/`, `RestClient`, `VoyageProperties`(prefix `ieuri.voyage`: `api-key: ${IEURI_VOYAGE_API_KEY:}`, `base-url`, `model`, `batch-size`, timeouts). 키는 환경변수로만, 로그·응답에 노출 금지(TourAPI 키와 같은 규칙) |
| 텍스트 | `"{title} | {categoryLabel} | {address}"` — 행의 언어. 제목은 저장된 그대로(면세 태그 포함 — 태그 매장끼리 닮게 나오는 것이 의도) |
| 저장 | `spot_embedding(spot_id PK, model, dimension, text_hash, embedding vector(1024), updated_at)`. 위치는 **T3** — 아래 |
| 대상 | `EmbeddingTarget`: 행이 없거나, `text_hash`가 다르거나, `model`/`dimension`이 설정과 다르면 대상. 초기 적재·모델 교체·증분이 이 한 규칙이다 |
| 단계 | `SpotSyncJobConfig`의 카탈로그 분리 단계 **뒤에** `spotEmbeddingStep`을 붙인다. 언어별 행 전부가 대상 판정을 거친다. 실패는 단계를 실패시키지 않고 카운트만 남긴다(AC-19.1). 재시도는 `embedding.retry-count`회, 시간 기반 대기 없음(POL-040) |
| 요청 크기 | Voyage 요청당 입력 상한·분당 한도는 **T5**. `batch-size`는 그 안에서 설정 |

### T3 — 벡터 저장 위치

| 선택 | 사실 | 결과 |
|---|---|---|
| 검색 노드 | `vector 0.8.5`가 pg_search 의존성으로 이미 설치. `tour.spot`이 좌표(`location geography`)까지 복제돼 있음 | 확장 요청 없음. 다만 반응·글·QA 집계는 메인 DB라 조회당 DB 2회는 어차피 발생 |
| 메인 DB | 확장 미설치 → 매니페스트로 활성화(POL-034·036, HA 양립성 확인) | 조회당 DB 1회 가능. 매니페스트 변경 필요 |

권장은 **검색 노드**다 — 이미 있는 것을 쓰고, DB 2회는 집계 포트 때문에 어느 쪽이든 생긴다. 어댑터는 `@Qualifier("searchJdbcTemplate")`로 검색 노드를 잡는다(`SpotSearchJdbcAdapter`와 같다). 마이그레이션은 `db/search-node/`에 둔다. 서버 하네스가 실측 후 바꿔도 된다.

## 학습 (R21~R24)

| 항목 | 설계 |
|---|---|
| 로그 | `feed_batch_log(id, member_id, batch_id, window_no, catalog_ids bigint[], scores double precision[], created_at)` — `scores[i]`는 `catalog_ids[i]`의 서빙 시점 점수 q. 좌표·조건 없음. 비로그인은 안 쓴다. 보관은 `AuditRetentionJobConfig`와 같은 모양의 정리 배치, 기간은 설정(T7) — POL-014 표에 행 추가 |
| 사례 | 창 하나 = 사례 하나. 정답 = 그 창의 카드 중 회원이 LIKE한 집합(반응 테이블 조인). LIKE가 없는 창은 제외(AC-22.2) |
| 목적함수 | L-앙상블 로그 가능도: `Σ_창 [ log det(L_Y) − log det(L + I) ]`, `Y` = LIKE 집합. 커널은 서빙과 같은 `DppReranker`의 커널 함수를 쓰고, q는 **로그에 저장된 서빙 시점 점수**를 쓴다(R21·AC-22.4). 학습 시 재계산하지 않는다 — 그 사이 바뀐 집계가 섞이면 서빙과 다른 커널을 학습한다. 벡터 거리 D는 학습 시점의 벡터로 계산한다(벡터는 모델이 같으면 바뀌지 않는다) |
| 탐색 | 설정된 격자(α × σ) 전수. 최대 조합을 `dpp_parameter_version(version, alpha, sigma, case_count, created_at)`에 저장 |
| 안전장치 | 사례 수 < `learning.min-cases`면 저장하지 않고 이유를 로그(AC-23.1) |
| 배치 | `DppParameterLearningJobConfig` — 기존 배치와 같이 `@Profile(BATCH_PROFILE)`, 실행 시점은 k8s CronJob(POL-056, 매니페스트에 CronJob 추가) |
| 서빙 | `LearnedDppParametersPort.latest()` → 있으면 그 값, 없으면 `RecommendationProperties.dpp.alpha/sigma`(AC-24.x). 조회마다 DB를 치지 않도록 짧게 메모이즈해도 되나 **없어도 동작해야** 한다 |

## 설정

`RecommendationProperties`, prefix `ieuri.tourism.recommendation`. 기존 properties는 평평한 data class 하나지만 이 기능은 키가 많아 중첩 data class(`score`, `dpp`, `cursor`; S2·S3에서 `embedding`, `learning`, `batchLog`)를 쓴다 — 금지하는 선례는 없다. 키·출발값은 스펙 §9 표가 원본이며 여기 복사하지 않는다. T7에 걸린 값(커서 수명·학습 최소 사례·로그 보관)은 스펙 §9의 출발값(24h·200·90일)을 코드 기본값으로 두고 KDoc에 출발점임을 적는다(POL-044). 여기서 다른 숫자를 고르지 않는다.

## 검증과 오류

| 상황 | 예외 | 코드 | 통로 |
|---|---|---|---|
| 좌표 범위 | 기존 `spots`와 같은 예외 | 기존 | 최상위 `errors[]` |
| 반경 상한 | `RadiusTooLargeException` | `RADIUS_TOO_LARGE` | 〃 |
| 빈 카테고리 | `EmptyCategorySetException` | `EMPTY_CATEGORY_SET` | 〃 (`SecurityGraphqlExceptionHandler`) |
| 커서 손상 | `InvalidCursorException` | `INVALID_CURSOR` | 〃 |
| 커서 수명 초과 | `CursorExpiredException` **신설** | `CURSOR_EXPIRED` | 〃 (`TourismQueryExceptionHandler`에 추가) |

`first`는 `coerceIn(1, 100)`으로 절삭한다(POL-018). 로그·유행·집계 포트의 실패는 오류가 아니다 — 요인 없음 또는 no-op이다.

좌표 검증은 도메인 `Coordinates`의 불변식이고, 반경 상한은 정책값 비교다. 그 실패를 오류 코드로 옮기는 두 함수(`coordinatesOf`·`requireRadiusWithin`)는 `application/validation/`에 둔다 — 서비스 패키지에 두지 않는다(2026-09-12 결정).

## 테스트가 붙는 단위

애플리케이션·도메인 단위 테스트만이다(MockK + kotlin-test, 기존 `SpotQueryServiceTest`와 같은 모양).

| 테스트 | 판정하는 것 |
|---|---|
| `RecommendationScoreTest` | AC-6.x, 7.x, 8.x, 9.x, 10.1, 11.x, 12.2 |
| `TaxRefundTitleRuleTest` | AC-11.1, 11.2 |
| `SpotSimilarityTest` | AC-14.x, 20.x |
| `DppRerankerTest` | AC-13.x, 15.x, 16.1. 구현자가 증분 Cholesky 등 빠른 그리디를 택하면 나이브 행렬식 그리디와 같은 순열을 내는지 이 테스트에 고정한다 |
| `RecommendedSpotsCursorTest` | AC-4.3·4.4 인코딩·만료 |
| `RecommendedSpotsQueryServiceTest` | AC-4.1, 4.3, 4.4, 5.x, 8.3, 9.3, 12.1, 12.2, 15.x, 16.x — 포트 전부 스텁 |
| `LocationQueryServiceTest` | AC-1.1, 1.2, 2.x, 3.x |
| `EmbeddingTargetTest` · `SpotEmbeddingSyncServiceTest` | AC-18.x, 19.1, 19.2 |
| `DppParameterGridTest` · `DppParameterLearningServiceTest` | AC-22.x, 23.x |
| `RecommendationPropertiesTest` | AC-25.x |

테스트하지 않는 것(인프라): Voyage HTTP 어댑터, 키셋 SQL, pgvector 저장, DGS 리졸버, 배치 런타임. AC-1.3(`spots` 불변)은 기존 테스트가 그대로 통과하는 것으로 판정한다.

## 이 저장소의 슬라이스가 열어주는 것

| 슬라이스 | 머지되면 호출 가능한 것 | 아직 없는 것 |
|---|---|---|
| S1 | `location.spots`, `location.recommendedSpots` 전부. 점수는 거리·인기·활동·면세, 유사도는 카테고리 대체. 커서·오류 코드 완성 | 벡터·취향·학습. 클라이언트는 **여기서 전부 착수** |
| S2 | 같은 계약. 유사도가 벡터로, 취향 요인 추가 | 학습 |
| S3 | 같은 계약. α·σ가 로그로 갱신 | — |

## 이 기능이 동작하려면 먼저 있어야 하는 것

| 데이터·설정 | 만드는 주체 | 없으면 |
|---|---|---|
| `IEURI_VOYAGE_API_KEY` | 사용자 발급 → 매니페스트 secret | S2 임베딩 단계가 전부 실패로 카운트. 목록은 정상 |
| `vector` 확장 (선택한 DB) | 검색 노드면 이미 있음. 메인이면 매니페스트 | S2 마이그레이션 실패 |
| 학습 배치 CronJob | 매니페스트 | S3 미실행. 서빙은 기본값 |
| 벌크 집계 진입점(반응 1·커뮤니티 2) | 이 저장소(S1) | 인기·활동 요인 없음 → 거리·면세만으로 점수 |
| POL-014 보관 기간 행 | 플래너(implemented 시) | — |

## 관련

- SPEC-0004(`spots`·PostGIS), SPEC-0013(거리 감쇠 5km, 검색 노드), SPEC-0018(면세 태그 실측, `SpotExclusionRule`), SPEC-0019(카탈로그), SPEC-0020(카테고리 다중 선택), SPEC-0029(언어별 행), SPEC-0015(다요인 점수 선례)
- 문헌: Wilhelm 외, *Practical Diversified Recommendations on YouTube with DPPs* (CIKM 2018) — 커널 식·창 방식·그리디; Chen 외 2018 — fast greedy MAP

## 변경 이력

| 날짜 | 변경 | 계기 |
|---|---|---|
| 2026-09-11 | 초안 | ieuri-server #131 |
| 2026-09-12 | 사용자 결정 추가 3건. (4) **묶음 캐시 제거** — 조건 지문(SHA-256)이 캐시 키 때문에 남아 있었고, 캐시 없이 재계산해도 비용이 작다(조회 4번 + DPP 수 ms, 카드 약 90장마다 1회). 커서는 `r1|위치|발급시각`뿐이고 묶음 B·캐시 TTL·AC-4.2·AC-16.2·AC-17.x가 사라진다. 스펙 R4·R15·R16·R17·§2·§9 함께 수정. (5) 검증 헬퍼를 `service/`에서 `application/validation/`으로 이동. (6) 벡터 DB의 역할은 S2 설계 시 다시 판단 — S1은 벡터를 쓰지 않는다 | 사용자 리뷰(2026-09-12) |
| 2026-09-12 | 사용자 결정 3건 반영. (1) **커서 서명 제거** — 서버가 커서 값을 신뢰하지 않아 서명이 지키는 자산이 없고, `IEURI_FEED_CURSOR_SECRET` 기동 조건만 늘렸다. 스펙 R4·AC-4.3·§2·§7·POL-045 행을 함께 고쳤다. 플래너가 같은 모양을 다시 내지 않도록 이슈에 남긴다. (2) **감사 이벤트 발행을 리졸버에서 서비스로** — 리졸버는 유스케이스 호출과 응답 변환만 한다. 기존 6곳(SPEC-0032 선례)은 후속 정리 PR. (3) **`Feed` 명명 제거** — 스키마 필드가 `recommendedSpots`이므로 코드도 `RecommendedSpots*`로 맞춘다 | 사용자 리뷰(2026-09-12) |
| 2026-09-11 | S1 구현 반영. (1) 캐시 값은 id 목록이 아니라 카드 목록 — 히트에서 hydrate 왕복을 없애기 위해. (2) 조건 해시·캐시 키에 호출자 포함 — 반응 제외로 후보가 회원마다 달라서. (3) `SpotReactionCountPort`·`SpotQaCountPort` 신설 대신 `SpotQueryPort.countReactionsIn`·`CommunityActivityCountPort` 하나. (4) `Location` VO 없음(`Coordinates` + `LocationView`), `NearbySpotsQueryService`는 `LocationQueryService`에 합침. (5) 캐시 예외는 어댑터가 아니라 서비스가 삼킨다(AC-17.3 단위 테스트). (6) T6 실측: 면세 태그는 EN `[Tax Refund Shop]` 10,489 · JA `[事後免税店]` 10,490 · ZH `[事後免稅店]` 10,349 · KO `[면세점(TAX REFUND SHOP)]` 28행 — 패턴 4개를 설정에 둔다. (7) `score.affinity-min-likes`는 S1에서 읽는 곳이 없어 S2가 도입한다 | ieuri-server 구현(feature/SPEC-0035-recommended-feed), 리뷰어·simplify-review 반영 |
