# SPEC-0013 · pg_search 검색 노드 분리 (ieuri-manifest)

> 대상 레포: `ieuri-manifest` · **파일 수정 완료, 적용 대기**
>
> 설계 문서 §6대로 매니페스트는 ieuri-server가 작성했고, **클러스터 적용은 사용자가 한다.**

## 왜 검색 전용 노드인가 — 세 번의 실패로 도달한 결론

처음 설계(§6)는 "메인 클러스터에 pg_search 확장만 켜면 된다"였다. 실제로는 세 단계로 막혔고,
마지막 것이 구조를 바꿨다.

1. **`schema "pdb" does not exist`** — 확장이 설치되지 않았다.
   원인은 `pg_search`가 `vector`(pgvector)를 의존 확장으로 요구하는데 빠뜨린 것.
2. **`must be loaded via shared_preload_libraries`** — 파일은 있는데 프로세스가 적재하지 않았다.
   ImageVolume("파일이 있는가")과 `shared_preload_libraries`("미리 적재했는가")는 다른 축이다.
3. **replica 2개가 CrashLoopBackOff** — 여기서 설계가 바뀌었다.

```
FATAL: replicas are not supported on community and require paradedb enterprise
context: WAL redo at 1/A93AA140 for pg_search/INIT_INDEX
```

**pg_search 커뮤니티 에디션은 물리 복제(standby)를 지원하지 않는다.** ParadeDB 소스의
`custom_rmgr.rs`가 `INIT_INDEX && StandbyMode`일 때 무조건 `error!`를 던진다 — 끌 수 있는
GUC도, 라이선스 키도 없다(Enterprise는 별도 private 이미지다). 0.24.0(PR #4901)에서 들어왔고,
메인테이너는 그 이전 버전도 "안전했던 게 아니라 단속만 안 했던 것"이라고 밝혔다.

### 그래서 논리 복제로 분리한다

게이트 조건이 좁다는 점이 열쇠다 — **BM25 인덱스를 만드는 WAL 레코드를 standby가 재생할 때만**
터진다. 따라서:

```
[메인] ieuri-pg  instances: 3      ← pg_search 없음. 물리 복제·HA 그대로
  primary ──(물리/바이트)──▶ replica-1, replica-2
     │
     │ 논리 복제 (행 변경만, 인덱스·DDL 제외)
     ▼
[검색] ieuri-search  instances: 1  ← pg_search 여기만. 자기가 primary라 게이트에 안 걸림
```

메인에 BM25 인덱스가 없으니 문제의 WAL 레코드 자체가 생기지 않고, replica는 재생할 것이 없다.
검색 노드는 standby가 아니라 독립 primary이므로 `StandbyMode`가 false다.

**우회가 아니라 ParadeDB가 커뮤니티 사용자에게 권장하는 구성이다**(0.19.0부터). ES를 도입하는
것과 달리 동기화 코드·별도 쿼리 DSL·JVM 스택이 필요 없고, Postgres가 복제를 책임진다.

## 고친 파일 (커밋 대기)

### `infra/cnpg/cluster/values.yaml` — 메인, pg_search 제거

`pg_search`·`vector`·`sharedPreloadLibraries`·ParadeDB ImageVolume을 **전부 걷어내 원래대로**
되돌렸다(postgis만 남는다). 대신 `publication`을 추가했다 — 발행은 발행자 쪽에 만들어진다.

복제 대상은 `spot`·`spot_festival`·`spot_reaction` 셋이다. `FOR ALL TABLES`를 쓰지 않는 이유는
회원·인증 테이블까지 검색 노드로 복제할 이유가 없기 때문이다(POL-013의 취지).

`wal_level`은 CNPG 기본값이 이미 `logical`이라 건드리지 않았다(실측 확인).

### `infra/cnpg/cluster/values-search.yaml` (신규) — 검색 노드

같은 차트를 다른 값으로 띄운다. `instances: 1`, 확장 3종(postgis → vector → pg_search 순서),
`sharedPreloadLibraries: [pg_search]`, 그리고 메인을 가리키는 `externalClusters`와 `subscription`.

> **`publication: null`이 필요하다.** Helm은 `-f`로 준 파일을 차트 기본값 `values.yaml` **위에
> 병합**하므로, 그냥 생략하면 메인의 publication 정의를 상속해 검색 노드에도 엉뚱한
> publication이 생긴다(실측으로 확인하고 고쳤다).

### `infra/cnpg/cluster/templates/cluster.yaml`

세 가지를 고쳤다.

1. `postgis` 키 하드코딩을 `range`로 일반화 — 확장을 더 얹을 때 템플릿을 안 고쳐도 된다.
2. `shared_preload_libraries` 렌더링 추가(`postgresql:` 블록을 `extensions`의 `with` 밖으로 꺼냈다).
3. `externalClusters` 렌더링 추가 — Subscription이 이름으로 참조한다.

### `infra/cnpg/cluster/templates/logical-replication.yaml` (신규)

CNPG의 `Publication`·`Subscription` CRD를 쓴다. 수동 SQL이 아니라 선언적으로 관리된다.
둘 다 `Reclaim: retain`이다 — 구독자가 붙은 채 발행이 사라지면 복제가 조용히 멈추고,
구독을 지울 때 슬롯 정리 순서가 어긋나면 발행자에 WAL이 무한정 쌓인다.

> **k8s 리소스 이름과 Postgres 식별자를 분리해야 한다.** k8s `metadata.name`은 RFC 1123이라
> **밑줄을 허용하지 않는데**, Postgres 식별자는 밑줄이 관례다. 처음에 `search_pub`을 그대로
> 이름에 붙였다가 argocd sync가 통째로 멈췄다:
>
> ```
> Publication.postgresql.cnpg.io "ieuri-pg-search_pub" is invalid:
> metadata.name: Invalid value: a lowercase RFC 1123 subdomain must consist of ...
> ```
>
> `metadata.name`에만 `| replace "_" "-"`를 걸고 `spec.name`은 원래 값을 넘긴다.
> 렌더링 결과: `metadata.name=ieuri-pg-search-pub`, `spec.name=search_pub`.

### `argocd/db/cnpg-search.yaml` (신규)

검색 노드용 Application. 생명주기가 달라 별도로 둔다 — 이 노드는 언제든 버리고 다시 만들 수
있어야 하고(원본은 메인에 있다), 그 복구가 메인의 동기화에 영향을 주면 안 된다.

## 렌더링 검증 (완료)

```
메인   : Cluster / Database / Publication      (pg_search 흔적 0건)
검색   : Cluster / Database / Subscription     (instances 1, 확장 3종, preload)
```

## 적용 (사용자)

**순서가 중요하다.**

1. **메인 먼저 적용** — pg_search가 빠지면서 롤링 재시작이 일어나고, 죽어 있던 replica 2개가
   살아난다. `kubectl get pods -n ieuri-db`로 3/3 확인.
2. **검색 노드 Application 생성** — argocd에 `cnpg-search.yaml`을 등록한다.
3. **구독 확인** — 최초 스냅샷 복사에 시간이 걸린다(spot 11만 행).
   ```
   kubectl get subscription -n ieuri-db
   kubectl get cluster -n ieuri-db          # ieuri-search가 healthy인지
   ```
4. **검색 노드에 스키마가 필요하다** — 논리 복제는 **DDL을 옮기지 않는다.** 구독 전에 검색
   노드에 `spot`·`spot_festival`·`spot_reaction` 테이블이 **이미 존재해야** 행을 받을 수 있다.
   아래 "미해결" 참조.

## 미해결 — 사용자 결정이 필요하다

**검색 노드의 스키마를 누가 만드는가.** 논리 복제는 행만 옮기므로 테이블 구조는 별도로
만들어야 하고, 이후 `spot` 스키마가 바뀔 때마다 **양쪽에 각각** 적용해야 한다. 선택지:

- Flyway가 두 데이터소스에 모두 마이그레이션한다 (서버 코드 변경 필요)
- 검색 노드는 필요한 테이블만 별도 스크립트로 관리한다
- CNPG `bootstrap.initdb.import`로 초기 스키마를 가져온다

**BM25 인덱스(V43)를 어디서 만드는가.** 지금 V43은 메인에 적용되는 Flyway 마이그레이션인데,
이 인덱스는 **검색 노드에만** 있어야 한다. 메인에 만들면 replica가 다시 죽는다.

**앱의 데이터소스 분리.** 검색 어댑터만 검색 노드를 보게 라우팅해야 한다.

이 셋은 서버 레포의 작업이며 SPEC-0013의 원래 범위를 넘는다. 별도 스펙으로 뺄지, 이번에
함께 처리할지 결정이 필요하다.
