# 운영 배포 시 DB TLS 적용 — 확인 메모

> 작성: 2026-08-10 · 계기: 로컬에서 `sslmode=disable`을 적용하며 "운영은 어떻게 할 것인가"를
> 정리한 것. **아직 적용하지 않았다** — 서버가 클러스터에 배포될 때 이 문서를 보고 반영한다.
>
> 이 저장소의 코드 변경은 없다. 작업 대상은 `ieuri-manifest`다.

---

## 0. 한 줄 요약

DB는 TLS를 켠다(`sslmode=verify-full`). **앱 인증서는 필요 없고**, CNPG가 발급한 **CA만
앱 네임스페이스로 가져오면** 된다. istio도 필요 없다.

---

## 1. 지금 상태 (2026-08-10 확인)

| 항목 | 상태 |
|---|---|
| 서버 배포 | **아직 안 됨** — `ieuri` 네임스페이스의 `ktripsopt`은 `nginx:1.28-alpine` placeholder |
| DB | CNPG `ieuri-pg` 3인스턴스, primary는 `ieuri-pg-2`, `ssl = on` |
| 검색 노드 | CNPG `ieuri-search` 1인스턴스 |
| 노드 | 단일(`lima-rancher-desktop`) |
| CNPG TLS 자산 | `ieuri-pg-ca` · `ieuri-pg-server` 시크릿 자동 발급됨 |
| `ieuri-pg-app` | **`ieuri` 네임스페이스에 이미 복사돼 있음**(27일째) — 비밀번호는 해결된 상태 |

운영 DB URL은 아직 정해진 것이 없다. 그래서 지금 정하면 된다.

---

## 2. 왜 TLS를 켜는가

`sslmode`를 **명시하지 않으면 JDBC 기본값은 `prefer`** 다 — SSL을 시도하되 실패하면
**조용히 평문으로 내려앉는다.** 암호화가 안 돼도 아무도 모른다는 뜻이라, 운영에서는
반드시 명시해야 한다. **이 문서에서 가장 중요한 한 가지다.**

`require`가 아니라 `verify-full`을 쓰는 이유: `require`는 암호화만 하고 **상대가 누구인지
확인하지 않는다.** CNPG가 CA를 이미 발급해 뒀으므로 신원 검증까지 하는 데 드는 추가
비용이 CA 파일 마운트뿐이다.

단일 노드라 지금은 트래픽이 기기 밖으로 나가지 않지만, 노드를 늘리는 순간 노드 간
평문이 된다. 그때 기억해서 바꾸는 것보다 처음부터 켜 두는 편이 낫다.

---

## 3. 앱 인증서는 필요 없다 (핵심)

서버 인증과 클라이언트 인증은 다른 것이다.

| | 무엇을 증명 | 필요한 것 | 우리 앱 |
|---|---|---|---|
| 서버 인증 | 이 DB가 진짜 우리 DB인가 | 앱이 **CA**로 서버 인증서를 검증 | **필요** |
| 클라이언트 인증(mTLS) | 이 앱이 진짜 우리 앱인가 | 앱이 **자기 인증서+키** 제시 | **불필요** |

TLS는 한쪽만 인증해도 성립한다(HTTPS로 은행 접속 시 내 PC에 인증서가 없는 것과 같다).
근거는 `pg_hba`다:

```
hostssl  {postgres}      {streaming_replica}       all  cert            ← CNPG 내부
hostssl  {replication}   {streaming_replica}       all  cert            ← CNPG 내부
hostssl  {all}           {cnpg_pooler_pgbouncer}   all  cert            ← CNPG 내부
host     {all}           {all}                     all  scram-sha-256   ← 우리 앱: 비밀번호
```

**`cert`(클라이언트 인증서)를 요구받는 건 CNPG 내부 계정뿐**이고 그건 CNPG가 알아서
발급·갱신한다. 우리 앱 계정 `ieuri`는 `scram-sha-256`, 즉 비밀번호 인증이다.

### istio가 필요 없는 이유

서비스 메시는 **앱이 TLS를 모를 때** 사이드카가 대신 감싸주는 물건이다. Postgres
프로토콜과 JDBC 드라이버는 TLS를 자체 지원하므로 이미 되는 것을 프록시로 감쌀 이유가 없다.

---

## 4. 네임스페이스를 나눠도 되는가 — 된다

DB는 `ieuri-db`, 앱은 `ieuri`로 나누는 구성에서:

| | 네임스페이스 경계를 넘나 | 대응 |
|---|---|---|
| 서비스 DNS | **넘는다** | `ieuri-pg-rw.ieuri-db.svc.cluster.local`로 그냥 붙는다 |
| Secret | **못 넘는다** | CA를 앱 네임스페이스로 **가져와야 한다** |

서버 인증서 SAN에 아래가 모두 들어 있어 **FQDN으로 쓰면 `verify-full`이 통과**한다:

```
ieuri-pg-rw, ieuri-pg-rw.ieuri-db,
ieuri-pg-rw.ieuri-db.svc, ieuri-pg-rw.ieuri-db.svc.cluster.local
(+ -r, -ro 각각 동일 4종)
```

네임스페이스 이름은 **`ieuri`를 그대로 쓰는 편이 낫다** — 시크릿·Valkey가 이미 거기 있어
`ieuri-app`으로 새로 파면 전부 옮겨야 한다.

---

## 5. 실제 검증한 것 (추측 아님)

클러스터 안에 임시 파드를 띄워 확인했다. 두 경우 모두 **클라이언트 인증서 없이 CA만으로**
접속했다.

| 검증 | 결과 |
|---|---|
| 같은 네임스페이스(`ieuri-db`)에서 `verify-full` | `t \| TLSv1.3 \| TLS_AES_256_GCM_SHA384` |
| **다른 네임스페이스(`ieuri`)에서 FQDN + `verify-full`** | `t \| TLSv1.3 \| TLS_AES_256_GCM_SHA384` |

검증에 쓴 접속 문자열:

```
host=ieuri-pg-rw.ieuri-db.svc.cluster.local port=5432 dbname=ieuri user=ieuri
sslmode=verify-full sslrootcert=/ca/ca.crt
```

테스트용으로 복사했던 CA는 정리했다. 원래 있던 `ieuri-pg-app`은 건드리지 않았다.

---

## 6. 배포 시 반영할 것

### 6.1 CA를 앱 네임스페이스에 두기

**CA 인증서는 공개 정보다** — 비밀이 아니라 "이 CA를 믿어라"는 선언이며 599바이트 텍스트다.
그래서 ConfigMap으로 저장소에 커밋해도 된다(권장).

```yaml
apiVersion: v1
kind: ConfigMap
metadata: { name: ieuri-pg-ca, namespace: ieuri }
data:
  ca.crt: |
    -----BEGIN CERTIFICATE-----
    ...   # kubectl get secret ieuri-pg-ca -n ieuri-db -o jsonpath='{.data.ca\.crt}' | base64 -d
    -----END CERTIFICATE-----
```

대안: Reflector/kubernetes-replicator로 자동 동기화. **지금은 과하다** — 옮길 것이 CA
하나뿐인데 컨트롤러를 하나 더 운영해야 한다. 다만 §7의 갱신 문제를 없애 준다.

### 6.2 앱 Deployment

```yaml
volumes:
  - name: db-ca
    configMap: { name: ieuri-pg-ca }
volumeMounts:
  - { name: db-ca, mountPath: /etc/db-ca, readOnly: true }
env:
  - name: IEURI_DB_URL
    value: >-
      jdbc:postgresql://ieuri-pg-rw.ieuri-db.svc.cluster.local:5432/ieuri
      ?sslmode=verify-full&sslrootcert=/etc/db-ca/ca.crt
  - name: IEURI_DB_PASSWORD
    valueFrom:
      secretKeyRef: { name: ieuri-pg-app, key: password }
```

**검색 노드도 같이 해야 한다.** `IEURI_SEARCH_DB_URL`은 `prepareThreshold=0`을 유지한 채
같은 파라미터를 붙인다(CA는 `ieuri-search-ca`로 별도다):

```
jdbc:postgresql://ieuri-search-rw.ieuri-db.svc.cluster.local:5432/ieuri
  ?prepareThreshold=0&sslmode=verify-full&sslrootcert=/etc/search-ca/ca.crt
```

### 6.3 확인

배포 후 이 쿼리로 실제 암호화 여부를 본다. `ssl=t`가 아니면 평문이다.

```sql
select ssl, version, cipher from pg_stat_ssl where pid = pg_backend_pid();
```

---

## 7. 남는 문제 · 범위 밖

- **CA 갱신.** CNPG가 CA를 자동 갱신하는데, ConfigMap 복사본은 **따라가지 않는다.**
  갱신 주기(기본 CA 1년 / 서버 인증서 90일)를 확인하고 만료 전에 갱신하는 절차가 필요하다.
  ConfigMap 방식의 유일한 실질 비용이며, Reflector를 쓰면 사라진다.
- **Valkey는 아직 평문이다.** `valkey-0/1`이 TLS 없이 떠 있고 Redis 프로토콜 TLS는 서버
  쪽에서 따로 켜야 한다. **refresh 토큰이 오가는 경로**라 언젠가 봐야 한다. 이번 검토
  범위 밖이라 확인만 했다.
- **mTLS(클라이언트 인증서)까지 갈 것인가.** 비밀번호가 이미 시크릿으로 관리되고 CNPG가
  그 전제로 `pg_hba`를 구성해 둬서 **지금은 과하다.** 필요해지면 그때 판단한다.
- 로컬의 `sslmode=disable`은 `application-local.yaml`에만 있어 운영 프로파일에 영향이 없다.
  터널(`kubectl port-forward`) 위에서 SSL 핸드셰이크가 깨지는 로컬 고유 문제 때문이다.
