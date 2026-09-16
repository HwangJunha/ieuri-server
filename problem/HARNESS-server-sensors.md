# 하네스 개편 — 계산적 센서

> `server-개편.md` §5의 센서를 무엇으로 어디까지 만들지에 대한 조사와 결정.
>
> **1차 센서를 켰다**(2026-09-16). 아래 "1차 센서" 절이 실제로 들어간 것이고, 그 아래는
> 그렇게 고른 근거가 된 전수 조사 결과다.

## 1차 센서 — 무엇을 켰는가

센서의 목적은 **컨벤션을 기계적으로 고정해 LLM이 판단 없이 자연스럽게 맞는 코드를 쓰게 하는
것**이다. ArchUnit이 하는 일과 같다 — 규칙이 빌드에 박혀 있으니 어기면 즉시 빨갛고, 그래서
매번 "이게 맞나"를 판단할 필요가 사라진다.

그래서 **이미 100% 지켜지는 컨벤션**을 골랐다. 역설적이지만 그게 가장 값싼 센서다 — 지금 전부
지켜지고 있으니 전체 코드를 검사해도 녹색이고, 앞으로 깨지는 것만 막는다. 리팩토링을 기다릴
필요가 없다.

| 파일 | 규칙 | 도구 |
|---|---|---|
| `ConsistencyConventionTest` | application이 JPA·Hibernate·QueryDSL에 의존하지 않는다 | ArchUnit |
| | domain이 common에 의존하지 않는다 | ArchUnit |
| | domain·리졸버에 `@Transactional`을 붙이지 않는다 (클래스·메서드 양쪽) | ArchUnit |
| | Spring Data 리포지토리는 `Repository`로 끝나고 `persistence/repository/`에 있다 | ArchUnit |
| | `Dto` 접미사를 쓰지 않는다 | ArchUnit |
| | 테스트 클래스는 `Test`로 끝난다 | ArchUnit |
| `ImportStyleTest` | 와일드카드 import를 쓰지 않는다 | Konsist |

**검사 범위는 전체 코드다.** 위반이 0건이라 그래도 된다.

### 도구를 왜 이렇게 나눴나

ArchUnit을 우선한다 — 이미 쓰고 있고(`LayeredArchitectureTest`), 바이트코드를 읽어 빠르며,
선례가 있어 읽는 사람이 한 체계만 알면 된다.

**와일드카드 import 하나만 Konsist다.** ArchUnit으로는 불가능하다 — 바이트코드에 import가 남지
않아 `import a.b.*`와 개별 import가 컴파일 후 구별되지 않는다. 소스를 읽는 도구가 필요하고,
그 자리가 Konsist가 의존성 값을 하는 유일한 자리다.

### 검증

각 규칙이 실제로 일하는지 양쪽으로 확인했다:

- **기준선 녹색** — 전체 테스트 통과. `@Ignore`도 예외 목록도 없다.
- **범위가 비어 있지 않다** — 규칙이 아무 클래스도 못 잡으면 통과해도 무의미하다.
  main 2208 / test 217 클래스, application 574 / domain 189 / 리졸버 95 / 리포지토리 46 /
  JUnit 테스트 176으로 전부 실제 대상이 있다.
- **새 위반을 잡는다** — 와일드카드 import를 쓴 임시 파일을 넣자 실패했고, 지우자 녹색이 됐다.

### 이 규칙들은 임시다 — 리팩토링에서 재검토

"옳은 컨벤션이라서"가 아니라 **"지금 위반이 0건이라서"** 골랐다. 둘은 다르다.

실제로 현재 일관성과 올바른 이름이 어긋나는 자리가 이미 보인다:

| 항목 | 지금 | 재검토할 것 |
|---|---|---|
| 엔티티 변환 동사 | `toNewEntity` 21건, plain `toEntity` 0건 | `New`가 붙은 근거가 없다 |
| `port/in` 이름 | `command/` 21개 중 15개가 방향 없는 `*UseCase` | CLAUDE.md는 `*CommandUseCase`/`*QueryUseCase`를 말한다. **이름 충돌까지 있다** — `MapRelationUseCase`(query) vs `MapRelationCommandUseCase`(command) |
| persistence 어댑터 | `*PersistenceAdapter` 16 / `*JdbcAdapter` 12 / `*StoreAdapter` 6 | 하나로 정해야 한다 |
| 읽기 모델 | `View` 50 / `Row` 2 | `View`로 통일할지 |

그래서 **네이밍 규칙은 리팩토링에서 바뀔 이유가 없는 셋만** 넣었다(`Repository`·`Dto` 금지·
`Test`). `UseCase`·`Adapter`·`View`는 2차로 미뤘다.

각 테스트 파일 KDoc에도 같은 경고를 적어 두었다 — **센서가 근거 없는 권위가 되는 것**,
즉 나중에 "센서가 이러니 이게 맞는 컨벤션이겠지" 하고 잘못된 것을 굳히는 일을 막기 위해서다.

## 순서

```
1차 (완료)   — 이미 일관된 것을 고정. 새 코드의 이탈만 막는다
    ↓
리팩토링     — 올바른 컨벤션을 정하고 코드를 거기에 맞춘다
    ↓
2차          — 정해진 컨벤션을 센서로 고정. 1차 규칙 일부는 이때 수정된다
```

---

# 조사 결과 (근거)

> 2026-09-16, main 623파일 / test 169파일 전수. 위 결정의 근거다.

## 왜 조사부터 했는가

§5는 센서 5개를 표로 제시하는데, 각각이 지정한 도구로 실제로 표현되는지가 문서만으로는
확인되지 않았다. 셋은 실제 코드에 대보니 모양이 달랐다.

**센서의 목적**은 컨벤션을 기계적으로 고정해 LLM이 판단 없이 자연스럽게 맞는 코드를 쓰게 하는
것이다. ArchUnit이 하는 일과 같다 — 규칙이 빌드에 박혀 있으니 어기면 즉시 빨갛고, 그래서 매번
"이게 맞나"를 판단할 필요가 사라진다. 이 목적이 아래 모든 판단의 기준이다.

## 확인된 사실

### Konsist는 쓸 수 있다

`com.lemonappdev:konsist:0.17.3`. 평범한 `testImplementation` + JUnit5 테스트라 별도 소스셋이나
Gradle 플러그인이 필요 없다.

- **임베디드 컴파일러가 2.0.20이다**(우리는 2.3.0). 자체 컴파일러로 소스를 파싱하므로 그보다
  나중 문법은 실패할 수 있다. **전체 소스 793파일 / 3932함수를 파싱해 확인했다 — 문제 없다.**
  다만 Kotlin을 올릴 때 이쪽이 먼저 깨질 수 있다는 뜻이다.
- 실패 메시지를 `additionalMessage`로 직접 쓸 수 있다. §5가 요구한 "어느 결정을 다시 보라"까지
  적는 형식이 그대로 가능하다.

### 매퍼 형태 검사 — 대상을 좁혀야 한다

패키지 전체를 검사했더니 **15건 중 14건이 거짓 양성**이었다. `parseBasicIsoDate`,
`toIntOrZero`, `firstNotBlank` 같은 파싱 헬퍼가 매퍼 파일에 같이 있을 뿐인데 걸렸다. 헬퍼는
경계를 넘지 않으므로 POL-033의 대상이 아니다.

변환 함수 이름(`toDomain`·`toEntity`·`toGql`·`toCommand`·`toView`·`toPayload`·`toRecord`)으로
좁히니 15건 → 1건이 되고, 그 1건은 진짜 위반이었다
(`ConversationEventPayloads.toView`가 클래스 멤버).

**패키지 패턴도 다르다.** §5는 `..adapter.out..mapper..`라고 하는데, GraphQL 입력→Command
매퍼가 `adapter/in/graphql/mapper`에 실재한다. `out`만 보면 그쪽이 통째로 빠진다.

### 단일 구현 인터페이스 검사 — 면제 기준이 이름이 아니다

`..port..` 패키지만 면제하면 `SubscriptionAuthorizer`·`EmailVerificationStatusPort`가 걸리는데,
셋 다 `common/security`에 있는 **공유 커널 포트**다. community가 auth의 토큰 저장소를 직접 알 수
없어 인터페이스를 common에 두고 auth가 구현하는 구조이고, 구현이 하나인 것이 정상이다.

`..port..` + `..common.security..`를 면제하니 1건이 남았고, 그건 들여다볼 값이 있었다 —
`Authorizer`의 KDoc이 *"추후 다른 인가 백엔드로 교체하더라도"*로 자기를 정당화하는데 그 교체는
오지 않았다.

### 루프 안 외부 호출 — Konsist로는 불가능하다

Konsist는 **선언만 본다.** 함수 본문은 원문 텍스트로만 주므로 "반복문 안의 호출"을 구조적으로
볼 수 없다. 유일한 길은 detekt 커스텀 룰이고 비용이 있다:

- 우리 스택(Kotlin 2.3.0 / JDK 25 / Gradle 9.5.1)에 맞는 detekt는 **2.0.0-alpha.6**이다.
  안정판 1.23.8은 Kotlin 2.0.21 기준. 빌드를 막는 도구를 알파에 얹는 선택이 된다.
- 별도 Gradle 서브프로젝트 + 룰 + 프로바이더 + 서비스 파일 + YAML 설정.
- 타입 해석 없이는 수신자 **이름 접미사 매칭**이라 거짓 양성 튜닝이 따라온다.

그리고 **실제 대상이 거의 없다.** 현재 코드 후보 15건 중 12건이 거짓 양성인데 전부 같은
이유다 — `port.findNicknames(page.map { it.counterpartId })`처럼 `.map`으로 모아 **배치 호출
한 번**을 하는, N+1의 정반대 모양이다. 텍스트 센서는 이 둘을 구별하지 못한다.

진짜 "요소마다 한 번"은 3건:

| 위치 | 모양 | 판단 |
|---|---|---|
| `MemberAgreementPersistenceAdapter:45` | `for` 안에서 약관마다 `findByMemberIdAndTermsId` | 진짜 N+1. 약관 수만큼이라 상한은 작다 |
| `MemberPersistenceAdapter:81` | `agreements.forEach { save(...) }` | 같은 성격 |
| `MessageRetentionService:55` | 오브젝트 키마다 `objectStoragePort.delete` | 의도된 것. 스토리지 삭제에 배치 API가 없다 |

### 포트 호출 횟수 상한 — 이미 하고 있다

MockK `verify(exactly = 1)` 자체는 이미 쓰고 있다. SPEC-0003의 지연 해석 테스트가 그 모양이다
(`{ id title overview }`는 업스트림 1회, `gallery` 선택 시 1회 추가).

다만 **공용 센서로 만들 수는 없다** — 옳은 호출 횟수는 유스케이스마다 달라 전역 규칙으로 쓸
숫자가 없다. 이것은 센서가 아니라 슬라이스마다 쓰는 단위 테스트이고, `CLAUDE.md`가 이미
"Pin it at the service level"로 요구하며 `test-strategy`가 형식을 정해 두었다.

### 스키마 ↔ 요구사항 대조 — 대조할 쪽이 없다

`spec.md`의 FR/AC는 자연어 문장이고 필드 이름이 그 안에 그대로 나온다는 보장이 없다. 지금
스크립트를 만들면 "요구사항 문장에 필드명이 문자열로 들어 있는가"를 검사하게 되는데, 그건
요구사항을 필드명으로 쓰도록 강요하는 것이라 문서 품질을 되레 떨어뜨린다.

같은 일을 `simplify-review`의 **역매핑 표**가 한다(§4) — 추가된 것마다 요구사항 id를 적게 하고
`(none)`이면 초과다.

## 전수 조사 — 이미 일관된 것 vs 깨져 있는 것

1차 센서를 고른 근거다. main 623 / test 169파일 전수(2026-09-16).

### 위반 0건 — 1차로 켤 수 있는 것

| 컨벤션 | 현재 |
|---|---|
| 와일드카드 import 없음 | 792파일 중 0 |
| `application`이 JPA·Hibernate·QueryDSL을 import 안 함 | 0. Spring도 `@Service`·`@Transactional` 등 4개 심볼뿐 |
| `domain`이 `common/`을 import 안 함 | 94파일 중 0 |
| 리포지토리가 `Repository`로 끝남 | 46/46 |
| `port/in`에 `handle()`·`execute()` 없음 | 139개 함수 전부 의미 있는 이름 |
| `lateinit var` 없음 | 0 |
| `domain`·리졸버에 `@Transactional` 없음 | 0 |
| `Dto` 접미사 없음 | 0 (읽기 모델의 실제 단어는 `View` 50개) |
| out 어댑터가 다른 어댑터를 주입 안 함 | 0 |
| 테스트가 `*Test` | 167/169 (나머지 2개는 Stub) |

**이 중 센서가 된 것은 7개다.** 나머지 셋은 검사할 수단이 마땅치 않아 뺐다:

- **`lateinit var` 없음** — 바이트코드에서 non-final 필드일 뿐이라 ArchUnit이 구별하지 못한다.
- **필드 주입 없음** — 같은 이유로 잡을 수 없다. 이 저장소는
  `-Xannotation-default-target=param-property`를 쓰므로 **생성자 파라미터의 애노테이션이 백킹
  필드에도 붙는다.** 그래서 ArchUnit의 "필드에 `@Autowired`"는 생성자 주입과 구별되지 않는다 —
  실제로 규칙을 켜자 `FcmPushSender`의 정상적인 생성자 주입(`@Autowired(required = false)`,
  선택적 빈)이 걸렸다.
- **`port/in`에 `handle()` 없음** — 금지 목록을 이름으로 박아야 하는데, 그 목록이 규칙의
  경계가 된다. 지금 0건이라 급하지 않고 2차에서 네이밍과 함께 정하는 편이 낫다.

### 깨져 있는 것 — 리팩토링 대상

| 컨벤션 | 현재 | 비고 |
|---|---|---|
| `port/in` 이름에 방향 없음 | `command/` 21개 중 15개가 `*UseCase` | 이름 충돌 2쌍 |
| `toGql`이 mapper 밖 | 28/37이 리졸버 인라인(76%) | POL-033 위반 |
| persistence 어댑터 3갈래 | `*PersistenceAdapter` 16 / `*JdbcAdapter` 12 / `*StoreAdapter` 6 | |
| `JdbcTemplate` 격리 | 27개 중 2개가 이름에 raw SQL 표시 없음 | `RegionMetricQueryAdapter`, `CongestionAreaQueryAdapter` |
| `application/service/` 접미사 | 45/50 | `TokenIssuer`, `PushDispatchStore` 등 5개 |
| `inline fun <reified T>` | 1건 | `TourApiDetailResponse.kt:180`. "Personal style"이 금지한 것 |
| `!!` | main 17곳 | `SpotQaQueryAdapter.kt`에 7곳(41%) |

### 문서와 실제가 다른 것

- **CLAUDE.md Layout에 `alarm` 도메인이 없다.** 실제로는 있고, 문서엔 아직 없는
  `commerce`·`auction`이 적혀 있다.
- `port/out` 하위가 문서엔 3개(`persistence`/`query`/`client`), 실제론 8개
  (`event`·`cache`·`token`·`push`·`security` 추가).
- Layout에 없는 패키지: `application/{policy,exception,event,validation}`,
  `infrastructure/{batch,config,locale,security}`, `adapter/out/storage`,
  `adapter/in/graphql/mapper`, `adapter/out/query/mapper`, `adapter/out/event/mapper`.

## 2차로 미룬 것

- **POL-033 매퍼 형태 검사.** 시험 구현으로 동작을 확인했고(변환 이름 7개로 좁히면 거짓 양성이
  15→1로 준다), 기존 위반이 34건이라 리팩토링과 같이 켠다. 이때 **§5의 패키지 패턴
  `..adapter.out..mapper..`는 못 쓴다** — GraphQL 입력 매퍼가 `adapter/in/graphql/mapper`에
  있어 `..adapter..mapper..`여야 한다.
- **단일 구현 인터페이스 검사.** 센서로 부적합하다고 본다 — 구현이 하나인 게 잘못인지는
  맥락에 달렸고(포트 정상, 공유 커널 정상), 면제 규칙을 두 번 고쳐 1건으로 줄인 것 자체가
  규칙이 아니라 휴리스틱이라는 증거다. 남은 1건(`Authorizer`)은 리뷰어의 판단으로 충분하다.
- **네이밍 재정의** — `UseCase`·`Adapter`·`View`·`toNewEntity`.

## 변경 이력

| 날짜 | 무엇을 | 왜 |
|---|---|---|
| 2026-09-16 | 조사 기록 작성. 센서 2개를 시험 구현해 확인 후 코드는 되돌림 | 무엇을 만들지 정하기 전에 무엇이 가능한지부터 확인해야 했다 |
| 2026-09-16 | 1차 센서 7개를 켬(ArchUnit 6 + Konsist 1). 전체 코드 검사 | 이미 위반 0건인 컨벤션만 골라 리팩토링을 기다리지 않고 고정했다. 매퍼·단일구현은 2차로 미뤘다 |
