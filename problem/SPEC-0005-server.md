> spec: SPEC-0005 · source: `Specs/SPEC-0005/spec.md` (Obsidian) · status: amended

# SPEC-0005 — 서버 설계

요구사항과 수용 기준은 스펙이 정본이다. 이 문서는 그것을 다시 적지 않고 번호로 인용하며, 이 레포에서의
설계 결정만 다룬다.

이 문서는 스펙 v1.2에서 신설된 **R15 하나만** 다룬다. SPEC-0005의 나머지 요구사항(R1~R14, R16)은 전부
클라이언트 소관이며 `ieuri-k-trip-spot/problem/SPEC-0005-client.md`에 있다.

관련 이슈: #32 · 브랜치 `feature/SPEC-0005-auth-error-surface` (`dev`에서 분기)

---

## 1. 무엇이 문제인가

`reactToSpot`은 인증 실패를 payload의 `userErrors[]`에 `code: "UNAUTHENTICATED"`로 담아 반환한다.
의도된 설계였다.

- `ReactToSpotResolver.kt:31-33` — `requireMemberId()`가 최상위 error를 던지므로 의도적으로 피하고,
  `currentMemberId()`가 null이면 `UnauthenticatedReactionException`을 던진다.
- `TourismExceptions.kt:19` — `UnauthenticatedReactionException : ReactionUserException("UNAUTHENTICATED", ...)`
- `TourismGraphqlErrorSupport.kt:12-16` — `toReactionUserErrorOrNull`이 `ReactionUserException`을
  `UserError`로 변환한다.

클라이언트의 자동 토큰 갱신은 최상위 `errors[]`를 보는 링크 계층에서 일어난다. `userErrors`는
`response.data` 안에 있으므로 링크 계층이 보지 못한다. 결과적으로 좋아요/싫어요 경로에서 accessToken이
만료되면 **자동 갱신이 아예 트리거되지 않는다**(`k-trip-spot#22` `[AMEND]`, 실기기 재현).

`access-ttl: 5m`(`src/main/resources/application.yaml`)이므로 한 화면에 5분만 머물러도 재현된다.

## 2. 설계 결정

### 2.1 인증 실패를 `ReactionUserException` taxonomy에서 분리한다 (R15)

`UnauthenticatedReactionException`이 `ReactionUserException`을 상속하는 한
`withReactionUserErrors`가 이를 붙잡아 payload로 변환한다. 상속 관계를 끊는 것이 이 변경의 핵심이며,
리졸버의 분기를 손보는 것으로는 부족하다 — taxonomy에 남아 있으면 다음 뮤테이션에서 같은 실수가 반복된다.

`reactToSpot`의 비로그인 처리는 `currentMemberProvider.requireMemberId()`로 되돌린다. 이 메서드는
`UnauthorizedException`을 던지고(`CurrentMemberProvider.kt:16-17`), 이는 `ReactionUserException`이
아니므로 `withReactionUserErrors`의 `?: throw e`(`TourismGraphqlErrorSupport.kt:22`)를 타고 그대로
최상위 `errors[]`로 전파된다. auth 도메인 리졸버들이 이미 쓰는 경로와 동일해진다.

`ReactToSpotResolver.kt:18`과 `:31`의 주석("최상위 error 아님", "requireMemberId()는 쓰지 않는다")은
결정이 뒤집혔으므로 함께 갱신한다. 주석이 남으면 다음 사람이 되돌린다.

### 2.2 나머지 도메인 실패는 건드리지 않는다 (AC-41)

`SpotNotFoundException`(`SPOT_NOT_FOUND`)과 `IllegalArgumentException`(`INVALID_INPUT`)은
`ReactionUserException` 경로에 그대로 남는다. 이들은 "요청은 정상 처리됐고 도메인 규칙에 걸렸다"에
해당하며, BaseResponse 규약이 원래 의도한 용법이다.

바뀌는 것은 `userErrors`가 실어 나르는 code 집합에서 `UNAUTHENTICATED` 하나가 빠지는 것뿐이다.
SDL은 변경되지 않는다.

### 2.3 `errorType` 매핑은 이 이슈가 아니다

R15는 인증 실패를 최상위 `errors[]`로 **옮기는** 것까지만 요구한다. 옮겨진 오류의
`extensions.errorType`을 `UNAUTHENTICATED`로 매핑하는 것은 #26 소관이며, `UnauthorizedException`
전반에 적용되는 별도 작업이다.

**#26과 함께 배포되어야 한다.** 이 변경만 먼저 배포하면 `reactToSpot`의 인증 실패는
`errorType: INTERNAL` + 예외 클래스명이 담긴 message 형태로 나간다. 클라이언트는 이를 E1(문자열 매칭)로
판별하므로 **동작은 한다** — AC-40은 충족된다. 다만 취약한 경로이고, #19가 message 노출을 차단하면
그 순간 깨진다. 스펙 「미결 사항」 서버 측 후속 작업 1·2 참조.

배포 순서 의존은 이 문서가 아니라 #26·#32 두 이슈에 기록되어 있다.

### 2.4 규약을 taxonomy 주석에 남긴다

`TourismExceptions.kt`의 KDoc은 "쿼리엔 payload 컨벤션이 없어 두 경로가 다르다"고 설명한다. 이 설명은
유효하지만, 인증 실패가 그 분기와 무관하게 항상 최상위라는 규약이 추가되었으므로 함께 적는다.

스펙이 POL-016의 하위 규약으로 승격 예정인 문장은 다음이다 — **인증 실패는 도메인·연산과 무관하게 항상
최상위 `errors[]`로 표면화한다. `userErrors[]`는 세션 상태를 나타내는 값을 담지 않는다.**

## 3. 계층 경계

- 예외 taxonomy는 application 계층(`tourism/application/exception`)에 있고, GraphQL 표면화는 인바운드
  어댑터(`infrastructure/adapter/in/graphql`)가 담당한다. 이 경계는 유지된다 — application은 GraphQL
  `ErrorType`을 알지 않는다(`TourismExceptions.kt:26-38`의 기존 방침과 동일).
- `UnauthorizedException`은 `common/security`에 있는 공유 커널이다. tourism 도메인이 이를 던지는 것은
  이미 auth 도메인이 하고 있는 것과 같으며 새 의존을 만들지 않는다.
- `requireMemberId()`로 되돌리는 것은 인증 판정을 리졸버에서 없애는 것이 아니라, 판정 결과의 표면화
  방식만 바꾸는 것이다. `ReactToSpotService`는 여전히 memberId를 받는다(`ReactToSpotService.kt:15`).

## 4. 검증

`ieuri-planner` 규약상 **단위 테스트는 application·domain 계층만** 대상이다. 리졸버와 GraphQL 표면화는
어댑터이므로 테스트 대상이 아니다.

이 변경의 본질이 어댑터 계층의 표면화 방식이라는 점을 그대로 적는다.

| 수용 기준 | 단위 테스트 가능 여부 |
|---|---|
| AC-39 (인증 실패가 최상위 `errors[]`로) | **불가** — 어댑터 표면화. 실제 GraphQL 응답을 봐야 확인된다 |
| AC-40 (클라이언트 갱신 트리거) | **불가** — 클라이언트 측 기준이며 서버 단위 테스트 범위 밖 |
| AC-41 (도메인 실패는 `userErrors` 유지) | **불가** — 동일한 이유 |
| AC-42 (`userErrors`에 `UNAUTHENTICATED` 없음) | **가능 — 단위 테스트로 닫는다.** 아래 참조 |

**AC-42는 단위 테스트 대상이다.** 이 기준은 어댑터 표면이 아니라 **예외 taxonomy의 불변식**이다 —
"`userErrors`로 변환되는 예외 집합에 인증 실패가 없다"는 명제이며, taxonomy는 application 계층
(`tourism/application/exception`, `auth/application/exception`)에 있고 순수하다.

검증 대상은 `toReactionUserErrorOrNull`(`TourismGraphqlErrorSupport.kt:12`)과
`toUserErrorOrNull`(`GraphqlErrorSupport.kt:13`)이다. 둘 다 `Throwable`을 받아 `UserError?`를 돌려주는
순수 함수이며 프레임워크도 컨텍스트도 필요 없다. `ReactionUserException`과 `UserFacingException`의
모든 하위 클래스를 넣어 돌려받은 `UserError.code`에 `"UNAUTHENTICATED"`가 없음을 단언한다.

이 테스트는 **회귀 방지 장치이지 이번 변경의 확인 절차가 아니다.** §6에 적은 대로 이 규약을 강제할
스키마 수단이 없으므로, 다음 도메인이 같은 실수를 반복하는 것을 막는 유일한 자동 장치가 이 테스트다.
새 뮤테이션이 인증 실패를 `userErrors`에 담으면 이 테스트가 깨져야 한다.

`UserError` 생성 타입이 `common.graphql.generated`에 있어 어댑터 패키지에서 테스트하게 되나, 검증
대상 함수 자체는 순수하며 DB·전송·Spring 컨텍스트를 요구하지 않는다.

AC-39·AC-41은 실제 요청을 보내 응답 형태를 육안 확인한다. 만료된 토큰(또는 토큰 없음)으로
`reactToSpot`을 호출해 최상위 `errors[]`가 채워지고 `data.reactToSpot.userErrors`에 인증 항목이
없음을 본다. 이를 자동화하려면 GraphQL 전송 계층과 Spring 컨텍스트가 필요하므로, 규약에 따라
**테스트하지 않고 남긴다.**

`ReactToSpotService`는 손대지 않으므로 그쪽에 추가되는 테스트는 없다. 이 변경으로 추가되는 단위
테스트는 위의 AC-42 taxonomy 불변식 하나다.

## 5. POL-008 승격 내용에 대한 서버 측 확인

스펙의 「적용 정책」이 POL-008(토큰 정책)을 `new`로 두고 승격 내용을 제안하고 있다. v1.1까지 이 스펙에는
서버 문서가 없어 그 내용이 서버 측에서 확인된 적이 없었다. v1.2에서 `ieuri-server`가 target이 되었으므로
여기서 확인한다.

승격 내용은 **이미 구현·운영 중인 동작의 명문화**이며 새로 결정된 것이 아니다 — 스펙의 서술과 일치한다.
확인 대상은 만료(access 5분 / refresh 30일), 1회용 회전, 재사용 감지 시 전체 세션 폐기, 다기기 로그인
허용, 로그아웃 시 해당 토큰만 폐기, Valkey 저장소와 fail-closed다.

**이 스펙은 위 어느 것도 변경하지 않는다.** R15는 오류의 표면화 위치만 바꾸며 토큰 수명·회전·저장소에
손대지 않는다. 따라서 POL-008 승격은 이 스펙의 구현 결과와 무관하게 성립하며, `implemented` 도달 전
`policies.md`로 승격되면 된다.

**저장소 항목도 현재 상태가 맞다.** `ieuri-server#16`("refresh 토큰 저장소 in-memory 캐시 → Valkey
이관")이 열려 있어 목표 상태일 가능성을 검토했으나, 코드 대조 결과 **작업은 완료되어 있고 이슈만 닫히지
않은 상태**다.

- `ValkeyRefreshTokenStore`(`auth/infrastructure/adapter/out/token/`)가 `RefreshTokenStorePort`의
  **유일한 구현체**다. `ConcurrentHashMap` 기반 in-memory 구현은 코드베이스에 남아 있지 않다.
- `build.gradle.kts`에 `spring-boot-starter-data-redis`, `application.yaml:37-42`에 Valkey 접속 설정이
  환경변수로 외부화되어 있다.
- **fail-closed 적용됨** — `existsActive`가 인프라 예외를 잡지 않고 전파한다. 장애를 `false`로 삼키면
  재사용 감지가 오작동해 세션이 전멸하므로, 이는 의도된 설계이며 클래스 KDoc에 명시되어 있다.

따라서 POL-008은 조건 없이 승격 가능하다. `#16`은 별건으로 닫으면 된다 — 이 스펙의 `implemented` 도달을
막지 않는다.

## 6. 알려진 부채

- **`errorType: INTERNAL` 잔존.** 2.3에 적은 대로 이 변경 단독으로는 `reactToSpot` 인증 실패가
  `INTERNAL`로 나간다. #26이 닫혀야 `UNAUTHENTICATED`가 된다. 그 전까지 클라이언트는 문자열 매칭
  경로로 판별한다.
- **규약의 강제 수단이 없다.** "인증 실패는 항상 최상위 `errors[]`"는 문서와 코드 리뷰로만 지켜진다.
  `userErrors`의 code는 SDL상 `String!`이므로 스키마 검증에 걸리지 않고, 서버 하네스의 계약 드리프트
  검출도 이 규약 위반을 잡지 못한다. 다음 뮤테이션에서 같은 실수가 반복될 수 있다.
