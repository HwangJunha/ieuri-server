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
| `RecommendationScore` | domain/strategy | `1/(1+거리÷반감)` × (태그면 배율). 순수 함수 |
| `CategoryDiversity` | domain/strategy | 같은 카테고리 n번째에 `penalty×(n−1)` 감점 후 정렬. 행을 숨기지 않는다 |
| `RecommendationPolicy` | application/policy | 위 둘 + 후보 상한 N. 설정값을 담는다 |
| `coordinatesOf`·`requireRadiusWithin` | application/validation | 도메인 검증 실패 → 오류 코드 번역. `spots`와 같은 코드 |
| `RecommendedSpotsQueryService` | application/service | 검증 → 감사 이벤트 → 후보(반응 제외) → 점수 → 감점 정렬 → 앞 first건 |
| `RecommendedSpotsResolver` | infrastructure/in/graphql | 인자 옮기기·응답 변환만. `requireMemberId` |
| `SpotGqlMapper` | infrastructure/in/graphql/mapper | `SpotView → Spot` |

기존 것을 그대로 쓴다: `SpotSpatialQueryPort.searchNearby`(후보), `SpotQueryPort.findReactedSpotIds`
(제외), `TourismEventPublisherPort`(감사 이벤트 — **서비스가 발행한다**, 리졸버가 아니다).

## 요청 한 번의 흐름

1. 좌표·반경 검증(`spots`와 같은 오류 코드), `first`는 1~100 절삭.
2. `LocationAccessed(memberId, "recommendedSpots")` 발행. 좌표는 넘기지 않는다.
3. 후보: 반경 안 가까운 순 N건(설정 300), 이 회원이 활성 언어에서 반응한 장소 제외 — 기존 SQL.
4. 점수: 거리 감쇠 × 면세 배율.
5. 카테고리 반복 감점으로 정렬. 동점은 id.
6. 앞 `first`건 → `Spot`. `first`보다 적으면 소진.

## 설정 (`ieuri.tourism.recommendation`, 출발값 · POL-044)

| 키 | 출발값 | 근거 |
|---|---|---|
| `distance-half-life-meters` | 5000 | SPEC-0013 실측 |
| `tax-refund-multiplier` | 0.8 | 출발점 |
| `tax-refund-title-patterns` | EN·JA·ZH·KO 4개 | 실측 2026-09-11: EN 10,489 · JA 10,490 · ZH 10,349 · KO 28행 |
| `category-repeat-penalty` | 0.1 | 출발점. 점수가 0~1이라 같은 카테고리 두 번째가 앞에 오려면 0.1 이상 높아야 한다 |
| `candidate-cap` | 300 | 출발점 |

가산·동부산 아울렛 좌표에서 첫 묶음의 카테고리 분포를 실측한 뒤 확정한다.

## 다음 슬라이스 — 취향 (S2)

장소마다 "제목 | 카테고리 | 주소" 임베딩을 두고, 회원이 LIKE한 장소 벡터의 평균과 DISLIKE 평균으로
`cos(후보, 좋아요) − cos(후보, 싫어요)`를 0~1로 옮겨 점수에 더한다. 최소 반응 수 미만이면 항을 뺀다.
피드라 한 쿼리 제약이 없으므로 저장 위치(검색 노드 pgvector vs 메인)는 S2 시작 때 실측으로 정한다.
스펙 R10·R18~R20이 이 범위다.

## 버린 것 (2026-09-11~12)

첫 구현은 스펙을 그대로 따라 `Location` 맥락 타입, `Location.spots` 키셋 커서, 커서 커넥션 추천,
HMAC 서명·조건 해시 커서, Valkey 묶음 캐시, DPP 재배열, 커뮤니티 활동 집계를 만들었다가 전부 버렸다
(PR #132 폐기). 원인은 **추천을 페이지네이션 목록으로 본 전제**다. 피드에는 페이지가 없고, 페이지를
흉내 내려는 순간 스냅샷·위치·해시·캐시가 딸려 온다. 스펙 §12에 이력이 있다.

## 변경 이력

| 날짜 | 변경 | 계기 |
|---|---|---|
| 2026-09-11 | 초안(스펙 그대로: Location·커서·캐시·DPP) | ieuri-server #131 |
| 2026-09-12 | 전면 재작성. 추천 = 커서 없는 묶음 루트 쿼리, 인증 필수, 기존 `spots` 불변. 점수는 거리·면세, 다양성은 카테고리 반복 감점. 취향은 S2 | 사용자 결정(2026-09-12) |
