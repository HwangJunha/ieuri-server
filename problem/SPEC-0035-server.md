> spec: SPEC-0035 · source: `Specs/SPEC-0035/spec.md` (Obsidian) · status: approved

# SPEC-0035 — 서버 설계: 내 주변 추천

요구사항·수용 기준·계약은 스펙 문서에 있다. 이 문서는 이 저장소가 그것을 **어떻게** 실현하는지만 적는다.

## 문제

가산처럼 아울렛 건물 하나에 옷가게 수백 개가 매달린 곳에서 "내 주변"을 가까운 순으로 보여 주면 화면이
옷가게로만 찬다(SHOPPING 56%, 한 좌표에 207행). 사용자가 원하는 것은 둘이다 — **내 취향에 맞는 곳이
앞에 오는 것**, 그리고 **옷가게만이 아니라 여러 종류가 섞여 나오는 것**.

## 이 변경의 성격

가산 변경이다. 루트 쿼리 `recommendedSpots` 하나를 더한다. 기존 `spots`(거리순 오프셋 목록)는
**손대지 않는다** — 그것이 "전체"이고 클라이언트가 이미 쓴다.

## 추천은 목록이 아니라 묶음이다

| | 목록(`spots`) | 추천(`recommendedSpots`) |
|---|---|---|
| 순서 | 거리(정렬 키) | 개인화 점수. 요청마다 다시 계산 |
| 다음 것 | 페이지네이션(같은 정렬 키로 이어 받기) | 묶음. "아직 반응하지 않은 것 중 앞 first건" |
| 본 것 | 신경 안 씀 | 반응(좋아요·싫어요)이 DB에 있어 자연히 빠진다 |
| 커서 | page/size | **없음** |

스와이프 화면은 로그인 전용이라 반응은 항상 기록된다. 클라이언트는 큐가 줄면 다시 부르고, 큐에 남은
카드와 겹치는 id만 걷어낸다. 서버는 세션·스냅샷·커서·캐시를 갖지 않는다.

## 배치

전부 `tourism`이다.

| 단위 | 계층 | 역할 |
|---|---|---|
| `TaxRefundTitleRule` | domain/specification | 제목 패턴으로 면세 태그 장소 판정. 패턴은 설정 |
| `RecommendationScore` | domain/strategy | 거리 `1/(1+거리÷반감)`, 취향 `((cos좋아요 − cos싫어요) + 1)/2`, 취향이 있으면 `(1−w)·거리 + w·취향`, × 면세 배율. 순수 함수 |
| `EmbeddingText` | domain/specification | 임베딩 입력 `"제목 | 카테고리 라벨 | 주소"` |
| `CategoryDiversity` | domain/strategy | 같은 카테고리 n번째에 `penalty×(n−1)` 감점 후 정렬. 행을 숨기지 않는다 |
| `RecommendationPolicy` | application/policy | 위 셋 + 후보 상한 N·취향 최소 반응 수·임베딩 사양(모델·차원·재시도). 설정값을 담는다 |
| `coordinatesOf`·`requireRadiusWithin` | application/validation | 도메인 검증 실패 → 오류 코드 번역. `spots`와 같은 코드 |
| `RecommendedSpotsQueryService` | application/service | 검증 → 감사 이벤트 → 반응(좋아요·싫어요) → 후보(반응 제외) → 취향 유사도(SQL 1회) → 점수 → 감점 정렬 → 앞 first건 |
| `SpotEmbeddingSyncService` | application/service | 대상 행 → 텍스트 → Voyage(즉시 재시도) → 저장. 실패는 세고 건너뛴다 |
| `SpotEmbeddingStorePort` → `SpotEmbeddingUpsertJdbcAdapter` | out/persistence | 대상 판정(벡터 없음·텍스트 변경·모델 불일치)과 upsert. 텍스트 해시는 SQL이 행에서 계산 |
| `TasteSimilarityQueryPort` → `TasteSimilarityQueryJdbcAdapter` | out/query | 좋아요·싫어요 평균(`avg(vector)`)과 후보의 코사인(`<=>`)을 SQL 한 번으로. 벡터는 DB 밖으로 안 나온다 |
| `TextEmbeddingPort` → `VoyageEmbeddingClient` | out/client/voyage | `POST /v1/embeddings`. 키는 환경변수 |
| `SpotEmbeddingTasklet` | infrastructure/batch | `spotSyncJob` 마지막 단계. 번역 행 id 키셋 |
| `RecommendedSpotsResolver` | infrastructure/in/graphql | 인자 옮기기·응답 변환만. `requireMemberId` |
| `SpotGqlMapper` | infrastructure/in/graphql/mapper | `SpotView → Spot` |

기존 것을 그대로 쓴다: `SpotSpatialQueryPort.searchNearby`(후보), `SpotQueryPort.findReactedSpotIdsByReaction`
(제외 집합이자 취향 재료), `AppCategoryQueryPort.findDisplays`(임베딩 텍스트의 카테고리 라벨),
`TourismEventPublisherPort`(감사 이벤트 — **서비스가 발행한다**, 리졸버가 아니다).

## 요청 한 번의 흐름

1. 좌표·반경 검증(`spots`와 같은 오류 코드), `first`는 1~100 절삭.
2. `LocationAccessed(memberId, "recommendedSpots")` 발행. 좌표는 넘기지 않는다.
3. 이 회원이 활성 언어에서 남긴 반응을 좋아요·싫어요로 가른다.
4. 후보: 반경 안 가까운 순 N건(설정 300), 반응한 장소 제외 — 기존 SQL.
5. 취향(S2): 반응이 최소 수(5) 이상이면 SQL 한 번 — 좋아요 평균 벡터·싫어요 평균 벡터와 후보 300개의 코사인. 벡터 없는 후보·조회 실패는 취향 없음.
6. 점수: 취향이 있으면 `(1−w)·거리 + w·취향`, 없으면 거리. × 면세 배율.
7. 카테고리 반복 감점으로 정렬. 동점은 id.
8. 앞 `first`건 → `Spot`. `first`보다 적으면 소진.

## 설정 (`ieuri.tourism.recommendation`, 출발값 · POL-044)

| 키 | 출발값 | 근거 |
|---|---|---|
| `distance-half-life-meters` | 5000 | SPEC-0013 실측 |
| `tax-refund-multiplier` | 0.8 | 출발점 |
| `tax-refund-title-patterns` | EN·JA·ZH·KO 4개 | 실측 2026-09-11: EN 10,489 · JA 10,490 · ZH 10,349 · KO 28행 |
| `category-repeat-penalty` | 0.1 | 출발점. 점수가 0~1이라 같은 카테고리 두 번째가 앞에 오려면 0.1 이상 높아야 한다 |
| `candidate-cap` | 300 | 출발점 |
| `taste-weight` | 0.5 | 출발점 |
| `taste-min-reactions` | 5 | 출발점 |
| `embedding.model` / `dimension` | `voyage-4` / 1024 | 확정. 차원은 `spot_embedding.embedding vector(1024)`와 같아야 한다 |
| `embedding.batch-size` / `retry-count` | 128 / 3 | Voyage 요청당 1,000건·320K 토큰 안. 재시도는 즉시(POL-040) |
| `ieuri.voyage.api-key` | `${IEURI_VOYAGE_API_KEY:}` | 없으면 기동은 되고 임베딩 단계만 실패로 센다 |

가산·동부산 아울렛 좌표에서 첫 묶음의 카테고리 분포를 실측한 뒤 확정한다.

## 취향 (S2)

**저장 위치는 메인 DB `tour.spot_embedding`**(마이그레이션 V6). 검색 노드는 논리 복제 구독자라 데이터소스가
읽기 전용이고, 메인 DB(dev)에는 `vector 0.8.5`가 이미 설치돼 있어 반응·좌표와 한 DB에서 만난다.
**운영 메인 클러스터 매니페스트(`infra/cnpg/cluster/values.yaml`의 `database.extensions`)에는 `vector`가
선언돼 있지 않다** — 운영 반영 전 추가해야 V6가 통과한다(POL-034·056).

- 번역 행(언어별)마다 벡터 하나. PK `translation_id`(FK CASCADE). 대상 판정은 SQL 한 조건 — 벡터 없음·
  `text_hash`(제목|분류|주소의 md5) 변경·모델 불일치. 초기 적재·증분·모델 교체가 같은 경로다.
- 텍스트는 `"제목 | 카테고리 라벨(행의 언어) | 주소"`. 제목은 저장된 그대로(면세 태그 포함).
- Voyage `voyage-4` 1024차원, `input_type: document`. 요청당 128건. 실패는 즉시 3회 재시도 후 건너뛰고
  센다 — 그 행은 다음 실행의 대상으로 남는다. 차원이 다른 벡터는 저장하지 않는다.
- 요청 시 SQL 한 번: `avg(embedding)`으로 좋아요·싫어요 평균, `1 − (embedding <=> 평균)`으로 후보 300개의
  코사인. 숫자만 돌려준다. 좋아요 평균이 없으면(벡터 없음) 그 후보는 취향 없음.
- 취향 요인 `((cos좋아요 − cos싫어요) + 1)/2`. 싫어요 평균이 없으면 cos싫어요 = 0. 반응(좋아요+싫어요)이
  5 미만이면 취향 항 없음.
- `spot_embedding`은 JPA 엔티티가 없다(`vector` 타입). `SchemaPlacementTest`의 엔티티 없는 표에 등록.

실측(2026-09-11): 대상 번역 행 80,529건, 반응 131건 — 취향이 실제로 켜지는 회원은 아직 거의 없다.

## 버린 것 (2026-09-11~12)

첫 구현은 스펙을 그대로 따라 `Location` 맥락 타입, `Location.spots` 키셋 커서, 커서 커넥션 추천,
HMAC 서명·조건 해시 커서, Valkey 묶음 캐시, DPP 재배열, 커뮤니티 활동 집계를 만들었다가 전부 버렸다
(PR #132 폐기). 원인은 **추천을 페이지네이션 목록으로 본 전제**다. 피드에는 페이지가 없고, 페이지를
흉내 내려는 순간 스냅샷·위치·해시·캐시가 딸려 온다. 스펙 §12에 이력이 있다.

## 변경 이력

| 날짜 | 변경 | 계기 |
|---|---|---|
| 2026-09-11 | 초안(스펙 그대로: Location·커서·캐시·DPP) | ieuri-server #131 |
| 2026-09-12 | S2 취향 구현. 임베딩은 메인 DB `spot_embedding`(검색 노드는 읽기 전용 구독자). Voyage 적재 단계를 `spotSyncJob` 끝에, 취향 유사도는 pgvector SQL 한 번 | 사용자 결정(2026-09-12) |
| 2026-09-12 | 전면 재작성. 추천 = 커서 없는 묶음 루트 쿼리, 인증 필수, 기존 `spots` 불변. 점수는 거리·면세, 다양성은 카테고리 반복 감점. 취향은 S2 | 사용자 결정(2026-09-12) |
