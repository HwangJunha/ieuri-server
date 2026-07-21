# in-memory캐시_valkey_이관

> 토큰/세션등을 in-memory 캐시에서 캐시서버인 valkey로 이관을 목표로 한다.

---

## 범위 (Scope)

- **스텝**: in-memory로 사용되었던 InMemoryRefreshTokenStore -> ValkeyRefreshTokenStore로 교체하는걸 목표로 한다. infra영여이기 때문에 infra만 변경을 한다.

---

## 포함 기능 (In Scope)

1. save() 기능을 기존 in-memory cache와 같은 로직이 흘러가야 된다.
2. existsActive 기능을 기존 in-memory cache와 같은 로직이 흘러가야 된다.
3. revoke 기능을 기존 in-memory cache와 같은 로직이 흘러가야 된다.
4. revokeAllByMember in-memory cache와 같은 로직이 흘러가야 된다.
5. valkey와 연결이 끊어지거나 오류가 발생시 클라이언트에게 해당 사항이 전달되어야 한다.

---