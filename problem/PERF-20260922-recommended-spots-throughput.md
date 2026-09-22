# PERF-20260922 — recommendedSpots 처리량(RPS) 병목 분석과 개선 방안

> **정정 (2026-09-22, SPEC-0038) — 이 문서의 §1-3·§1-4와 개선 방안 ①은 실측으로 반증됐다.**
>
> 아래 §1-3이 근거로 든 704ms는 실측이 맞지만 **앱이 실행하지 않는 쿼리 형태**다. 그 EXPLAIN은
> 후보 제한 없이 `ORDER BY embedding <=> const LIMIT 100`을 8만 행 전체에 돌린 것이고,
> `TasteSimilarityQueryJdbcAdapter`의 실제 쿼리는 `t.catalog_id = ANY(?)`로 후보를 먼저 좁혀
> `spot_embedding_pkey` 인덱스 조회(1000 loops × 약 16µs)를 탄다 — **실측 26ms**다.
>
> 그래서 ①(top-K 재설계 + HNSW)은 **병목이 아닌 곳을 겨냥**하며, 추천 랭킹을 바꿔 recall을
> 떨어뜨리는 비용만 남는다(SPEC-0038 BR-001의 하한 0.90).
>
> ②(candidateCap 하향)도 처리량 대책이 아니다. 거리 정렬은 `LIMIT` **전에** 반경 안 전 행을
> 번역 테이블과 조인해야 하므로(가산 20km에서 8,925회, 버퍼 27,593개) cap을 바꿔도 조인
> 횟수와 버퍼는 불변이고 heapsort 크기만 바뀐다.
>
> **진짜 병목은 그 `LIMIT` 이전 조인이었다.** 번역 행이 좌표·분류 사본을 갖고 있어 카탈로그
> 조인을 `LIMIT` 뒤로 옮길 수 있고, 그것으로 버퍼가 27,593 → 4,626개(6배)가 된다.
> 자세한 근거와 기각 대안은 `SPEC-0038-server.md`의 D-01~D-07에 있다.
>
> §1-1·§1-2(부하 추이와 HikariCP 지표)와 §1-6(병목이 아닌 것)은 유효하다. §2 ③·④도 유효하다.

## 요약

100 RPS 목표로 dev(api-dev)에 k6 부하를 걸었으나 **TPS가 35에서 벽에 부딪힌다.**
순차적으로 병목을 걷어낸 결과, 자원(CPU·메모리)·커넥션은 더 이상 병목이 아니고,
**남은 벽은 `recommendedSpots` 쿼리 자체의 처리 시간**임이 실측으로 확정됐다.

이 문서는 그 근거와, 서버·앱·인프라에 걸친 개선 방안을 정리한다. 단순 서버 튜닝만으로는
부족하며 앱(클라이언트) 계약과 추천 파이프라인 설계까지 손봐야 한다.

---

## 1. 측정으로 확정된 사실 (2026-09-22, dev)

### 1-1. 부하 결과 추이

| 시점 | 조건 | TPS | p95 | 오류율 |
|---|---|---|---|---|
| 초기 | 앱 1파드, 읽기 분리 전 | ~36 | 5.6s | 0% |
| 읽기 분리 후 | 앱 2파드, 풀 30 | ~36 | 8s | 12~19% (502) |
| 커넥션 상향 후 | 앱 2파드, 풀 60, max_conn 500 | ~35 | 9.85s | **0%** |

**커넥션을 30→60으로 올리고 DB max_connections를 200→500으로 올렸더니 502·timeout은
사라졌지만(오류율 0%), TPS는 그대로다.** 즉 커넥션은 병목이 아니었다.

### 1-2. HikariCP 지표 (읽기 풀 = HikariPool-2, -ro)

부하 절정 구간(2초 간격 실측):

```
active=60 (풀 60개 전부 사용 중)   pending 최대 205   timeout=0
acquire 대기 평균 1.9초             replica 메모리 600Mi/2Gi (여유)   replica CPU 여유
```

풀 60개가 **항상 꽉 차 있고**(active=60 유지), 대기열(pending)이 205까지 쌓인다.
timeout은 0 — 실패는 없고 **기다렸다 처리**되므로 p95만 늘어난다. **커넥션을 더 늘려도
동시 실행 쿼리 수만 늘 뿐, replica가 초당 처리하는 쿼리 수가 한계다.**
`application.yaml`의 오랜 경고 그대로다: "조회 하나가 커넥션을 오래 잡는 상태에서 풀만
열면 DB 동시 실행만 늘어난다."

### 1-3. 근본 원인 — vector 유사도 쿼리 (pgvector, 인덱스 없음)

`spot_embedding` = **80,741행**, `embedding vector(1024)`. pgvector 0.8.5(HNSW 지원).

**`embedding` 컬럼에 ANN 인덱스(HNSW/IVFFlat)가 없다.** V6 마이그레이션이 테이블만 만들고
인덱스를 하나도 만들지 않았다. `spot_embedding`의 인덱스는 PK(translation_id) 하나뿐.

replica EXPLAIN ANALYZE 실측:

```
-- 후보 제한 없는 코사인 정렬 top-100
ORDER BY embedding <=> const LIMIT 100
 → Parallel Seq Scan on spot_embedding (80,741행 전량)
 → Execution Time: 704 ms          ← 유사도 1회에 0.7초

-- model 필터 avg(embedding) (centroid 계산)
WHERE model='voyage-4'
 → Seq Scan 80,741행 전량 (model 카디널리티=1, 필터가 아무것도 못 거른다)
 → Execution Time: 635 ms
```

### 1-4. 그런데 인덱스만으로 해결되지 않는다 (쿼리 형태 문제)

실제 앱 쿼리(`TasteSimilarityQueryJdbcAdapter.findSimilarities`)는 **top-K 형태가 아니다.**
candidate 집합(`t.catalog_id = ANY(ARRAY[...])`, 최대 1000개)에 대해 **전부** 코사인 거리를
계산하고 전량 반환한다. `ORDER BY embedding <=> centroid LIMIT k`가 없다.

→ **pgvector ANN 인덱스는 `ORDER BY <=> LIMIT k` 형태에서만 쓰인다.** 지금 쿼리 형태로는
HNSW 인덱스를 만들어도 옵티마이저가 선택하지 않는다. 비용의 본질은 "candidate 1000개 ×
2 centroid × 1024차원 정확 코사인 계산"이다.

즉 **① 인덱스 부재 + ② 인덱스를 못 쓰는 쿼리 형태 + ③ candidateCap 1000**의 3중 문제다.

### 1-5. candidateCap = 1000

`application.yaml:595` `ieuri.tourism.recommendation.candidate-cap: 1000`
(코드 기본 300 → 2026-09-18 가산/20km 실측 근거로 1000으로 상향). 이 값이 유사도 계산량을
직접 결정한다. 공간 검색(`searchNearby`)이 최대 1000 후보를 뽑고, 그 1000개 전부가 vector
계산으로 넘어간다.

### 1-6. 병목이 아닌 것 (확정)

- **공간 검색(PostGIS)**: `idx_spot_catalog_location` GiST 인덱스 있음, `ST_DWithin`
  인덱스 친화 형태. 정상.
- **커넥션 풀 / max_connections**: 60/500으로 timeout 0 달성. 병목 아님.
- **앱 CPU**: 절정에도 542~573m (limit 1000m의 57%). 남는다.
- **replica 자원**: 메모리 600Mi/2Gi, CPU 여유. 병목 아님.
- **N+1 (category label/iconUrl)**: DataLoader로 이미 배치됨(PR#146). 정상.
- **Traefik OOM(502)**: 파드 재기동 창의 일시 현상. 부하 자체와 무관(재현 안 됨).

---

## 2. 개선 방안

우선순위는 "효과 대비 위험". ①이 가장 확실하고 위험이 낮다.

### ① [서버] vector 쿼리를 top-K 형태로 재설계 + HNSW 인덱스 — 핵심

**무엇:**
- `findSimilarities`를 candidate 전량 계산이 아니라, centroid 기준 **`ORDER BY embedding
  <=> centroid LIMIT k`** 형태로 바꾼다.
- `spot_embedding.embedding`에 `USING hnsw (embedding vector_cosine_ops)` 인덱스 생성
  (Flyway 신규 마이그레이션).

**효과:** 8만(또는 1000) 정확 계산 → ANN 로그 스케일 탐색. 유사도 쿼리가 수백 ms →
수 ms 예상. 이것이 커넥션 점유 시간을 직접 줄여 TPS를 올린다.

**주의 (반드시 검토):**
- 현재 쿼리는 like/dislike **두 centroid**를 쓴다. top-K를 두 축으로 어떻게 정의할지
  (like 유사도 상위? like-dislike 결합 점수?) 추천 로직 재설계가 필요하다 — 단순 인덱스
  추가가 아니라 **추천 랭킹 설계 변경**이다. 앱/기획과 합의 필요.
- HNSW 인덱스 빌드는 8만 행 × 1024차원이라 메모리·시간을 쓴다. `maintenance_work_mem`,
  빌드 중 replica 영향, `CREATE INDEX CONCURRENTLY` 여부를 정해야 한다.
- 인덱스가 생기면 정확도(recall)가 100%가 아니게 된다. `ef_search`/`m` 파라미터로 recall과
  속도를 저울질해야 하며, 추천 품질 영향은 앱에서 확인해야 한다.

### ② [서버/설정] candidateCap 재검토 (1000 → ?)

**무엇:** `candidate-cap: 1000`을 낮춰 유사도 계산량을 직접 줄인다.

**효과:** 즉효·저위험(설정 한 줄). 500으로 낮추면 vector 계산량 절반.

**주의:** 2026-09-18에 300→1000으로 **의도적으로 올린** 값이다(가산 20km 커버리지 근거).
낮추면 넓은 지역에서 추천 후보가 줄어 **추천 품질·커버리지가 나빠진다.** 앱에서 실제
추천 결과가 허용 범위인지 확인해야 하는 **앱 관여 작업**이다. ①이 되면 cap을 유지해도
되므로, ①의 대안이자 임시책.

### ③ [앱] 추천 요청 계약 재검토

**무엇:**
- 추천을 **매 요청 실시간 계산**하는 대신, 사용자별 추천을 **비동기 선계산·캐싱**하고
  요청 시 캐시를 읽게 한다(Valkey 활용).
- 또는 페이지네이션·프리페치로 한 요청의 계산량을 줄인다.

**효과:** 근본적. 실시간 vector 계산을 요청 경로에서 걷어내면 RPS 상한이 크게 오른다.

**주의:** 추천 신선도(reaction 반영 지연) vs 성능 트레이드오프. 앱 UX·기획 결정이 필요한
**가장 큰 앱 작업**이다. SPEC 수준 논의 대상.

### ④ [인프라] work_mem / replica 튜닝 (후순위)

읽기 120 커넥션이 동시에 정렬을 쓰면 work_mem×동시성이 replica 2Gi를 압박할 수 있다.
현재는 여유(600Mi)지만, ①~③으로 쿼리가 가벼워지기 전까지 부하를 더 올리면 재검증 필요.
**쿼리를 고치기 전에 인프라만 키우는 것은 근본 대책이 아니다**(1-2 참조).

---

## 3. 권장 순서

1. **② candidateCap 임시 하향**으로 즉시 숨통(앱 추천 품질 확인 후) — 저위험 임시책
2. **① top-K 재설계 + HNSW 인덱스** — 근본 해결. 추천 랭킹 설계 변경이라 앱/기획 합의 필요
3. **③ 선계산·캐싱** — 실시간 계산을 걷어내는 구조 개선. SPEC 논의
4. ①~③ 각 단계마다 dev 재부하로 TPS·p95·replica 메모리 재측정

## 4. 미해결 / 확인 필요

- ①의 top-K 재설계에서 like/dislike 두 centroid를 어떻게 단일 순위로 접을지 (추천 로직)
- HNSW 파라미터(m, ef_construction, ef_search)와 recall 허용선 — 추천 품질 기준 필요
- ② cap 하향 시 실제 추천 커버리지 저하폭 — 앱에서 검증
- 운영 반영 시: 운영 replica도 2Gi, 노드 2 OCPU/12Gi라 HNSW 빌드·메모리 산술 재검토

---

## 부록 — 실측 쿼리 (replica EXPLAIN용)

### 공간 검색 (searchNearby) — 정상, 인덱스 사용

```sql
WITH c AS (SELECT ST_MakePoint(:lon, :lat)::geography AS center)
SELECT cat.id, t.title, ..., ST_Distance(cat.location, c.center) AS distance_meters
FROM tour.spot_catalog cat
     JOIN tour.spot_catalog_translation t ON t.catalog_id = cat.id AND t.visible, c
WHERE t.language = 'EN'
  AND ST_DWithin(cat.location, c.center, 20000)      -- GiST 인덱스 사용
ORDER BY ST_Distance(cat.location, c.center), cat.id
LIMIT 1000;
```

### vector 유사도 (findSimilarities) — 병목, 인덱스 미사용

```sql
WITH liked AS (SELECT avg(e.embedding) AS v FROM tour.spot_embedding e
   JOIN tour.spot_catalog_translation t ON t.id=e.translation_id
   WHERE e.model='voyage-4' AND t.language='EN' AND t.catalog_id = ANY(ARRAY[/*liked*/]::bigint[])),
 disliked AS (... 동일 구조, disliked ids ...)
SELECT t.catalog_id,
   1 - (e.embedding <=> liked.v)    AS like_similarity,
   1 - (e.embedding <=> disliked.v) AS dislike_similarity
FROM tour.spot_embedding e
     JOIN tour.spot_catalog_translation t ON t.id=e.translation_id
     CROSS JOIN liked CROSS JOIN disliked
WHERE e.model='voyage-4' AND t.language='EN'
  AND t.catalog_id = ANY(ARRAY[/* 최대 1000 candidate */]::bigint[]);
-- ORDER BY <=> LIMIT k 가 없어 ANN 인덱스 미사용. candidate 전량 정확 계산.
```
