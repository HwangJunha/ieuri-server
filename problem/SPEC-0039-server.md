# SPEC-0039 서버 설계

> spec: SPEC-0039 · source: `Specs/SPEC-0039/spec.md` (Obsidian) · 결정 보고서: ieuri-server#164 본문의 링크
> 기준 커밋(리팩토링 전): `82fdd48` (dev, 2026-09-28)

## 근거 요구사항

- 동작 불변: FR-001 · FR-002 · FR-003 · FR-004 · BR-001 · BR-002
- 규칙의 자리: FR-005 · FR-006 · FR-007 (AC-001~AC-007)
- 경계 변환: FR-008 · FR-009
- 센서: FR-010 · FR-011 · FR-012 · FR-013 · FR-014 · FR-015
- 회귀 보호: FR-016
- 품질: QR-001
- 단위 테스트 밖의 확인: VC-001 ~ VC-007
- 제약: 스타일=ktlint·아키텍처=ArchUnit(사용자 결정), 기한 2026-10-02, 여러 브랜치 허용

## 적용 정책

| POL | 무엇을 구속하는가 |
|---|---|
| POL-015 | 실패에도 HTTP 200. VC-001은 HTTP 상태까지 비교한다 |
| POL-016 | 코드 체계와 auth enum/문자열 비대칭 유지. 판정 위치를 옮겨도 `code`·`codeValue`·표면화 위치(`errors[]`/`userErrors[]`)가 같아야 한다(D-03) |
| POL-013 | VC 하네스가 받은 원본 응답(닉네임·이메일 포함)을 저장소에 커밋하지 않는다(D-25) |
| POL-017 | 커서 문자열은 불투명 계약이다. 커서 인코딩 코드를 옮겨도 바이트가 같아야 하며 VC-001이 그대로 비교한다 |
| POL-018 | 상한 초과의 절삭/거절 방식 유지(AC-007). 정책값 판정을 한곳에 모아도 `coerceIn`과 `PAGE_TOO_DEEP`·`INVALID_LIMIT` 거절은 그대로다 |
| POL-019 · POL-055 | 정렬은 계약. VC 하네스는 배열을 정렬해 "안정화"하지 않는다(D-25) |
| POL-020 · POL-033 | 생성 타입·JPA 엔티티는 application·domain에 들어가지 않고, 변환은 `mapper` 패키지에서 한다(D-08·D-09). 주석 규약(POL-033 (1))은 이번에 새로 쓰는 코드에만 적용 |
| POL-035 | 검색어 트림 후 100자 — `maxSearchQueryLength` 판정 위치를 옮겨도 값·순서 동일(D-06) |
| POL-044 | 설정으로 뺀 값은 설정으로 남는다(FR-007, D-07). 반대로 도메인 상수(`Post.TITLE_MAX_LENGTH` 등)를 이번에 설정으로 올리지 않는다 |
| POL-053 | 관리 연산의 이중 방어가 이름 변경 뒤에도 남는지 VC-001 오류 사례(권한 없음)로 확인한다 |
| POL-054 | 파기 판정은 값·의미·정렬·폴백·기본값 기준. VC-001의 비교 기준 그 자체다 |

## 결정

| # | 슬라이스 | 결정 | 근거 | 탈락 대안과 이유 |
|---|---|---|---|---|
| D-01 | S3~S5 | **옮기는 것과 남기는 것을 규칙의 성격으로 가른다.** domain으로: 길이·개수 한도, 허용 값 범위, 상태 전이 가능 여부(FR-005가 열거한 셋). application에 남김: 존재 확인(`*NotFound`), 소유·참여·차단의 **예외 선택**(판정 자체는 이미 `ownedBy`·`hasParticipant`로 domain), 저장소가 필요한 유일성(`AlreadyReported`·`EmailAlreadyInUse`), 자격증명·토큰 재사용, `requireNotNull(saved.id)` 같은 기술적 불변, **설정값으로 조정되는 한도**(D-06) | 저장소·외부 호출 없이 참·거짓이 정해지는 것만 FR-005의 대상이다. 존재·유일성은 정의상 저장소를 봐야 한다. 서비스 52개 중 규칙 판정이 있는 32개(grep: throw·require·check 174줄)를 분류한 결과는 아래 설계 절의 표 | **전부 domain으로**: 존재 확인을 domain에 넣으려면 domain이 포트를 알아야 해 순수성(FR-005 자체)이 깨진다. **서비스에 그대로 두고 상수만 공유**: 지금 상태가 그것이며, 같은 규칙이 서비스 2곳 + domain 2곳에서 판정되는 것이 FR-006 위반의 원인이다 |
| D-02 | S3~S4 | **판정 형태: domain이 규칙을 순수 판정 함수로 소유하고, 위반을 값(규칙별 위반 타입)으로 돌려준다. application은 지금과 같은 시점에 그 함수를 호출하고, 애그리게이트의 생성·변경 메서드도 같은 함수로 가드한다.** 한 필드 한도는 애그리게이트 쪽 판정, 여러 객체에 걸친 규칙은 `domain/specification`, 상태 전이는 애그리게이트 메서드의 결과로 표현 | **판정 순서가 응답을 정한다.** `createPost`는 제목·본문·사진 수를 장소 조회(`SPOT_NOT_FOUND_IN_LOCALE`)와 사진 업로드 확인보다 먼저 본다. 판정을 `Post.create()` 안으로만 옮기면 "긴 제목 + 없는 장소" 요청의 코드가 바뀐다(FR-003 파기). 판정 함수를 앞에서 호출하면 순서가 그대로이고 규칙은 한곳에 있다(FR-006). 선례: `tourism/application/validation/NearbySearchValidation.kt` | **domain이 예외를 던지고 서비스가 잡아 번역**: 판정 시점이 애그리게이트 호출 시점으로 밀려 순서가 깨지고, `IllegalArgumentException`을 넓게 잡으면 무관한 IAE까지 삼킨다. **필드마다 VO**: 애그리게이트 시그니처·엔티티 매퍼·기존 테스트가 함께 흔들리는데 얻는 동작이 없다. **Specification으로 통일**: 한 필드 길이 규칙까지 클래스로 만들면 판정이 애그리게이트와 멀어진다 |
| D-03 | S3~S5 | **오류 응답 동일성은 번역 계층이 책임진다.** `application/validation`의 도메인별 번역 함수가 위반 값 → **기존 예외 클래스 그대로**(같은 `code`·같은 기본 메시지)로 옮긴다. 애그리게이트 가드가 던지는 `IllegalArgumentException`의 메시지는 **현재 문자열을 바이트 그대로 유지**한다. 한도를 문자열에 박아 둔 예외 메시지는 이번에 옮기는 규칙에 한해 domain 상수로 템플릿화한다 — 출력 바이트가 같을 때만 | 도메인 `require` 메시지는 **이미 응답에 나간다**: 세 도메인의 오류 지원(`toCommunityUserErrorOrNull`, auth `toUserErrorOrNull`, `withAlarmUserErrors`)이 IAE를 `INVALID_INPUT` + `e.message`로 바꾼다(FR-003·FR-004, POL-016). 템플릿화는 AC-005에서 메시지만 옛 숫자를 말하는 상태를 막는다 | **예외를 domain으로 옮겨 코드를 domain이 앎**: auth enum(POL-016)이 domain으로 새어 든다. **오류 코드 한곳 통합(enum화)**: 이번 범위 밖이고, 비대칭 유지가 POL-016의 명시 결정이다 |
| D-04 | — | **애그리게이트 루트 구조를 새로 들이지 않는다.** 이미 있는 `domain/aggregate`(20개)에 규칙을 옮겨 담을 뿐, 공통 기반 클래스·애그리게이트 병합·애그리게이트 내 이벤트 수집은 하지 않는다 | spec은 도입을 요구도 금지도 하지 않는다. 현재 코드가 이미 애그리게이트 단위(`create`·`restore`·Boolean 결과 상태 변경)로 짜여 있어, 부족한 것은 구조가 아니라 **규칙이 서비스에 있다**는 점이다 | **splearn식(JPA 엔티티 = 도메인)**: apps.md·POL-033과 충돌. **애그리게이트 병합**: 로딩 단위·트랜잭션 모양이 바뀌어 조회 성능이 달라진다(SPEC-0038 목표 유지) — 동작 불변 리팩토링에서 가장 위험한 변경이다 |
| D-05 | S3 | **`createPost`와 `editPost`의 태그 개수 초과 응답 비대칭을 유지한다.** 생성은 도메인 `require`가 `INVALID_INPUT`("태그는 최대 5개입니다."), 수정은 `TOO_MANY_TAGS`. 판정은 한 함수(D-02)로, **번역만 경로별로** 지금과 같게 둔다. AC-004는 "같은 도메인 판정이 같은 위반을 돌려준다"로 domain 단위에서 검증한다 | BR-001·FR-003을 함께 만족하는 길은 판정 일원화 + 번역 보존뿐이다. 결함은 별도 spec 대상으로 보고한다 | **둘 다 `TOO_MANY_TAGS`로 통일**: 생성 경로 응답 코드가 바뀐다(POL-054) |
| D-06 | S5 | **설정값으로 조정되는 한도는 application에서 판정하되, 규칙마다 판정 함수 하나로 모은다**(`application/validation`, 정책값을 인자로 받는 순수 함수). 반경 3곳, 좌표 번역 3곳, bbox 면적 2곳을 하나씩으로 | FR-006 + FR-007. 값의 출처가 운영 튜닝(POL-044)이라 domain 불변식이 아니다 | **한도를 domain VO에 인자로 넘김**: "설정이 domain까지 흐른다"는 새 모양을 만들고 얻는 것은 같다. **그대로 둠**: FR-006 위반이 남는다 |
| D-07 | S2 | **`application/policy`의 10개 묶음은 이름·위치·값을 그대로 둔다.** 센서(`application.policy`의 클래스는 `Policy`로 끝난다)는 **B1에서 보류** — `RecommendationPolicy.kt`의 `data class EmbeddingSpec`(정책의 하위 묶음)이 어긋나 "현재 0건" 전제가 틀렸다. 개명할지 규칙을 좁힐지 사용자 결정 대기 | 전부 프레임워크 무의존 data class이고 인프라 `@ConfigurationProperties` → `@Bean`에서 조립된다. FR-007은 모양 변경을 요구하지 않는다 | **`*Settings`로 개명 / `*Properties`와 병합**: 병합하면 application이 Spring 바인딩 타입을 알게 된다. **domain으로 이동**: POL-044 취지와 반대 |
| D-08 | S2 | **FR-008 센서를 명시 규칙으로 둔다**: `..application..`·`..domain..`은 `org.around.ieuri.common.graphql.generated..`와 `..persistence.entity..`에 의존하지 않는다. 현재 위반 0건 | 기존 규칙은 `com.netflix..`·`graphql..`만 본다. **생성 타입은 `common.graphql.generated`에 있어 지금 무방비다.** 실패 메시지가 FR-008을 가리켜야 한다(FR-015) | **기존 계층 규칙으로 충분하다고 보고 생략**: 생성 타입 경로가 실제로 빠져 있다 |
| D-09 | S6 | **변환 위치 규칙(FR-009)**: application·infrastructure에서 이름이 `to[A-Z]…`이거나 `from`인 메서드는 **`..mapper..` 패키지의 최상위 함수에만** 있다. 클래스·`object`·companion 멤버 변환 금지. `toString`은 제외. application 쪽 변환(도메인 → View 등)은 `application/mapper/`로 모은다. domain은 대상 아님 | 전수: infrastructure 멤버 변환 63건(리졸버 30 등) + mapper 밖 최상위 1, application 14건. 규칙을 "클래스 멤버인가"로 쓰는 것은 1차 조사(`HARNESS-server-sensors.md`)의 교훈. 패키지 패턴은 `..mapper..` | **생성 타입 생성자 호출 금지**: 186곳, payload 조립까지 걸린다(D-10). **Konsist로 작성**: 멤버 vs 최상위 함수는 바이트코드로 구별된다(`*Kt`) |
| D-10 | S6 | **리졸버의 payload 조립은 리졸버에 남긴다.** 변환 규칙의 대상은 "경계 값 ↔ 도메인/View"로 한정 | 성공/실패 봉투를 채우는 것은 요청 흐름의 일부다 | **payload까지 mapper로**: D-09의 186곳 문제 |
| D-11 | S6 | **리졸버가 도메인 VO를 조립하는 2곳**(`RegionRecommendationResolver`, `MapQueryResolver`)은 **오류 응답이 동일함을 확인할 수 있으면** `application/validation`으로 옮기고, 아니면 in/graphql `mapper`로만 옮긴다 | "인바운드 어댑터는 도메인 VO를 조립하지 않는다"는 합의. FR-003이 우선이다 | **무조건 application으로**: 실패 경로가 `errors[]` ↔ `userErrors[]`로 바뀔 위험 |
| D-12 | S7 | **`port.in.command`는 `*CommandUseCase`, `port.in.query`는 `*QueryUseCase`.** 48개 중 25개 개명, 파일명도 맞춘다 | CLAUDE.md Layout이 이미 이 이름을 규정. 방향 없는 이름은 과거 이름 충돌을 낳았다 | **`*UseCase`로 통일**: 개명 수는 비슷하고 import만 보고 방향을 모른다 |
| D-13 | S7 | **`application/service`의 최상위 클래스는 전부 `*Service`.** 5개 개명(`TokenIssuer`, `ProviderEmailUniqueness`, `ProviderTokenCollector`, `SpotQaPhotoKeyVerifier`, `PushDispatchStore`) | 한 패키지 한 규칙 | **협력 객체용 패키지 신설**: 규칙 없는 패키지가 하나 더 생길 뿐이다 |
| D-14 | S7 | **`adapter.out.persistence`는 `*PersistenceAdapter`, `adapter.out.query`는 `*QueryAdapter`, `JdbcTemplate`·`NamedParameterJdbcTemplate`에 의존하는 클래스는 `*JdbcAdapter`**(역방향도). config의 DataSource 설정은 대상 밖. 20개 개명 | 현재 persistence 56개가 네 갈래, JdbcTemplate 어댑터 25개 중 `Jdbc` 표시 19 | **`Spatial`도 raw SQL 표시로 허용**: 같은 사실에 단어가 둘. **tourism의 `Store` 유지**: FR-011 위반 그 자체 |
| D-15 | S7 | **`application/query`의 타입은 `*View` 또는 `*Query`로 끝난다.** 그 밖의 11개는 개명하거나 제자리로 옮긴다 | "읽기 모델"이라는 한 역할에 단어 하나 | **자유 명명 유지**: 센서가 검사할 것이 없다 |
| D-16 | — | **`toNewEntity`는 유지한다.** 규칙 문서에 이유를 적는다 | 갱신은 관리 엔티티에 쓰고 더티체킹에 맡긴다. 새 엔티티를 만드는 변환은 생성 경로에서만 쓴다 — `New`가 그 구분을 이름으로 강제한다 | **`toEntity`로 개명**: 갱신 경로에서 새 엔티티를 만들어 `save()`하는 오용을 부른다 |
| D-17 | S2 | **`port.out`의 인터페이스 중 infrastructure가 구현하는 것은 `*Port`.** 116/116 | 비용 0으로 FR-011을 넓힌다. 대상을 "infrastructure가 구현하는"으로 두는 것은 포트의 정의 그대로다 — 같은 패키지의 결과 타입 `sealed interface SnsRevokeResult`가 첫 구현에서 거짓 양성으로 걸렸다 | **"연산을 선언한 인터페이스"**: 결과 타입에 프로퍼티가 생기면 getter가 메서드로 잡혀 다시 거짓 양성이 된다(리뷰 지적). **sealed 제외**: Kotlin sealed 여부는 바이트코드로 안정적으로 보이지 않는다 |
| D-18 | S7 | **패키지·이름 규칙 문서(FR-011)는 CLAUDE.md Naming 절을 표로 확장해 둔다.** 1차 센서 KDoc의 "임시 규칙" 경고도 갱신 | CLAUDE.md가 규칙의 권위 문서다. 별도 문서는 드리프트한다 | **`docs/conventions.md` 신설**: 두 사본이 어긋난다 |
| D-19 | — | **사용자 정의 스테레오타입 애노테이션은 들이지 않는다** | 패키지 + 접미사로 역할이 이미 결정된다 | **도입**: 같은 사실의 세 번째 표시가 된다 |
| D-20 | S1 | **ktlint는 kotlinter 5.7.0(ktlint 1.8.0)으로 붙인다.** **CI를 `./gradlew test` → `./gradlew check`로 바꾼다.** CI는 dev→master PR(test.yml)과 머지 후 빌드(build.yml)에서만 돌므로, feature→dev PR의 스타일 위반은 **머지 후 build.yml에서 드러나 dev 배포를 막는다** — 로컬 `check`가 1차 게이트다(PR 단계 lint 게이트 여부는 사용자 결정 대기) | 현재 CI는 `test`만 돌려 `check`에 걸린 린트가 CI에서 실행되지 않는다. 인프라 규칙: 관용(플러그인)이 있으면 손으로 만들지 않는다. kotlinter를 먼저 시험해 Gradle 9.5.1·설정 캐시·Kotlin 2.3.0에서 통과했고(위반 수가 CLI 실측과 일치), 생성 코드 제외도 태스크 설정으로 된다 — 기준(먼저 통과하는 쪽)을 만족해 ktlint-gradle은 시험하지 않았다 | **`Exec`로 CLI 호출**: 수제 통합. **JUnit 안에서 룰 엔진 호출**: 포맷 태스크를 따로 만들어야 한다 |
| D-21 | S1 | **ktlint 구성: `ktlint_code_style = intellij_idea`, `package-name` 규칙은 끈다**, 자동 생성 코드는 경로 제외. 기준선 0건은 **일괄 포맷 커밋 → 수작업 → 린트 활성화** 순서 | 실측(ktlint 1.8.0): intellij_idea 1364건 중 1226 자동 수정, 잔여 138. 그중 **98건이 `port.in`·`adapter.in`의 `` `in` `` 패키지**다 — CLAUDE.md가 정한 레이아웃과 충돌하는 규칙이지 기존 부채가 아니므로 규칙 비활성화이지 FR-014의 억제가 아니다(사용자 승인 2026-09-28). ktlint_official은 7194건·24.6k줄 변경 | **ktlint_official**: diff가 약 4배. **`in` 패키지 개명(`inbound`)**: 수백 파일의 import가 바뀌고 CLAUDE.md 레이아웃을 뒤집는다. **baseline.xml**: FR-014 금지 |
| D-22 | S2·S6·S7 | **실패 메시지(FR-015)**: ArchUnit은 `because("…")`에 무엇을 고칠지 쓴다 — 실패 헤더 `Rule '<술어>, because <이유>' was violated`에 규칙과 이유가 함께 찍히고 위반 줄이 위치(`(File.kt:line)`)를 준다. Konsist는 `additionalMessage`. ktlint는 기본 출력(`파일:줄:열 [규칙 id]`) | B1 탐침으로 실제 출력을 확인했다(D-08·D-17). **초안의 `as(…)`를 뒤집는다** — `as`는 술어 설명을 통째로 덮어써 무엇을 검사했는지가 사라진다(리뷰 지적) | **`as("…")`**: 위 이유. **기본 규칙 설명만**: 무엇을 할지 말하지 않는다 |
| D-23 | S8 | **FR-013 도구·한도는 승인 대기(Q01).** 실측: detekt 2.0.0-alpha.6이 Gradle 9.5.1/Kotlin 2.3/JDK 25에서 동작, 기본 한도(함수 60 · 클래스 600 · CC 14 초과)로 main 8 · test 2건. detekt 1.23.8 플러그인은 JDK 25 jvmTarget에서 실패. 사용자가 detekt 외 업계 대안 조사를 요청(2026-09-28) | spec 제약: ktlint는 길이·복잡도를 검사하지 않는다 | 조사 결과와 함께 이 행을 확정한다 |
| D-24 | S1·S2 | **센서 실행 비용(QR-001)**: 상한은 **도입 전 대비 +25%**(사용자 승인 2026-09-28). 측정은 같은 머신에서 `./gradlew clean check --no-build-cache` 3회 중앙값. 새 ArchUnit 규칙은 클래스 임포트를 공유한다 | 기준(단독 1회): `clean test` 93초 · `clean build` 111초. 스위트 48초 중 아키텍처 테스트 약 39초 — 임포트 공유가 가장 큰 절감 여지다 | **측정 없이 상한 제안**: 근거 없는 숫자 |
| D-25 | S0·S9 | **VC-001 하네스: 두 서버 교차(A/B) 실행.** 기준 커밋 서버(A)와 리팩토링 후 서버(B)를 **같은 dev DB·Valkey**에 붙여 로컬 포트 두 곳(8080·8888 제외)에 띄우고, 요청 하나를 A·B에 연달아 보내 HTTP 상태 + 본문을 비교한다. 요청 집합은 99개 연산 × (성공 ≥1, 오류 ≥1). 변경 연산은 A용·B용 계정을 따로 둔다. 정규화는 호출마다 달라지는 값만, 목록을 결과에 남기고 **배열은 정렬하지 않는다**(POL-019·055). 산출물: `problem/SPEC-0039-vc/`. **원본 응답은 커밋하지 않는다**(POL-013) | 외부 실시간 데이터·시계·캐시가 결과를 흔든다 — 교차 실행이 그 차이를 최소화한다. CLAUDE.md: 인프라 테스트를 스위트에 넣지 않는다. dev DB에 테스트 데이터가 생기는 것은 사용자 동의(2026-09-28) | **스위트 안 DGS 골든 테스트**: CLAUDE.md가 금지하는 컨텍스트 부팅 테스트. **기준 응답을 먼저 캡처해 두고 나중에 비교**: 실시간 데이터·시각 차이가 거짓 양성으로 쏟아진다 |
| D-26 | S9 | **VC-002: `git diff 82fdd48..HEAD -- src/main/resources/schema/`가 0줄**임을 기록한다 | 스키마 우선 — 파일이 곧 스키마다. FR-002보다 엄격하다 | **인트로스펙션 비교**: 같은 것을 확인하는 데 서버 기동이 필요하다 |
| D-27 | S0·S9 | **VC-007: 기준 커밋과 최종 커밋의 JUnit XML 테스트 목록을 대조해 `problem/SPEC-0039-vc/test-mapping.md`에 남긴다.** 사라지거나 이름이 바뀐 항목만 수작업으로 대응을 적는다 | 기계적으로 가능한 부분은 기계로 한다. 기준: 183 suite · 1664 테스트 | **수작업 전수**: 누락이 난다 |
| D-28 | 전체 | **작업 중에는 컴파일만, 테스트는 각 브랜치 PR 직전에 `check` 전체.** 본문을 **그대로 옮긴** 변환은 필드별 변환 테스트를 새로 요구하지 않는다(VC-001 A/B가 보호). 옮기면서 **본문을 고친** 변환은 필드별 테스트를 쓴다 | CLAUDE.md "작성 중 테스트를 돌리지 않는다". 변환 테스트 규칙은 "슬라이스가 추가하거나 바꾼 것"에 걸리는데, 잘라 붙이기는 필드 대응을 바꾸지 않는다(사용자 승인 2026-09-28) | **옮긴 변환 77건 전부에 필드별 테스트**: 기한 안에 불가 |
| D-29 | — | **브랜치 4개 순차 + 검증 1회.** ktlint 일괄 포맷은 맨 먼저 단독 브랜치로 머지. 각 센서는 **자기가 검사할 코드를 고친 커밋 뒤**에 켠다 | FR-014(도입 시점 0건). 포맷 diff(약 6.8k줄)와 리팩토링 diff가 섞이면 리뷰가 불가능하다 | **한 브랜치**: 포맷 diff 속에서 동작 변경을 찾아야 한다. **병렬 브랜치**: 같은 서비스 파일을 건드려 충돌한다 |

## 부족한 정보

없음.

**승인 대기 (spec의 `[TBD]`)**

| 항목 | 막는 것 | 상태 |
|---|---|---|
| Q01 — FR-013 도구와 한도 | S8, VC-004의 한도 부분 | detekt 외 업계 대안 조사 중(사용자 요청 2026-09-28) |
| Q02 — QR-001 상한 | — | **+25% 승인(2026-09-28)** |

## 규모 가정

spec이 `미정`으로 둔 것 — 실측값은 spec.md에 되쓴다.

| 항목 | 값 (2026-09-28 실측) | 이 숫자로 내린 판단 |
|---|---|---|
| GraphQL 연산 수 | Query 35 · Mutation 62 · Subscription 2 = **99**. Mutation은 전부 `userErrors` payload | VC-001 요청 집합 ≥ 198. 구독 때문에 하네스에 WebSocket 클라이언트가 필요하다(D-25) |
| 현재 빌드·테스트 시간 | `clean test` 93초 · `clean build` 111초(단독 1회), 1664 테스트. **기준 `clean check --no-build-cache` 3회: 89·87·104초, 중앙값 89초** → QR-001 상한 약 111초. **B1 후 3회: 73·105·107초, 중앙값 105초(+18%, 상한 안)** — 아키텍처 테스트 약 39초 → 6.5초로 줄었으나 ktlint 태스크가 더해졌다. 같은 머신에서도 편차가 커(73~107초) S9에서 다시 잰다 | Q02 상한의 기준(D-24) |
| ktlint 초기 위반 | intellij_idea 1364(자동 수정 1226, 잔여 138: package-name 98 · kdoc 30 · filename 6 · property-naming 4) / official 7194 | D-21 |
| 함수·클래스 길이·복잡도 분포 | 함수 LOC main p99 35 · max 99, CC main p99 8 · max 19, 클래스 main max 520줄 / test max 1318줄 | Q01(D-23) |

grep으로 확인한 수(worktree `82fdd48`):

- 규칙 판정이 있는 서비스 32개, throw·require 174줄
- 변환 재배치 대상: infrastructure 63 + application 14 = 77 (D-09)
- 개명 대상: port/in 25, service 5, 영속 어댑터 20, application/query 11 (D-12~D-15)
- 이미 0건인 규칙: application·domain → 생성 타입 0, port/out `*Port` 116/116

## 슬라이스

| # | 브랜치 | 인수 조건 / 확인 항목 | 내용 | 상태 |
|---|---|---|---|---|
| S0 | B1 | VC-001·VC-007 준비 | 이 문서, 기준 커밋 테스트 목록 추출 | 완료 |
| S1 | B1 | FR-012 · FR-014 · VC-004(스타일) · VC-005 | ktlint 플러그인(D-20) → 일괄 포맷 커밋 → 수작업 수정 → 린트 활성화, CI `check` 전환 | 완료. VC-004(스타일) 탐침 확인 |
| S2 | B1 | FR-008 · FR-010 · FR-015 | 이미 0건인 센서: 생성 타입·엔티티 누출 금지(D-08), `*Port`(D-17). 임포트 공유(D-24) | 완료. VC-003 탐침 확인. `*Policy`(D-07)는 보류 |
| S3 | B2 | AC-001 · AC-002 · AC-004 · AC-005 | community 규칙을 domain 판정 + `application/validation` 번역으로(D-02·D-03·D-05) | |
| S4 | B2 | AC-003 | 상태 전이 규칙을 애그리게이트 결과로 | |
| S5 | B2 | AC-006 · AC-007 | 설정 한도 판정 한곳으로(D-06), 값 불변 확인 | |
| S6 | B3 | FR-009 · FR-010 · FR-015 | 변환 77건 이동(D-09~D-11) → 변환 위치 센서 | |
| S7 | B3 | FR-011 · FR-010 | 개명(D-12~D-15) + CLAUDE.md Naming 절(D-18) → 이름·패키지 센서 | |
| S8 | B4 | FR-013 · VC-004(한도) | Q01 승인 후 도구·한도 적용, outlier 분할 | Q01 대기 |
| S9 | (머지 후 dev) | VC-001 ~ VC-007 · QR-001 | A/B 실행, 스키마 diff, 테스트 대응표, 센서별 위반 삽입·제거 확인, 시간 측정. 결과를 spec 변경 이력에 | |

브랜치: B1 = `feature/SPEC-0039-hexagonal-sensors`, B2 = `feature/SPEC-0039-domain-rules`, B3 = `feature/SPEC-0039-boundary-naming`, B4 = `feature/SPEC-0039-complexity-limit`.

## 이번 범위 아님

- SPEC-0020 빈 카테고리 거절 메시지의 내부 식별자 노출 수정(FR-004가 명시적으로 제외)
- `createPost` 태그 개수 초과 코드 통일(D-05) — 별도 spec
- 오류 코드 통합·auth `ErrorCode` enum 확장·tourism/community enum화(POL-016)
- 리졸버 payload 조립을 mapper로 이동(D-10), 생성 타입 생성자 호출 금지 센서
- 도메인 상수 한도를 설정값으로 승격
- 애그리게이트 병합·공통 기반 클래스·애그리게이트 내 이벤트 수집(D-04), 필드별 VO 신설(D-02)
- 사용자 정의 스테레오타입 애노테이션(D-19)
- `!!` 제거, `inline fun <reified T>` 1건, `MemberAgreementPersistenceAdapter`의 루프 조회 — 이 spec의 FR이 아니다
- `out/event` 리스너를 `adapter/in/event`로 옮기는 구조 결정, 리졸버의 감사 이벤트 발행 정리
- 리졸버·DataLoader·in/graphql 보조 파일의 이름 규칙
- 단일 구현 인터페이스 센서, 루프 안 외부 호출 센서(1차 조사에서 부적합 판정)
- 기존 KDoc을 POL-033 (1)에 맞춰 줄이는 작업
- `LongParameterList` 류의 파라미터 수 한도(FR-013이 요구하지 않음)
- SPEC-0038 추천 조회 성능 재측정(spec이 요구하지 않음)
- ktlint 커스텀 룰

## 설계

**도메인 / 애그리게이트 경계.** 경계는 바꾸지 않는다(D-04). 바뀌는 것은 규칙의 자리다. 대표 사례:

- `Post` 제목·본문 길이는 **네 곳**에서 판정된다 — `PostCommandService.createPost`, `editPost`, `Post.create`, `Post.editBy`.
- 답글 본문 한도는 서비스가 `SpotAnswer.BODY_MAX_LENGTH`로, domain은 `SpotAnswerReply.BODY_MAX_LENGTH`로 본다. 지금은 둘 다 500이라 드러나지 않을 뿐, 답글 한도를 바꾸면 서비스가 옛 값으로 거절한다 — AC-005가 잡으려는 결함이다.
- 반경 판정 3벌, bbox 면적 판정 2벌(D-06).

서비스 32개의 판정 분류(대략치, S3 착수 시 확정):

| 분류 | 대표 위치 | 대략 | 처리 |
|---|---|---|---|
| 도메인 불변식 재판정 | `PostCommandService`, `CommentCommandService`, `SpotQuestionCommandService`, `SpotAnswerCommandService`, `MessageReportCommandService`, `ReportCommandService`, `PostImageUploadService`, `BlockCommandService`·`ConversationCommandService`·`MapRelationService`(자기 자신), `RoleService`(마지막 관리 역할), `EmailChangeService`·`MemberRegistrationService`·`AuthTokenService`(회원 상태) | 약 35곳 / 14개 서비스 | domain 판정 + validation 번역(D-02·D-03) |
| 이미 domain이 판정하는 VO 실패의 번역 | `PostTag.parseOrNull`, `Coordinates`, `Nickname`, `Email`, `PhotoObjectKey` | 약 12 | 번역만 `application/validation`로 |
| 설정값 한도 | 반경, bbox, 검색어 길이, 줌, 구역 수, 이미지 MIME·크기, 재발송 쿨다운 | 약 15 | application 한곳(D-06) |
| 존재 확인 | `*NotFound` | 약 40 | 서비스에 남김 |
| 소유·참여·차단의 예외 선택 | `Not*Owner`, `NotConversationParticipant`, `Blocked`, `Forbidden` | 약 15 | 판정은 domain(이미), throw는 서비스 |
| 저장소가 필요한 유일성·자격증명 | `AlreadyReported`, `EmailAlreadyInUse`, `MemberAlreadyRegistered`, `InvalidCredentials`, `TokenReuseDetected` | 약 10 | 서비스 |
| 기술적 불변 | `requireNotNull(saved.id)` 등 | 약 12 | 서비스 |

**커맨드·쿼리 분리.** 바꾸지 않는다. 쿼리 쪽 변환이 서비스 멤버에서 `application/mapper`로 나갈 뿐이다(D-09).

**영속성 영향.** 없다. 테이블·컬럼·마이그레이션 변경 없음(BR-002). 주의할 곳:

- **클래스 개명은 빈 이름을 바꾼다.** `@Qualifier`·조건부 빈이 참조하면 함께 바꾼다.
- **Valkey에 JSON으로 쓰이는 타입(`CachedSpotDetail` 계열, `CachedSeoulOpenDataPayload` 계열, 대화 이벤트 페이로드)은 프로퍼티명을 바꾸지 않는다.** 필드명이 저장값 그 자체이고, 롤링 배포 중 구·신 파드가 같은 캐시와 채널을 공유한다.
- `PushDispatchStore`는 `REQUIRES_NEW` 전파를 `LayeredArchitectureTest`가 강제한다 — 개명(D-13) 시 규칙이 새 이름을 계속 잡는지 확인한다.
- 자동 생성 코드 제외: ArchUnit은 QueryDSL Q클래스를 구조로 제외, DGS 생성 타입은 `common` 패키지라 새 규칙 범위 밖. ktlint는 경로로 제외(D-21).

**센서 목록(최종).** 기존 규칙은 유지한다. 새로 켜는 것: 생성 타입·엔티티 누출(D-08), `*Port`(D-17), `*Policy`(D-07, 보류 중), 변환 위치(D-09), 이름 규칙(D-12~D-15), ktlint(D-20·D-21), 길이·복잡도(D-23, Q01 후).

## 변경 이력

| 날짜 | 무엇을 | 왜 |
|---|---|---|
| 2026-09-28 | 초안 작성(D-01~D-29), 실측값 기록 | SPEC-0039 착수 전 설계 기록 |
| 2026-09-28 | D-21(intellij_idea·package-name 끔), D-24(Q02 +25%), D-25(dev DB 사용), D-28(그대로 옮긴 변환 테스트 면제) 사용자 승인 | 설계 합의 |
| 2026-09-28 | B1 완료 기록: D-07 보류, D-17 대상 기준, D-20 kotlinter 선택 근거·CI 경로, D-22 `because`로 뒤집음, 기준 시간 | 구현과 reviewer 지적 반영 |
