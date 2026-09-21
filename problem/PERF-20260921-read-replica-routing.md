# PERF-20260921 — 읽기 복제본 라우팅과 트랜잭션 경계

> 2026-09-21 개발 클러스터 부하 측정에서 확정된 내용. **DB primary CPU가 선형 병목**이고
> replica 2대는 완전히 놀고 있다. 다만 지금의 트랜잭션 경계로는 읽기 분리를 넣어도
> 절반만 효과를 본다.
>
> 선행 측정: [PERF-20260921-category-n1.md](./PERF-20260921-category-n1.md)

## 1. 무엇이 확정됐나

### CPU와 처리량이 선형이다

CNPG CPU limit만 바꾸고 같은 부하(100 RPS, k6)를 돌린 결과:

| | CNPG 500m | CNPG 1000m | 변화 |
|---|---|---|---|
| **TPS** | 12.20 | **26.11** | **+114%** |
| med | 8.88s | 5.40s | −39% |
| p90 | 25.13s | 19.41s | −23% |
| p95 | 30.12s | **24.34s** | −19% |
| 에러 | 1.07% | **0.21%** | −80% |

CPU를 2배 주니 처리량이 **2.14배**가 됐다. 그리고 1000m에서도 다시 포화됐다:

```
12:13:47  primary  466m
12:14:37  primary  908m
12:15:02  primary 1002m   ← 새 limit도 100%
replica       7~10m       ← 유휴
앱          144~290m      ← limit 1000m 대비 17~29%
```

cgroup `cpu.stat`(500m 시점): `nr_throttled 2326 / nr_periods 67331`, `throttled_usec 774초`.

### 같은 쿼리가 부하에 따라 20배 차이

```
부하 없음:   17~75ms
부하 중:     1,100ms
```

`Buffers: shared hit=27,714 read=0` — **디스크를 전혀 읽지 않는다.** 캐시 적중률 100%인데
CPU가 포화된다. 파싱·계획·스캔·가시성 검사·직렬화가 CPU를 쓰고, 그 자원이 모자란 것이다.

### 배제된 것들 (전부 실측)

| 후보 | 결과 |
|---|---|
| 앱 CPU | ❌ limit 1000m 중 17~29%만 사용 |
| 앱 메모리 | ❌ 2Gi 중 1036Mi (51%) |
| 커넥션 풀 | ❌ 30으로 충분, pending 없음 |
| Tomcat 스레드 | ❌ **가상 스레드**라 상한이 아님(플랫폼 스레드 40개) |
| 커버링 인덱스 | ❌ **효과 없음** — dev에서 실제로 만들어 재봄 |
| 언어별 조인 낭비 | ❌ **없음** |

**커버링 인덱스**: `(catalog_id, language) INCLUDE (title, address, visible) WHERE visible`을
`CREATE INDEX CONCURRENTLY`로 만들어 측정했다. `Index Only Scan`으로 계획이 바뀌었으나
56~91ms로 기존 63~79ms와 **겹친다**. 조인 9,028회는 `ORDER BY ST_Distance`의 구조적 비용이라
(정렬 후에야 LIMIT 적용) 인덱스로 줄지 않는다. 테스트 인덱스는 제거했다.

**언어 조인**: 반경 20km 안에 KO 8,707 / EN 5,185 / JA 5,176 / ZH 5,094행이 있으나,
실행계획의 `Index Cond`에 `language='KO'`가 들어가 **다른 언어 행은 읽지 않는다**(`rows=0.94`).

---

## 2. replica가 놀고 있다

```
IEURI_DB_URL = jdbc:postgresql://ieuri-pg-rw...   ← rw 하나만 사용

ieuri-pg-1 (primary)  커넥션 30 / CPU 1002m
ieuri-pg-2 (replica)  커넥션  0 / CPU  7~10m
ieuri-pg-3 (replica)  커넥션  0 / CPU  7~10m
```

CNPG가 `-ro`(replica 전용) 서비스를 만들어 두었는데 앱이 쓰지 않는다. `instances: 3`으로
**디스크 3벌·메모리 3벌 비용을 내면서 CPU는 1벌만** 쓰는 셈이다.

CPU와 처리량이 선형이므로 이론상 3인스턴스를 다 쓰면 26 → **~78 TPS**다(primary가 쓰기·복제도
하므로 실제로는 덜 나온다).

**운영에서는 이것 말고 방법이 없다.** 노드가 2 OCPU라 limit을 올려도 쓸 CPU가 없다.

---

## 3. 그런데 지금 구조로는 절반만 된다

### 전수 조사 결과

`@Transactional` 270건 중 `readOnly = true`가 106건인데, **위치가 뒤집혀 있다:**

| 레이어 | readOnly 보유 파일 |
|---|---|
| `infrastructure/adapter/out/query` | **25** |
| `infrastructure/adapter/out/persistence` | 10 |
| `application/service` | **6** |

그리고 **조회 서비스 11개가 `@Transactional`을 아예 쓰지 않는다:**

```
RecommendedSpotsQueryService   MapQueryService
SpotDiscoveryService           MapRecommendationService
SpotQueryService               RegionRecommendationService
CommunityQueryService          ConversationQueryService
SpotQaQueryService             MemberQueryService
RoleQueryService
```

클래스 레벨 `@Transactional`은 **0건**이다. 전부 메서드 레벨이고, 그것도 대부분 어댑터에 있다.

### 이것이 만드는 결과

Spring 라우팅은 `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`로 판단한다.
서비스에 트랜잭션이 없으면 **어댑터마다 별도 트랜잭션**이 열린다:

```
recommendedSpots()                    ← 트랜잭션 없음
  ├ findReactedSpotIdsByReaction()    ← 어댑터의 @Transactional(readOnly=true)
  │    커넥션 획득 → 반납
  ├ searchNearby()                    ← 별도 트랜잭션
  │    커넥션 획득 → 반납
  └ findSimilarities()                ← 또 별도
       커넥션 획득 → 반납
```

Tempo 트레이스가 정확히 이 모양이다(전체 24.58s):

```
connection 19.88s  →  query 248µs
connection  3.39s  →  query 93ms
connection  1.21s  →  query 1.1s
connection  4.67ms →  query 4.54ms
```

**요청 하나가 커넥션을 4회 이상 빌린다.** 그리고 가상 스레드라 동시 요청 수에 상한이 없어,
600 VU가 커넥션 30개를 두고 경쟁한다.

### 읽기 분리를 넣으면 생길 문제

동작 자체는 한다 — 조회 어댑터 25개에 `readOnly=true`가 있으니 그 쿼리들은 replica로 간다.
하지만:

- 한 요청 안에서 **여러 트랜잭션**이 열리므로 각각 **다른 replica**로 갈 수 있다
- 복제 지연이 인스턴스마다 다르면 **같은 요청 안에서 데이터 시점이 어긋난다**
- 예: 반응 조회는 replica-1(지연 10ms), 공간 검색은 replica-2(지연 200ms)

---

## 4. 해야 할 일 (순서대로)

### ① 서비스 레이어에 트랜잭션 경계 세우기 — 선행 조건

```kotlin
@Transactional(readOnly = true)
override fun recommendedSpots(...): List<SpotView> {
    // 어댑터 호출들이 하나의 트랜잭션·하나의 커넥션 안에서
}
```

효과가 둘이다:

1. **커넥션 획득 4회 → 1회** — 오늘 측정한 병목이 직접 줄어든다
2. **한 요청이 한 인스턴스로 고정** — 읽기 분리 시 시점 일관성

대상은 위 11개 조회 서비스. **읽기 분리보다 먼저 해야 한다** — 이것만으로도 커넥션 경합이
줄고, 이게 없으면 읽기 분리의 일관성이 깨진다.

⚠️ 트랜잭션 경계가 바뀌면 영속성 컨텍스트 수명도 바뀐다. `open-in-view: false`이므로
지연 로딩이 서비스 밖으로 새어 나가면 `LazyInitializationException`이 난다. 조회 서비스들이
DTO/View로 변환해 반환하는지 확인이 필요하다.

### ② 읽기/쓰기 라우팅

`AbstractRoutingDataSource` + **`LazyConnectionDataSourceProxy`**:

```
LazyConnectionDataSourceProxy
  └ RoutingDataSource
       ├ readOnly=true  → ieuri-pg-ro   (replica)
       └ readOnly=false → ieuri-pg-rw   (primary)
```

⚠️ **`LazyConnectionDataSourceProxy`는 선택이 아니라 필수다.** 없으면 트랜잭션 시작 시점에
커넥션을 먼저 잡아 라우팅 판단 전에 primary가 결정된다. 에러 없이 **조용히 전부 primary로 간다.**

⚠️ 기본값은 primary여야 한다. 애노테이션 없는 조회는 primary로 가고(느리지만 정확), 쓰기가
replica로 새어 `cannot execute INSERT in a read-only transaction`이 나는 일은 없어야 한다.

⚠️ **JDBC `targetServerType=preferSecondary`는 쓰면 안 된다.** 커넥션 수립 시점에만 서버를
고르므로, 그 커넥션으로 쓰기를 보내면 실패한다. 읽기 전용 워크로드용 옵션이다.

### ③ 쓰기 직후 읽기 경로 식별

CNPG 기본이 비동기 복제다. 좋아요를 누르고 바로 목록을 조회하는 식의 경로는 `readOnly=false`로
두거나 별도 처리가 필요하다. **어느 경로가 그런지 목록을 만들어야 한다.**

---

## 5. 하지 말아야 할 것

| | 이유 |
|---|---|
| 커넥션 풀 더 늘리기 | DB가 이미 100%다. 동시 실행만 늘어 쿼리가 더 느려진다 |
| Tomcat 스레드 튜닝 | 가상 스레드라 설정 자체가 무의미하다 |
| 커버링 인덱스 | 실측으로 효과 없음 확인 |
| pgpool-II / PgCache 도입 | 2 OCPU 노드에 프록시 컨테이너를 더 얹는다. CNPG `-rw`/`-ro`와 기능도 겹친다 |
| 운영 CNPG CPU limit 상향 | 노드가 2 OCPU라 올려도 쓸 CPU가 없다 |

---

## 6. 열린 질문

- [ ] 11개 조회 서비스에 `@Transactional(readOnly = true)`를 붙였을 때 지연 로딩이 깨지는 곳은?
- [ ] 쓰기 직후 읽기가 필요한 경로 목록 (복제 지연 허용 불가)
- [ ] `candidate-cap: 1000`이 적정한가 — 후보 9,028건 조인이 거리 정렬의 비용인데,
      300으로 낮췄을 때 카테고리 다양성이 얼마나 나빠지는지 재측정 필요
      (300 → 1000은 2026-09-18에 올린 이력이 있다)
- [ ] 운영 replica 3개가 **같은 노드·같은 디스크**다. 읽기 분리로 CPU는 나뉘지만 I/O는
      그대로다. DB 노드 분리 후에야 온전한 효과가 난다
