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
| Valkey를 기능 부품으로 쓰는 것 | **캐시가 없어도 같은 결과가 나와야 한다**(R17, POL-new-C). 커서만으로 재계산 가능해야 한다 |
| 종류(군집) 라벨로 하드 스캐터링 | 장소 간 유사도를 쓰지 않아 항목 단위 개인화로 못 간다(사용자 결정) |
| 루트 필드 둘 / 모드 인자 | 인자를 공유하는 변형은 한 맥락 객체의 두 보기다(POL-new-D) |

## 도메인 배치

전부 `tourism`이다. JPA 엔티티는 도메인 모델이 아니다(기존 규칙).

| 단위 | 계층 | 역할 | 인용 |
|---|---|---|---|
| `Location` (VO) | domain | 검증된 좌표. `validatedCenter`와 같은 규칙 | R1 |
| `ScoreFactors` (VO) | domain | 요인별 `Double?` — null은 "없음"이지 0이 아니다 | R6 |
| `RecommendationScore` (도메인 서비스, 순수) | domain | 가중합·분모 축소·면세 배율 | R6~R12 |
| `TaxRefundTitleRule` (도메인 함수) | domain | 제목 패턴 판정. `SpotExclusionRule`과 같은 모양(설정 패턴 목록) | R11 |
| `AffinityCentroid` (VO) | domain | LIKE 벡터 평균, 코사인 | R10 |
| `SpotSimilarity` (도메인 함수) | domain | 벡터 거리, 없으면 카테고리 대체 거리 | R14 |
| `DppReranker` (도메인 서비스, 순수) | domain | 커널 L 구성, 창 k 그리디, 결정적 동점 규칙 | R13, R15, R16 |
| `FeedCursor` (VO) | domain | 조건 해시·위치·발급시각·서명. 인코딩·검증·만료 판정 | R4 |
| `DistanceCursor` (기존 VO 재사용) | domain | `Location.spots`의 (거리, id) 키셋. `SearchCursors.kt`의 `d` 접두 커서를 그대로 쓴다 | R3 |
| `SpotEmbedding` (VO) | domain | `spotId`, `model`, `dimension`, `textHash`, `vector` | R18~R20 |
| `EmbeddingTarget` (도메인 함수) | domain | 대상 판정 한 규칙 — 미보유·텍스트 변경·모델 불일치 | R18 |
| `DppParameterGrid` (도메인 서비스, 순수) | domain | 사례 집합에 대한 로그 가능도, 격자 최대 | R22 |
| `LocationQueryService` | application | `location` 진입 검증만. DB 없음 | R1 |
| `NearbySpotsQueryService` | application | `Location.spots` — 기존 `SpotQueryService.spots`의 후보 경로 + 키셋 커서 | R2, R3, R5 |
| `RecommendedFeedQueryService` | application | 첫 요청·이어 받기·묶음 생성·캐시·로그 | R4~R17, R21, R24 |
| `SpotEmbeddingSyncService` | application | 임베딩 단계의 명령. 대상 선정 → 제공자 호출 → 저장 | R18~R20 |
| `DppParameterLearningService` | application | 로그 → 사례 → 격자 → 저장/미적용 | R22~R24 |

## 조회 경로

### GraphQL 어댑터

- `LocationQueryResolver` — `@DgsQuery location(latitude, longitude)`는 `GqlLocation(lat, lng)` 경량 객체만 돌려준다. DB를 건드리지 않는다(AC-1.1).
- `@DgsData(parentType = "Location", field = "spots")`·`"recommendedSpots"`는 `dfe.getSource<GqlLocation>()`로 좌표를 받는다. `CategoryDisplayResolver`가 하는 방식 그대로이고, `error(...)`로 계약 위반을 잡는다. 가상 스레드에서 두 형제 필드가 동시에 풀릴 수 있으므로 공유 가변 상태를 두지 않는다.
- `PageInfo`는 `common/pagination/CursorPage.kt`의 `cursorPageOf`/`keysetPageOf`로 만든다. `first + 1` 초과 조회로 `hasNextPage`를 판정하는 기존 규약을 따른다.

### `Location.spots` (R2, R3, R5)

`SpotQueryService.spots`의 후보 경로를 그대로 쓰되 페이지만 키셋이다.

- `SpotSpatialQueryPort`에 키셋 조건을 받는 메서드를 하나 더한다: `searchNearbyAfter(search, afterDistance, afterId, limit)`. 어댑터 SQL은 기존 `ST_DWithin` + `ORDER BY ST_Distance, id`에 `AND (dist > ? OR (dist = ? AND id > ?))`를 더한다. `ST_DWithin`을 유지해야 GiST 인덱스를 탄다(기존 주석).
- 커서는 `DistanceCursor`(기존). 손상은 도메인 `IllegalArgumentException` → `InvalidCursorException`(`INVALID_CURSOR`) 승격 — 기존 경로.
- 반응 제외는 `findReactedSpotIds(memberId, language)` 그대로(AC-3.2).

### `Location.recommendedSpots` — 첫 요청 (R4~R17)

| 단계 | 포트 | 비고 |
|---|---|---|
| 1 후보 | `SpotSpatialQueryPort.searchNearby(limit = N, offset = 0, excludedSpotIds = 반응)` | 기존 SQL. 거리 가까운 순 N건 |
| 2 집계 | `SpotReactionCountPort.countByCatalogIds(ids, language): Map<Long, LikeDislikeCount>` **신설(벌크)** — 기존 `countLikes`는 단건·LIKE만 | R8. 없는 id는 map에 없음 = 요인 없음 |
| 3 활동 | `CommunityActivityCountPort.countPostsByCatalogIds(ids)`, `SpotQaCountPort.countByCatalogIds(ids)` **신설(벌크)** — 현재 카운트 포트가 없음 | R9. 커뮤니티 도메인의 포트를 tourism이 소비한다 — 방향은 tourism → community 읽기 전용 |
| 4 벡터 | `SpotEmbeddingPort.findByCatalogIds(ids, model, dimension): Map<Long, FloatArray>` | R10, R14, R20. 모델·차원 불일치는 어댑터가 걸러 없는 것으로 |
| 5 취향 | `SpotReactionPort.findLikedCatalogIds(memberId, language): Set<Long>` **신설(LIKE만)** + 4의 벡터로 `AffinityCentroid` | R10. 비로그인·최소량 미만은 건너뜀 |
| 6 유행 | `ExternalTrendScorePort.findByCatalogIds(ids): Map<Long, Double>` — 어댑터는 **NoOp**(빈 map) | R12. 출처가 정해지면 어댑터만 추가 |
| 7 점수 | `RecommendationScore` | 순수 |
| 8 재배열 | `DppReranker(alpha, sigma, k)` — 학습값 있으면 그것(`LearnedDppParametersPort.latest()`), 없으면 설정 | R13, R24 |
| 9 묶음 | 앞 B건의 catalog id 순열 | R15 |
| 10 캐시 | `FeedBatchCachePort.put(key, batchNo, ids)` — 키는 조건 해시. 예외는 삼키고 기록 | R17 |
| 11 로그 | `FeedBatchLogPort.append(memberId, batchId, windows)` — 로그인만. 예외는 삼킴 | R21 |
| 12 응답 | 앞 `first`건을 `SpotQueryPort`로 hydrate → `SpotEdge` + `FeedCursor(hash, position = first, issuedAt)` | |

### 이어 받기

1. `FeedCursor.decode(after, secret)` — 서명·형식 실패 → `InvalidCursorException`, 조건 해시 불일치 → `InvalidCursorException`, 수명 초과 → `CursorExpiredException("CURSOR_EXPIRED", BAD_REQUEST)` **신설**.
2. 위치 p, 묶음 번호 b = p ÷ B. `FeedBatchCachePort.get(key, b)` 히트면 슬라이스, 미스·예외면 **첫 요청의 1~9를 다시 수행**해 같은 순열을 만든다(AC-17.2, AC-17.3). 재계산이 같은 결과를 내려면 아래 "결정성"을 지켜야 한다.
3. b번째 묶음이 필요한데 b ≥ 1이면 앞 묶음들의 id를 제외한 후보로 순열을 만든다(AC-16.2). 앞 묶음 id는 캐시 히트면 캐시에서, 아니면 재계산으로 얻는다.

### 결정성 (AC-13.3, AC-4.1)

- 후보는 (거리, id) 순으로 고정 순서로 받는다.
- 점수 계산은 `Double`이되 동점은 id 오름차순으로 가른다. DPP 그리디의 인자 최대 선택도 같다.
- 커널 계산은 같은 순서로 같은 부동소수 연산을 하므로 같은 입력에 같은 결과다. 다만 **묶음 사이에 반응이 기록되면** 후보가 바뀌어 재계산 결과가 어긋날 수 있다 — 캐시 미스일 때만 생기는 한두 장의 어긋남이며 스펙 §5가 명시한다.

### 커서 형식 (R4)

`f1|{conditionsHash}|{position}|{issuedAtEpochSec}|{hmacSha256}` → Base64 URL-safe 무패딩. 기존 커서(`SearchCursors.kt`)는 서명이 없지만 이 커서는 조건 해시로 불일치를 잡는 것이 목적이라 서명이 없으면 해시를 위조해 검사를 무력화할 수 있다. 키는 환경변수 `IEURI_FEED_CURSOR_SECRET`(매니페스트 secret, POL-056). 조건 해시 입력은 좌표(소수 5자리로 반올림)·반경·정렬된 카테고리 코드·언어다. 좌표는 커서 안에 실리지만 로그에는 남기지 않는다(POL-045).

### 캐시 어댑터

`ValkeyFeedBatchCache` — `ValkeySpotCache`와 같은 자리(`infrastructure/adapter/out/cache/`), 같은 인스턴스, 키 `ieuri:tourism:feed:{hash}:{batchNo}`, 값은 id 목록, TTL은 설정(T7). 모든 예외를 잡아 `null`/no-op으로 바꾼다 — 캐시는 있으면 빠르고 없으면 정확한 것이다.

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

`RecommendationProperties`, prefix `ieuri.tourism.recommendation`. 기존 properties는 평평한 data class 하나지만 이 기능은 키가 많아 중첩 data class(`score`, `dpp`, `cursor`, `cache`, `embedding`, `learning`, `batchLog`)를 쓴다 — 금지하는 선례는 없다. 키·출발값은 스펙 §9 표가 원본이며 여기 복사하지 않는다. T7에 걸린 값(커서 수명·캐시 TTL·학습 최소 사례·로그 보관)은 스펙 §9의 출발값(24h·30m·200·90일)을 코드 기본값으로 두고 KDoc에 출발점임을 적는다(POL-044). 여기서 다른 숫자를 고르지 않는다.

## 검증과 오류

| 상황 | 예외 | 코드 | 통로 |
|---|---|---|---|
| 좌표 범위 | 기존 `spots`와 같은 예외 | 기존 | 최상위 `errors[]` |
| 반경 상한 | `RadiusTooLargeException` | `RADIUS_TOO_LARGE` | 〃 |
| 빈 카테고리 | `EmptyCategorySetException` | `EMPTY_CATEGORY_SET` | 〃 (`SecurityGraphqlExceptionHandler`) |
| 커서 손상·서명·조건 불일치 | `InvalidCursorException` | `INVALID_CURSOR` | 〃 |
| 커서 수명 초과 | `CursorExpiredException` **신설** | `CURSOR_EXPIRED` | 〃 (`TourismQueryExceptionHandler`에 추가) |

`first`는 `coerceIn(1, 100)`으로 절삭한다(POL-018). 캐시·로그·유행·집계 포트의 실패는 오류가 아니다 — 요인 없음 또는 no-op이다.

## 테스트가 붙는 단위

애플리케이션·도메인 단위 테스트만이다(MockK + kotlin-test, 기존 `SpotQueryServiceTest`와 같은 모양).

| 테스트 | 판정하는 것 |
|---|---|
| `RecommendationScoreTest` | AC-6.x, 7.x, 8.x, 9.x, 10.1, 11.x, 12.2 |
| `TaxRefundTitleRuleTest` | AC-11.1, 11.2 |
| `SpotSimilarityTest` | AC-14.x, 20.x |
| `DppRerankerTest` | AC-13.x, 15.x, 16.1. 구현자가 증분 Cholesky 등 빠른 그리디를 택하면 나이브 행렬식 그리디와 같은 순열을 내는지 이 테스트에 고정한다 |
| `FeedCursorTest` | AC-4.2~4.4 인코딩·서명·만료 |
| `RecommendedFeedQueryServiceTest` | AC-4.1, 5.x, 10.2~10.4, 12.1, 12.3, 16.2, 16.3, 17.x, 21.x, 24.x — 포트 전부 스텁 |
| `NearbySpotsQueryServiceTest` | AC-2.x, 3.x |
| `LocationQueryServiceTest` | AC-1.1, 1.2 |
| `EmbeddingTargetTest` · `SpotEmbeddingSyncServiceTest` | AC-18.x, 19.1, 19.2 |
| `DppParameterGridTest` · `DppParameterLearningServiceTest` | AC-22.x, 23.x |
| `RecommendationPropertiesTest` | AC-25.x |

테스트하지 않는 것(인프라): Valkey 어댑터, Voyage HTTP 어댑터, 키셋 SQL, pgvector 저장, DGS 리졸버, 배치 런타임. AC-1.3(`spots` 불변)은 기존 테스트가 그대로 통과하는 것으로 판정한다.

## 이 저장소의 슬라이스가 열어주는 것

| 슬라이스 | 머지되면 호출 가능한 것 | 아직 없는 것 |
|---|---|---|
| S1 | `location.spots`, `location.recommendedSpots` 전부. 점수는 거리·인기·활동·면세, 유사도는 카테고리 대체. 커서·캐시·오류 코드 완성 | 벡터·취향·학습. 클라이언트는 **여기서 전부 착수** |
| S2 | 같은 계약. 유사도가 벡터로, 취향 요인 추가 | 학습 |
| S3 | 같은 계약. α·σ가 로그로 갱신 | — |

## 이 기능이 동작하려면 먼저 있어야 하는 것

| 데이터·설정 | 만드는 주체 | 없으면 |
|---|---|---|
| `IEURI_FEED_CURSOR_SECRET` | 매니페스트 secret(POL-056) | 커서 발급 불가 — S1 기동 조건 |
| `IEURI_VOYAGE_API_KEY` | 사용자 발급 → 매니페스트 secret | S2 임베딩 단계가 전부 실패로 카운트. 목록은 정상 |
| `vector` 확장 (선택한 DB) | 검색 노드면 이미 있음. 메인이면 매니페스트 | S2 마이그레이션 실패 |
| 학습 배치 CronJob | 매니페스트 | S3 미실행. 서빙은 기본값 |
| 벌크 집계 포트 3개 | 이 저장소(S1) | 인기·활동 요인 없음 → 거리·면세만으로 점수 |
| POL-014 보관 기간 행 | 플래너(implemented 시) | — |

## 관련

- SPEC-0004(`spots`·PostGIS), SPEC-0013(거리 감쇠 5km, 검색 노드), SPEC-0018(면세 태그 실측, `SpotExclusionRule`), SPEC-0019(카탈로그), SPEC-0020(카테고리 다중 선택), SPEC-0029(언어별 행), SPEC-0015(다요인 점수 선례)
- 문헌: Wilhelm 외, *Practical Diversified Recommendations on YouTube with DPPs* (CIKM 2018) — 커널 식·창 방식·그리디; Chen 외 2018 — fast greedy MAP

## 변경 이력

| 날짜 | 변경 | 계기 |
|---|---|---|
| 2026-09-11 | 초안 | ieuri-server #131 |
