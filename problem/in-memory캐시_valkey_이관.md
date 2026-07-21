# in-memory캐시_valkey_이관

> 토큰/세션등을 in-memory 캐시에서 캐시서버인 valkey로 이관을 목표로 한다.

---

## 범위 (Scope)

- **스텝**: in-memory로 사용되었던 `InMemoryRefreshTokenStore` → `ValkeyRefreshTokenStore`로 교체하는걸 목표로 한다. infra 영역이기 때문에 infra만 변경을 한다.
- **포트 불변**: `RefreshTokenStorePort` 시그니처(save/existsActive/revoke/revokeAllByMember)는 그대로 둔다. `application`/`domain`은 손대지 않는다.

---

## 포함 기능 (In Scope)

1. `save()` 기능을 기존 in-memory cache와 같은 로직이 흘러가야 된다.
2. `existsActive` 기능을 기존 in-memory cache와 같은 로직이 흘러가야 된다.
3. `revoke` 기능을 기존 in-memory cache와 같은 로직이 흘러가야 된다.
4. `revokeAllByMember` in-memory cache와 같은 로직이 흘러가야 된다.
5. valkey와 연결이 끊어지거나 오류가 발생시 클라이언트에게 해당 사항이 전달되어야 한다.

---

## 확정된 설계 규칙 (Design Decisions — 승인됨)

### 1. 만료(TTL) — Valkey 네이티브 TTL 사용

- jti 키에 만료 시각 기준 TTL을 걸어(`SET key value EX <seconds>`) Valkey가 자동 축출하게 한다.
- 따라서 `existsActive`는 **`EXISTS` 한 번**으로 처리한다(InMemory의 지연 정리 로직 불필요).
- `save` 시 `ttl = expiresAt - clock.instant()`를 계산한다. 이미 만료된(≤0) 경우 저장하지 않는다.
- TTL 계산은 주입된 `Clock` 빈을 사용한다(테스트 가능성).

### 2. 키 스키마 (네임스페이스)

- jti 인덱스: `ieuri:auth:refresh:jti:{jti}` → 값 = `memberId`(문자열)
- 회원 인덱스: `ieuri:auth:refresh:member:{memberId}` → `Set<jti>`
- 회원 인덱스 Set에는 개별 TTL이 없으므로, jti 키가 만료돼도 Set 멤버는 자동으로 사라지지 않는다(stale 누적).
  이는 `revokeAllByMember` 시점에 정리되므로 기능상 문제 없음. 다만 Set 자체가 영구히 남지 않도록,
  `save` 시 회원 Set에도 (해당 토큰 TTL로) `EXPIRE`를 갱신해 최신 refresh TTL만큼만 살아있게 한다.

### 3. 원자성 — 순차 명령 (best-effort)

- `save`/`revoke`/`revokeAllByMember`의 다중 키 조작은 Lua/MULTI 없이 순차 실행한다.
- 트레이드오프: 명령 사이 중간 실패 시 jti 키와 회원 Set 간 일시적 불일치가 가능하다.
  - stale Set 멤버는 `existsActive`(EXISTS)에 영향을 주지 않고, `revokeAllByMember`가 최종 정리한다.
  - 향후 일관성 강화가 필요하면 Lua 스크립트로 승격한다(후속 과제).

### 4. 연결 장애 처리 (요건 #5) — 자연 전파(일반 에러) + fail-closed

- 어댑터는 인프라 예외를 **잡지 않는다.** Spring Data Redis 예외(`RedisConnectionFailureException` 등)를
  그대로 전파시켜, 리졸버의 `withUserErrors`가 userError로 매핑하지 못하고(UserFacingException 아님)
  기존 `InvalidTokenException`과 동일하게 GraphQL **최상위 error로 전달**한다.
- **`existsActive`는 절대 장애를 `false`로 삼키지 않는다.** false 반환 시 재사용 감지가 오작동해
  해당 회원의 모든 세션이 폐기되는 심각한 부작용이 있으므로 반드시 예외를 던진다(fail-closed).
  `redis.hasKey`는 장애 시 예외를 던지므로 별도 처리 없이 fail-closed가 성립한다.
- **의도적으로 단순화**: 별도 `try/catch` 번역 계층이나 커스텀 예외(`TokenStoreUnavailableException`)를
  두지 않는다. 클라이언트가 이 장애를 *일반 에러*로 받아도 충분하다는 결정("Simple is Best")에 따른다.
  만약 클라이언트가 "저장소 일시 장애(재시도 가능)"를 별도 코드로 구분해야 한다면, 그때 필요한 것은
  커스텀 예외가 아니라 이를 안정적 error code로 매핑하는 전역 `DataFetcherExceptionResolver`다(후속 과제).

### 5. 기존 InMemory 구현 — 제거하고 완전 대체

- `InMemoryRefreshTokenStore` 및 `InMemoryRefreshTokenStoreTest`를 삭제한다.
- Valkey는 Redis 프로토콜 호환이므로 `spring-boot-starter-data-redis`(Lettuce)를 사용한다.

---

## 인프라/설정 (Config)

- 의존성: `implementation("org.springframework.boot:spring-boot-starter-data-redis")`
- 설정(`application.yaml`) — 전부 환경변수 외부화, **command timeout 필수**(장애 시 요청 스레드 행 방지):
  ```yaml
  spring:
    data:
      redis:
        host: ${IEURI_VALKEY_HOST:localhost}
        port: ${IEURI_VALKEY_PORT:6379}
        password: ${IEURI_VALKEY_PASSWORD:}
        timeout: 2s
        client-name: ieuri
  ```
- 로컬: `docker run -p 6379:6379 valkey/valkey:8` 또는 동등한 Valkey 인스턴스 필요.

---

## 테스트 전략 — 순수 단위 테스트만

- **정책**: 애플리케이션 외부로 나가는 대상(DB, 캐시서버(Valkey), OTEL 등)은 더 이상 테스트 스코프에 넣지 않는다.
  실제 저장/TTL 축출/네트워크는 Valkey의 책임이므로 Testcontainers 등 인프라 통합 테스트를 두지 않는다.
- `InMemoryRefreshTokenStoreTest` 삭제.
- `ValkeyRefreshTokenStoreTest`는 외부 Valkey 없이 `StringRedisTemplate`을 mockk 대역으로 두고 **어댑터 자체 로직만** 검증:
  만료 토큰 저장 스킵, 남은 TTL로 SET, 회원 전체 폐기 키 정리, 그리고 **요건 #5**(인프라 예외 →
  `TokenStoreUnavailableException` 번역, existsActive의 fail-closed).
- 별도 인프라 의존성(Testcontainers/Docker) 추가 없음.

---

## 범위 밖 (Out of Scope / 후속)

- 다중 키 조작의 Lua/트랜잭션 원자성 승격.
- 만료된 refresh 재제출이 재사용 감지로 전체 세션을 폐기하는 현재 동작(정책상 의도됨, 변경 안 함).
- top-level 에러에 안정적 error code/extensions를 부여하는 전역 `DataFetcherExceptionResolver`
  (현재는 기존 인증 에러와 동일하게 최상위 error로 전파하는 것에 맞춤).
