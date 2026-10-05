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
- 제약: 스타일=ktlint·아키텍처=ArchUnit(사용자 결정), 여러 브랜치 허용. 기한은 범위 확장(2026-09-30)으로 다시 정한다 — 부족한 정보 Q03

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
| POL-054 | 파기 판정은 값·의미·정렬·폴백·기본값 기준. VC-001의 비교 기준 그 자체다. D-38(다중 위반 우선순위)·D-43(결함 수정)은 의도한 차이로 VC-001 결과에 따로 적는다 |
| POL-041 | 자동 숨김은 서로 다른 신고자 5명 — 카운터 원자화(D-40)가 신고 집계에도 적용되어야 한다 |
| POL-049 · POL-050 | 신고된 메시지 열람 vs 6개월 파기가 충돌한다 — Q04 |
| POL-056 | 풀을 나눌 때 매니페스트의 풀 크기·환경변수를 함께 바꾼다(D-33) |

## 결정

| # | 슬라이스 | 결정 | 근거 | 탈락 대안과 이유 |
|---|---|---|---|---|
| D-01 | S3~S5 | **[D-38이 뒤집음 — 2026-09-30]** **옮기는 것과 남기는 것을 규칙의 성격으로 가른다.** domain으로: 길이·개수 한도, 허용 값 범위, 상태 전이 가능 여부(FR-005가 열거한 셋). application에 남김: 존재 확인(`*NotFound`), 소유·참여·차단의 **예외 선택**(판정 자체는 이미 `ownedBy`·`hasParticipant`로 domain), 저장소가 필요한 유일성(`AlreadyReported`·`EmailAlreadyInUse`), 자격증명·토큰 재사용, `requireNotNull(saved.id)` 같은 기술적 불변, **설정값으로 조정되는 한도**(D-06) | 저장소·외부 호출 없이 참·거짓이 정해지는 것만 FR-005의 대상이다. 존재·유일성은 정의상 저장소를 봐야 한다. 서비스 52개 중 규칙 판정이 있는 32개(grep: throw·require·check 174줄)를 분류한 결과는 아래 설계 절의 표 | **전부 domain으로**: 존재 확인을 domain에 넣으려면 domain이 포트를 알아야 해 순수성(FR-005 자체)이 깨진다. **서비스에 그대로 두고 상수만 공유**: 지금 상태가 그것이며, 같은 규칙이 서비스 2곳 + domain 2곳에서 판정되는 것이 FR-006 위반의 원인이다 |
| D-02 | S3~S4 | **[D-38이 뒤집음 — 2026-09-30]** **판정 형태: domain이 규칙을 순수 판정 함수로 소유하고, 위반을 값(규칙별 위반 타입)으로 돌려준다. application은 지금과 같은 시점에 그 함수를 호출하고, 애그리게이트의 생성·변경 메서드도 같은 함수로 가드한다.** 한 필드 한도는 애그리게이트 쪽 판정, 여러 객체에 걸친 규칙은 `domain/specification`, 상태 전이는 애그리게이트 메서드의 결과로 표현 | **판정 순서가 응답을 정한다.** `createPost`는 제목·본문·사진 수를 장소 조회(`SPOT_NOT_FOUND_IN_LOCALE`)와 사진 업로드 확인보다 먼저 본다. 판정을 `Post.create()` 안으로만 옮기면 "긴 제목 + 없는 장소" 요청의 코드가 바뀐다(FR-003 파기). 판정 함수를 앞에서 호출하면 순서가 그대로이고 규칙은 한곳에 있다(FR-006). 선례: `tourism/application/validation/NearbySearchValidation.kt` | **domain이 예외를 던지고 서비스가 잡아 번역**: 판정 시점이 애그리게이트 호출 시점으로 밀려 순서가 깨지고, `IllegalArgumentException`을 넓게 잡으면 무관한 IAE까지 삼킨다. **필드마다 VO**: 애그리게이트 시그니처·엔티티 매퍼·기존 테스트가 함께 흔들리는데 얻는 동작이 없다. **Specification으로 통일**: 한 필드 길이 규칙까지 클래스로 만들면 판정이 애그리게이트와 멀어진다 |
| D-03 | S3~S5 | **[D-38이 뒤집음 — 2026-09-30]** **오류 응답 동일성은 번역 계층이 책임진다.** `application/validation`의 도메인별 번역 함수가 위반 값 → **기존 예외 클래스 그대로**(같은 `code`·같은 기본 메시지)로 옮긴다. 애그리게이트 가드가 던지는 `IllegalArgumentException`의 메시지는 **현재 문자열을 바이트 그대로 유지**한다. 한도를 문자열에 박아 둔 예외 메시지는 이번에 옮기는 규칙에 한해 domain 상수로 템플릿화한다 — 출력 바이트가 같을 때만 | 도메인 `require` 메시지는 **이미 응답에 나간다**: 세 도메인의 오류 지원(`toCommunityUserErrorOrNull`, auth `toUserErrorOrNull`, `withAlarmUserErrors`)이 IAE를 `INVALID_INPUT` + `e.message`로 바꾼다(FR-003·FR-004, POL-016). 템플릿화는 AC-005에서 메시지만 옛 숫자를 말하는 상태를 막는다 | **예외를 domain으로 옮겨 코드를 domain이 앎**: auth enum(POL-016)이 domain으로 새어 든다. **오류 코드 한곳 통합(enum화)**: 이번 범위 밖이고, 비대칭 유지가 POL-016의 명시 결정이다 |
| D-04 | — | **[D-40이 뒤집음 — 2026-09-30]** **애그리게이트 루트 구조를 새로 들이지 않는다.** 이미 있는 `domain/aggregate`(20개)에 규칙을 옮겨 담을 뿐, 공통 기반 클래스·애그리게이트 병합·애그리게이트 내 이벤트 수집은 하지 않는다 | spec은 도입을 요구도 금지도 하지 않는다. 현재 코드가 이미 애그리게이트 단위(`create`·`restore`·Boolean 결과 상태 변경)로 짜여 있어, 부족한 것은 구조가 아니라 **규칙이 서비스에 있다**는 점이다 | **splearn식(JPA 엔티티 = 도메인)**: apps.md·POL-033과 충돌. **애그리게이트 병합**: 로딩 단위·트랜잭션 모양이 바뀌어 조회 성능이 달라진다(SPEC-0038 목표 유지) — 동작 불변 리팩토링에서 가장 위험한 변경이다 |
| D-05 | S3 | **`createPost`와 `editPost`의 태그 개수 초과 응답 비대칭을 유지한다.** 생성은 도메인 `require`가 `INVALID_INPUT`("태그는 최대 5개입니다."), 수정은 `TOO_MANY_TAGS`. 판정은 한 함수(D-02)로, **번역만 경로별로** 지금과 같게 둔다. AC-004는 "같은 도메인 판정이 같은 위반을 돌려준다"로 domain 단위에서 검증한다 | BR-001·FR-003을 함께 만족하는 길은 판정 일원화 + 번역 보존뿐이다. 결함은 별도 spec 대상으로 보고한다 | **둘 다 `TOO_MANY_TAGS`로 통일**: 생성 경로 응답 코드가 바뀐다(POL-054) |
| D-06 | S5 | **`application/policy`의 숫자 한도는 "한도 값 + 판정"을 함께 가진 규칙 객체로 바꾼다**(예: 반경 상한 판정, 페이지 크기 절삭). 규칙 객체는 infrastructure config가 설정값으로 만들어 주입한다. 반경 3곳·좌표 번역 3곳·bbox 면적 2곳의 판정이 각각 한 객체로 모인다. 절삭/거절 방식과 오류 코드는 그대로(AC-007, D-03) | FR-006(한 규칙 한 곳) + FR-007(설정으로 조정). 값과 판정이 한 객체에 있으면 한 곳 판정이 구조로 보장된다. **초안 결정(application 판정 함수 + 숫자 봉투)을 뒤집는다(2026-09-28 사용자 결정)** — 초안이 탈락시킨 "한도를 domain에 인자로 넘김"의 사유(설정이 domain까지 흐른다)는 `RecommendationScore`가 이미 설정값을 받아 domain에 있는 선례로 약했다 | **숫자 봉투(Policy) + application 판정 함수**: 값과 판정이 떨어져 있어 판정이 다시 흩어질 수 있다. **인터페이스 + 구현 여럿(교과서 전략 패턴)**: 한도는 알고리즘 교체가 아니라 값 변경이고 두 번째 구현이 없다 — 교체할 일이 생기면 그때 인터페이스를 뽑는다 |
| D-07 | S5 | **`application/policy` 묶음(10개)을 없앤다.** 알고리즘(`RecommendationScore`·`CategoryDiversity`·`RegionScoreCalculator`·`DistanceScoring`·`FreshnessDecay`)은 서비스가 직접 주입받고, 숫자 한도는 D-06의 규칙 객체로, 외부 호출 설정(임베딩 모델명·차원·재시도 등)은 해당 어댑터 설정으로 내린다(필드마다 S5에서 판정). `*Policy` 이름 센서는 두지 않는다 | 사용자 질문("Policy가 필요한가, 전략 패턴이어야 하지 않나", 2026-09-28)에 대한 조사: 10개 중 8개는 숫자 한도뿐이고 2개는 알고리즘 객체를 담아 서비스가 `policy.score.score(…)`로 꺼내 쓴다. 봉투가 알고리즘을 들고 다닐 이유가 없다. **초안(이름·위치 유지 + `*Policy` 센서)을 뒤집는다** | **유지**: 값·판정 분리와 봉투 경유가 남는다. **`*Settings` 개명**: 이름만 바뀌고 구조 문제가 남는다 |
| D-08 | S2 | **FR-008 센서를 명시 규칙으로 둔다**: `..application..`·`..domain..`은 `org.around.ieuri.common.graphql.generated..`와 `..persistence.entity..`에 의존하지 않는다. 현재 위반 0건 | 기존 규칙은 `com.netflix..`·`graphql..`만 본다. **생성 타입은 `common.graphql.generated`에 있어 지금 무방비다.** 실패 메시지가 FR-008을 가리켜야 한다(FR-015) | **기존 계층 규칙으로 충분하다고 보고 생략**: 생성 타입 경로가 실제로 빠져 있다 |
| D-09 | S6 | **변환 위치 규칙(FR-009)**: application·infrastructure에서 이름이 `to[A-Z]…`이거나 `from`인 메서드는 **`..mapper..` 패키지의 최상위 함수에만** 있다. 클래스·`object`·companion 멤버 변환 금지. `toString`은 제외. application 쪽 변환(도메인 → View 등)은 `application/mapper/`로 모은다. domain은 대상 아님 | 전수: infrastructure 멤버 변환 63건(리졸버 30 등) + mapper 밖 최상위 1, application 14건. 규칙을 "클래스 멤버인가"로 쓰는 것은 1차 조사(`HARNESS-server-sensors.md`)의 교훈. 패키지 패턴은 `..mapper..` | **생성 타입 생성자 호출 금지**: 186곳, payload 조립까지 걸린다(D-10). **Konsist로 작성**: 멤버 vs 최상위 함수는 바이트코드로 구별된다(`*Kt`) |
| D-10 | S6 | **리졸버의 payload 조립은 리졸버에 남긴다.** 변환 규칙의 대상은 "경계 값 ↔ 도메인/View"로 한정 | 성공/실패 봉투를 채우는 것은 요청 흐름의 일부다 | **payload까지 mapper로**: D-09의 186곳 문제 |
| D-11 | S6 | **리졸버가 도메인 VO를 조립하는 2곳**(`RegionRecommendationResolver`, `MapQueryResolver`)은 **오류 응답이 동일함을 확인할 수 있으면** `application/validation`으로 옮기고, 아니면 in/graphql `mapper`로만 옮긴다 | "인바운드 어댑터는 도메인 VO를 조립하지 않는다"는 합의. FR-003이 우선이다 | **무조건 application으로**: 실패 경로가 `errors[]` ↔ `userErrors[]`로 바뀔 위험 |
| D-12 | S7 | **[D-39이 뒤집음 — 2026-09-30]** **`port.in.command`는 `*CommandUseCase`, `port.in.query`는 `*QueryUseCase`.** 48개 중 25개 개명, 파일명도 맞춘다 | CLAUDE.md Layout이 이미 이 이름을 규정. 참고 프로젝트(splearn)의 역할 명사(`MemberRegister`·`MemberFinder`)와 비교 후 유지(사용자 동의 2026-09-28) — 접미사는 센서로 강제되지만(FR-011) 역할 명사의 적절성은 기계가 판정할 수 없다. 방향 없는 이름은 과거 이름 충돌을 낳았다 | **`*UseCase`로 통일**: 개명 수는 비슷하고 import만 보고 방향을 모른다 |
| D-13 | S7 | **[D-39이 뒤집음 — 2026-09-30]** **`application/service`의 최상위 클래스는 전부 `*Service`.** 5개 개명(`TokenIssuer`, `ProviderEmailUniqueness`, `ProviderTokenCollector`, `SpotQaPhotoKeyVerifier`, `PushDispatchStore`) | 한 패키지 한 규칙 | **협력 객체용 패키지 신설**: 규칙 없는 패키지가 하나 더 생길 뿐이다 |
| D-14 | S7 | **[D-36이 뒤집음 — 2026-09-30]** **`adapter.out.persistence`는 `*PersistenceAdapter`, `adapter.out.query`는 `*QueryAdapter`, `JdbcTemplate`·`NamedParameterJdbcTemplate`에 의존하는 클래스는 `*JdbcAdapter`**(역방향도). config의 DataSource 설정은 대상 밖. 20개 개명 | 현재 persistence 56개가 네 갈래, JdbcTemplate 어댑터 25개 중 `Jdbc` 표시 19 | **`Spatial`도 raw SQL 표시로 허용**: 같은 사실에 단어가 둘. **tourism의 `Store` 유지**: FR-011 위반 그 자체 |
| D-15 | S7 | **[D-36이 뒤집음 — 2026-09-30]** **`application/query`의 타입은 `*View` 또는 `*Query`로 끝난다.** 그 밖의 11개는 개명하거나 제자리로 옮긴다 | "읽기 모델"이라는 한 역할에 단어 하나 | **자유 명명 유지**: 센서가 검사할 것이 없다 |
| D-16 | — | **[D-32이 뒤집음 — 2026-09-30]** **`toNewEntity`는 유지한다.** 규칙 문서에 이유를 적는다 | 갱신은 관리 엔티티에 쓰고 더티체킹에 맡긴다. 새 엔티티를 만드는 변환은 생성 경로에서만 쓴다 — `New`가 그 구분을 이름으로 강제한다 | **`toEntity`로 개명**: 갱신 경로에서 새 엔티티를 만들어 `save()`하는 오용을 부른다 |
| D-17 | S2 | **`port.out`의 인터페이스 중 infrastructure가 구현하는 것은 `*Port`.** 116/116 | 비용 0으로 FR-011을 넓힌다. 대상을 "infrastructure가 구현하는"으로 두는 것은 포트의 정의 그대로다 — 같은 패키지의 결과 타입 `sealed interface SnsRevokeResult`가 첫 구현에서 거짓 양성으로 걸렸다 | **"연산을 선언한 인터페이스"**: 결과 타입에 프로퍼티가 생기면 getter가 메서드로 잡혀 다시 거짓 양성이 된다(리뷰 지적). **sealed 제외**: Kotlin sealed 여부는 바이트코드로 안정적으로 보이지 않는다 |
| D-18 | S7 | **패키지·이름 규칙 문서(FR-011)는 CLAUDE.md Naming 절을 표로 확장해 둔다.** 1차 센서 KDoc의 "임시 규칙" 경고도 갱신 | CLAUDE.md가 규칙의 권위 문서다. 별도 문서는 드리프트한다 | **`docs/conventions.md` 신설**: 두 사본이 어긋난다 |
| D-19 | — | **사용자 정의 스테레오타입 애노테이션은 들이지 않는다** | 사용자 결정(2026-09-28, splearn 비교 후). 패키지 + 접미사로 역할이 이미 결정되고, splearn의 `@ApplicationService`는 클래스 단위 `@Transactional`을 묶어 우리의 메서드별 `readOnly`·`REQUIRES_NEW`와 충돌한다 | **도입**: 같은 사실의 세 번째 표시가 된다 |
| D-20 | S1 | **ktlint는 kotlinter 5.7.0(ktlint 1.8.0)으로 붙인다.** **CI를 `./gradlew test` → `./gradlew check`로 바꾼다.** CI는 dev→master PR(test.yml)과 머지 후 빌드(build.yml)에서만 돌므로, feature→dev PR의 스타일 위반은 **머지 후 build.yml에서 드러나 dev 배포를 막는다** — 로컬 `check`가 1차 게이트다— 이를 막으려 **feature→dev PR에 `lintKotlin`만 도는 `lint.yml`을 둔다**(로컬 실측 5초, 컴파일 없음. 사용자 승인 2026-09-28) | 현재 CI는 `test`만 돌려 `check`에 걸린 린트가 CI에서 실행되지 않는다. 인프라 규칙: 관용(플러그인)이 있으면 손으로 만들지 않는다. kotlinter를 먼저 시험해 Gradle 9.5.1·설정 캐시·Kotlin 2.3.0에서 통과했고(위반 수가 CLI 실측과 일치), 생성 코드 제외도 태스크 설정으로 된다 — 기준(먼저 통과하는 쪽)을 만족해 ktlint-gradle은 시험하지 않았다 | **`Exec`로 CLI 호출**: 수제 통합. **JUnit 안에서 룰 엔진 호출**: 포맷 태스크를 따로 만들어야 한다 |
| D-21 | S1 | **ktlint 구성: `ktlint_code_style = intellij_idea`, `package-name` 규칙은 끈다**, 자동 생성 코드는 경로 제외. 기준선 0건은 **일괄 포맷 커밋 → 수작업 → 린트 활성화** 순서 | 실측(ktlint 1.8.0): intellij_idea 1364건 중 1226 자동 수정, 잔여 138. 그중 **98건이 `port.in`·`adapter.in`의 `` `in` `` 패키지**다 — CLAUDE.md가 정한 레이아웃과 충돌하는 규칙이지 기존 부채가 아니므로 규칙 비활성화이지 FR-014의 억제가 아니다(사용자 승인 2026-09-28). ktlint_official은 7194건·24.6k줄 변경 | **ktlint_official**: diff가 약 4배. **`in` 패키지 개명(`inbound`)**: 수백 파일의 import가 바뀌고 CLAUDE.md 레이아웃을 뒤집는다. **baseline.xml**: FR-014 금지 |
| D-22 | S2·S6·S7 | **실패 메시지(FR-015)**: ArchUnit은 `because("…")`에 무엇을 고칠지 쓴다 — 실패 헤더 `Rule '<술어>, because <이유>' was violated`에 규칙과 이유가 함께 찍히고 위반 줄이 위치(`(File.kt:line)`)를 준다. Konsist는 `additionalMessage`. ktlint는 기본 출력(`파일:줄:열 [규칙 id]`) | B1 탐침으로 실제 출력을 확인했다(D-08·D-17). **초안의 `as(…)`를 뒤집는다** — `as`는 술어 설명을 통째로 덮어써 무엇을 검사했는지가 사라진다(리뷰 지적) | **`as("…")`**: 위 이유. **기본 규칙 설명만**: 무엇을 할지 말하지 않는다 |
| D-23 | S8 | **FR-013: detekt 2.0.0-alpha.6(버전 고정), 길이·복잡도 규칙만 켠다 — 함수 60줄 · 클래스 600줄 · 순환 복잡도 14 초과 시 실패**(사용자 승인 2026-09-28). 나머지 규칙셋은 끈다. baseline·`@Suppress`는 쓰지 않는다(FR-014) | 실측: 이 한도에서 main 8·test 2건만 고치면 된다. detekt 1.23.8은 JDK 25에서 실패(jvmTarget 우회도 실패, 실측). 세 지표를 서버 없이 `check`에서 재는 도구는 detekt뿐(조사). 사용률 1위라는 직접 측정은 없고 대리 지표(빌드 파일 언급 수·스타)만 있다 | **SonarQube**: 서버 필요, 순환 복잡도 규칙 없음, UI 억제 가능. **Konsist 길이만**: 복잡도 불가 — FR-013 미충족. **40/400/10**: 기한 안 수정량이 크다 |
| D-24 | S1·S2 | **센서 실행 비용(QR-001)**: 상한은 **도입 전 대비 +25%**(사용자 승인 2026-09-28). 측정은 같은 머신에서 `./gradlew clean check --no-build-cache` 3회 중앙값. 새 ArchUnit 규칙은 클래스 임포트를 공유한다 | 기준(단독 1회): `clean test` 93초 · `clean build` 111초. 스위트 48초 중 아키텍처 테스트 약 39초 — 임포트 공유가 가장 큰 절감 여지다 | **측정 없이 상한 제안**: 근거 없는 숫자 |
| D-25 | S0·S9 | **VC-001 하네스: 두 서버 교차(A/B) 실행.** 기준 커밋 서버(A)와 리팩토링 후 서버(B)를 **같은 dev DB·Valkey**에 붙여 로컬 포트 두 곳(8080·8888 제외)에 띄우고, 요청 하나를 A·B에 연달아 보내 HTTP 상태 + 본문을 비교한다. 요청 집합은 99개 연산 × (성공 ≥1, 오류 ≥1). 변경 연산은 A용·B용 계정을 따로 둔다. 정규화는 호출마다 달라지는 값만, 목록을 결과에 남기고 **배열은 정렬하지 않는다**(POL-019·055). **실행 도구는 서버 레포에 두지 않고 `ieuri-QA/graphql` 스위트(Dart `test`)를 쓴다 — 같은 스위트를 dev와 로컬에 돌려 둘 다 통과하는지로 판정한다**(2026-09-29 사용자 지시로 초안의 "스크립트를 `problem/SPEC-0039-vc/`에 둔다"를 뒤집음: 직접 만든 도구 대신 기존 QA 스위트가 선례). 계획·결과 기록만 `problem/SPEC-0039-vc/`에 둔다. **원본 응답은 커밋하지 않는다**(POL-013) | 외부 실시간 데이터·시계·캐시가 결과를 흔든다 — 교차 실행이 그 차이를 최소화한다. CLAUDE.md: 인프라 테스트를 스위트에 넣지 않는다. dev DB에 테스트 데이터가 생기는 것은 사용자 동의(2026-09-28) | **스위트 안 DGS 골든 테스트**: CLAUDE.md가 금지하는 컨텍스트 부팅 테스트. **기준 응답을 먼저 캡처해 두고 나중에 비교**: 실시간 데이터·시각 차이가 거짓 양성으로 쏟아진다 |
| D-26 | S9 | **VC-002: `git diff 82fdd48..HEAD -- src/main/resources/schema/`가 0줄**임을 기록한다 | 스키마 우선 — 파일이 곧 스키마다. FR-002보다 엄격하다 | **인트로스펙션 비교**: 같은 것을 확인하는 데 서버 기동이 필요하다 |
| D-27 | S0·S9 | **VC-007: 기준 커밋과 최종 커밋의 JUnit XML 테스트 목록을 대조해 `problem/SPEC-0039-vc/test-mapping.md`에 남긴다.** 사라지거나 이름이 바뀐 항목만 수작업으로 대응을 적는다 | 기계적으로 가능한 부분은 기계로 한다. 기준: 183 suite · 1664 테스트 | **수작업 전수**: 누락이 난다 |
| D-28 | 전체 | **작업 중에는 컴파일만, 테스트는 각 브랜치 PR 직전에 `check` 전체.** 본문을 **그대로 옮긴** 변환은 필드별 변환 테스트를 새로 요구하지 않는다(VC-001 A/B가 보호). 옮기면서 **본문을 고친** 변환은 필드별 테스트를 쓴다 | CLAUDE.md "작성 중 테스트를 돌리지 않는다". 변환 테스트 규칙은 "슬라이스가 추가하거나 바꾼 것"에 걸리는데, 잘라 붙이기는 필드 대응을 바꾸지 않는다(사용자 승인 2026-09-28) | **옮긴 변환 77건 전부에 필드별 테스트**: 기한 안에 불가 |
| D-29 | — | **[D-44이 뒤집음 — 2026-09-30]** **브랜치 4개 순차 + 검증 1회.** ktlint 일괄 포맷은 맨 먼저 단독 브랜치로 머지. 각 센서는 **자기가 검사할 코드를 고친 커밋 뒤**에 켠다 | FR-014(도입 시점 0건). 포맷 diff(약 6.8k줄)와 리팩토링 diff가 섞이면 리뷰가 불가능하다 | **한 브랜치**: 포맷 diff 속에서 동작 변경을 찾아야 한다. **병렬 브랜치**: 같은 서비스 파일을 건드려 충돌한다 |
| D-30 | C1 | **주석 전반을 CLAUDE.md POL-033 규칙으로 줄인다**: 무엇을 하는지 한두 줄, 운영 경고와 주석에만 있는 동작 사실(null vs 빈 목록, 절삭 vs 거절, 정렬 보장)은 한 줄로 압축해 남기고, 설계 근거·버린 대안·사고 이력은 지우고 SPEC id만 남긴다. 범위: main·test Kotlin, `build.gradle.kts`, `gradle.properties`, `application*.yaml`, 워크플로. **`.graphqls` 설명은 제외**. 동작 불변은 주석 제외 토큰 비교로 확인한다 | 사용자 요청("주석이 전반적으로 너무 길다", 2026-09-28). 실측: main 코드 27,838줄에 주석 13,637줄(49%), 10줄 이상 블록 305개·15줄 이상 62개. 스키마 설명은 클라이언트 계약이라 D-26·BR-001에 걸린다. B2·B3 전에 해야 옮기는 코드에 긴 주석이 딸려 가지 않는다 | **15줄 이상만 자름**: 요청(전반)을 해결하지 못한다. **근거를 `problem/`으로 옮김**: SPEC-0035 이전 코드는 결정 기록이 없어 옮길 자리를 새로 만드는 일이 된다. 대가: spec에 없는 옛 근거는 커밋 이력에만 남는다 |
| D-31 | C2 | **주석 길이 센서: KDoc·블록 주석·연속된 `//` 묶음은 15줄 미만**(Konsist로 파일을 읽고 문자열 안의 주석 기호·자동 생성 코드는 제외). 연속 `//`까지 세는 것은 블록을 줄로 쪼개 규칙을 피하지 못하게 하려는 것이다. 코드 끝에 붙은 `//`도 연속된 줄이면 한 묶음으로 센다. 제외는 프로젝트 루트 기준 경로(`build/`·`.claude/`)로 판정한다 — 절대 경로에 `/build/`가 들어간 체크아웃에서 센서가 조용히 꺼지지 않게(리뷰 지적). 주석 정리 후 위반 0건일 때 켠다 | CLAUDE.md의 기존 규칙(KDoc 15줄 미만)을 기억이 아니라 빌드로 강제한다(FR-010 취지) | **센서 없이 리뷰에 맡김**: 정리 직후부터 다시 길어진다 |
| D-32 | S11~S16 | **영속성을 JPA에서 Exposed 1.x DSL로 바꾼다.** domain은 계속 순수 Kotlin이다. 끝나면 JPA·QueryDSL·kapt를 걷어낸다(S16) | 사용자 결정(2026-09-30). 조사: JPA를 row mapper로만 쓴다 — 관계·cascade·lazy·`@Version` 0건, raw SQL 어댑터 25·`ON CONFLICT` 27. JPA 암묵 동작(자동 flush·1차 캐시·dirty-checking)이 lost update(글 좋아요·댓글 수, 질문 답변 수, 대화 읽음 지점)의 원인이다. 암묵 동작에 기대는 서비스 코드는 결함으로 보고 고친다(사용자 지시) | **JPA 유지**: 장점을 못 쓰고 암묵 동작의 위험만 진다. **JPA 엔티티 = 도메인(splearn)**: 위와 같은 암묵 동작이 도메인까지 온다. **Spring Data JDBC**: 권장안이었으나 사용자가 Exposed 선택 — 둘 다 암묵 동작이 없고, Exposed는 PostGIS·upsert·window 함수를 DSL로 더 많이 덮는다 |
| D-33 | S11~S15 | **[풀 크기는 D-49가 바꿈 — 2026-10-04]** **Exposed는 별도 DataSource(같은 DB, primary/replica 라우팅 동일)와 자기 트랜잭션 매니저를 쓰고, bounded context 단위로 community → tourism → auth → alarm 순서로 옮긴다.** 옮긴 context는 서비스 `@Transactional`이 Exposed 매니저를 지정하고, 그 context의 raw SQL도 Exposed DataSource를 쓴다. **풀은 JPA 풀(60)을 줄이지 않고 작게 더한다**(`ieuri.exposed.pool`, 기본 최대 10·유휴 2) — JPA 풀 크기는 추천 조회(tourism, S13까지 JPA) 부하로 정해진 값이라 떼어 주면 추천이 느려진다. tourism 이전(S13)에서 두 풀 크기를 맞바꾼다. dev DB 실측: `max_connections` 500 중 133 사용(2026-09-30). **초안의 "합계 유지"를 S11에서 바꿨다** | 사용자 지시(2026-09-30). 확인: context 간 쓰기는 전부 `AFTER_COMMIT` 이벤트, 동기 호출은 조회 유스케이스 3개뿐이라 **한 트랜잭션이 두 매니저에 걸치는 곳이 없다**. tourism 배치 스텝은 tourism 이전 때 매니저를 바꾼다 | **한 번에 전부 교체**: 검증 단위가 서버 전체가 되어 회귀 위치를 좁힐 수 없다. **같은 DataSource 공유**: JPA와 Exposed 매니저가 같은 커넥션을 두고 경합한다 |
| D-34 | S10 | **Spring Boot 4.1.1 · DGS 12.1.0 · codegen 8.7.0 · Kotlin 2.3.21로 올리고, DGS BOM 뒤에 Boot BOM을 다시 import해 Boot 관리 버전이 이기게 한다.** 예외로 `json-path`는 DGS 12가 strictly 3.0.0을 요구해 Boot 속성(`json-path.version`)으로 3.0.0에 맞춘다 | 조사: DGS 기동 검사는 메이저(4)만 본다, spring-graphql 2.0.x·graphql-java 25.0이 4.0과 같다, 실제 비호환 지점은 Boot 4.2(graphql-java 26). 실측: 현재 DGS 11.1.0 BOM이 `reactor-core 3.8.0 → 3.6.1`, `context-propagation 1.2.1 → 1.2.0`으로 내려 Spring 7 요구보다 낮다. DGS 12는 Jackson 3 기본 — 우리가 이미 Jackson 3 | **DGS 11.1.0 유지**: BOM 다운그레이드 문제가 남는다. **라이브러리별 버전 고정**: 고정값이 다음 업그레이드에서 다시 낡는다 |
| D-35 | S11 | **[D-48이 뒤집음 — 2026-10-04]** **스키마 검증: 기동 시 `MigrationUtils.statementsRequiredForDatabaseMigration`이 비어 있지 않으면 기동을 실패시킨다.** 오탐은 결과 문자열을 거르지 않고 `Table` 정의를 DB와 똑같이 맞춰 없앤다. **PostgreSQL 컬럼 타입 불일치는 잡지 못하는 한계로 받아들인다.** S11 실측으로 확정: 통합 검사에서 **Exposed가 우리 identity 컬럼에 대해 스스로 낸 시퀀스 문장(`dropUnmappedSequences` + 테이블 시퀀스의 생성문)만 뺀다** | 조사(Exposed 1.5.0 소스): `ddl-auto=validate` 대응은 없다. 타입 비교는 H2에서만 동작하고 CHECK는 이름만, 인덱스는 종류·조건을 보지 않는다. S11 실측(`community_post`·`community_post_like`): 오탐은 identity 시퀀스 이름(Exposed가 `"community.x_id_seq"`로 추정), `now()` 기본값(`CURRENT_TIMESTAMP`로 비교), FK `ON UPDATE`(Exposed 기본 RESTRICT, DB NO ACTION) 세 종류. 뒤 둘은 선언으로 없어진다(D-42). 이름 붙은 CHECK·인덱스·UNIQUE·NULL 여부는 일치. 감도 확인: nullable 오선언·인덱스 누락·CHECK 이름 오타를 넣자 정확히 그 셋만 보고하고 기동이 실패(웹 서버 기동 전), 되돌리자 통과. **CHECK는 개별 함수 조합으로는 잡히지 않아 통합 검사를 쓴다** | **타입 비교기 직접 작성**: 선례도 Spring 관용도 없는 수제 인프라(CLAUDE.md). **검증 없음**: 엔티티와 스키마가 어긋나도 첫 쿼리까지 모른다 |
| D-36 | S12~S15 | **패키지: context → 층 → 애그리거트.** `domain/<aggregate>/`(루트·소속 엔티티·VO·예외·이벤트), `domain/shared/`, `application/<aggregate>/{provided,required}/`, `adapter/<기술>/`(`graphql`·`event`·`persistence`·`client`·`storage`·`config`). `infrastructure/adapter/in|out/` 구분을 없앤다 | splearn 대조(2026-09-30). 현재 community 한 context에 디렉터리 40개, `domain/aggregate/`에 성격이 다른 애그리거트 11개가 섞여 있고 포트는 파일 하나에 여럿 묶였다. 애그리거트 패키지가 있어야 애그리거트 간 의존을 센서로 잡는다 | **splearn처럼 층을 최상위로**: 그 프로젝트는 context가 하나다 — 우리는 context 분리가 전제. **지금의 종류별(`aggregate/vo/event`)**: 관련 코드가 흩어진다. **in/out 유지**: 방향은 기술 이름이 이미 말한다 |
| D-37 | S12~S15 | **도메인 모델은 불변 `data class`(전부 `val`, 컬렉션은 읽기 전용 `List`). 상태 변경 메서드는 검증 후 `copy`한 새 인스턴스를 돌려준다. 저장소 복원은 public 생성자로 한다(`restore` 없음). 도메인 밖에서 애그리거트 `copy` 호출을 금지하는 센서를 둔다.** `equals`는 기본값 — 애그리거트끼리 비교하는 곳이 나오면 그때 id 기준으로 바꾼다 | 사용자 결정(2026-09-30). 조사: 비JPA Kotlin의 다수 관행 — Spring Kotlin 튜토리얼(JDBC판) "immutable data classes", Spring Data 문서, Exposed 공식 샘플, JetBrains DDD 글. 지금 `Post`는 필드 목록을 생성자·`private set`·`restore`에 세 번 쓴다 | **private 생성자 + `@ConsistentCopyVisibility`**: 매퍼가 생성자를 못 불러 `restore`가 돌아온다. **가변 클래스 + `private set`(현재)**: 비JPA에서 권하는 출처를 찾지 못했고, Exposed는 저장이 어차피 명시적이다 |
| D-38 | S12~S15 | **규칙 판정은 domain에만 둔다. domain은 위반마다 타입 있는 예외를 던지고, application은 선검증도 결과 해석(`Boolean`)도 하지 않는다.** 예외 → 오류 코드 번역은 어댑터의 오류 처리기가 기존 코드(`code`·`codeValue`) 그대로 한다(POL-016). **한 요청에 위반이 여럿일 때 어느 코드가 먼저 나가는지는 바뀔 수 있고, 이를 받아들인다** | 사용자 지시("비즈니스 로직도 고친다, 과한 설계를 뺀다", 2026-09-30). 실측: `Post` 제목·본문·사진 한도가 서비스 2곳 + domain 2곳에서 판정되고, `editBy`의 `false`를 서비스가 "숨겨진 글"로 해석한다. 원인은 domain의 이름 없는 `IllegalArgumentException`이다. **D-01~D-03을 뒤집는다** — D-02가 이 방식을 탈락시킨 사유(판정 순서)는 다중 위반 요청의 우선순위일 뿐이고, 그 대가로 규칙이 네 곳에 흩어졌다 | **D-02 판정 함수 + 위반 값**: 규칙 한 곳은 되지만 서비스가 판정 시점을 쥐어 로직이 계속 서비스에 남는다. **예외를 넓게 잡아 번역**: 무관한 IAE까지 삼킨다 |
| D-39 | S12~S15 | **서비스는 애그리거트마다 `XxxModifyService`·`XxxQueryService` 둘로 모으고, 본문은 "찾는다 → 도메인 메서드 → 저장한다"로 쓴다.** 수정도 `save`를 명시한다. 다른 서비스·context의 조회는 `provided`의 `XxxFinder`로 한다. 들어오는 포트 파라미터는 `provided/dto`, 예외는 `provided/exception`에 둔다. 들어오는 포트 인터페이스 이름은 community 합의에서 정한다 | splearn·사용자 메모(서비스는 절차적으로 읽혀야 한다). 실측: community 서비스 19개(`PostCommandService`·`PostLikeCommandService`·`PostHideCommandService`…). **D-12·D-13을 뒤집는다** — 이름 규칙은 새 구조에서 다시 센서로 건다 | **현재 쪼개진 서비스 유지**: 한 애그리거트의 규칙을 보려면 서비스 여러 개를 읽어야 한다. **유스케이스당 클래스 하나**: 같은 문제가 더 커진다 |
| D-40 | S12~S15 | **애그리거트 규칙: (1) 한 트랜잭션에서 애그리거트 하나만 load→변경→save. 다른 애그리거트 변경은 이벤트. (2) 카운터(좋아요·댓글·답변 수)는 애그리거트 상태가 아니라 저장소의 원자적 증감으로 같은 트랜잭션에서 올린다. (3) 동시에 수정될 수 있는 애그리거트는 `version` 컬럼(낙관적 락). 대상은 context 합의에서 정한다. (4) 애그리거트 밖은 id로만 참조. (5) 외부 I/O(HTTP·Valkey·R2)는 트랜잭션 밖. (6) 조회는 애그리거트를 거치지 않고 DSL로 조회 DTO를 바로 만든다** | 사용자 지시("경계도 바꿔야 한다", 2026-09-30). 조사: lost update 4건, 탈퇴 시 카운터 미감소, 반응 첫 insert 경합, auth의 트랜잭션 안 외부 호출·여러 애그리거트 동시 변경. Exposed는 행 전체를 UPDATE하므로 (3)이 없으면 lost update가 다시 생긴다. **D-04를 뒤집는다** | **카운터를 이벤트로 나중에 맞춤**: 방금 누른 좋아요가 숫자에 바로 반영되지 않는다(POL-041 숨김 임계는 신고 수라 해당 없음). **비관적 락**: 대화·글 수정 빈도에서 대기만 늘린다 |
| D-41 | S12~S15 | **변환은 어댑터마다 `mapper` 패키지의 확장 함수로만 한다**(`adapter/graphql/mapper`·`adapter/persistence/mapper`·`adapter/client/mapper`). 리졸버와 저장소 구현에는 변환 코드가 없다. 필드별 변환 테스트는 POL-033 그대로. D-10(payload 조립은 리졸버)은 유지 | 사용자 지시(2026-09-30). 실측: `CommunityMutationResolver`가 입력 → Command 변환을 메서드 안에 인라인으로 가진다. D-09의 확장 | **context에 mapper 하나**: GraphQL 생성 타입과 Exposed 타입이 한 패키지에 섞여 어댑터 간 의존이 생긴다 |
| D-42 | S11~S15 | **Exposed 사용 규칙: DSL만(DAO 금지). `Table` 객체는 `adapter/persistence/table/`에 DB와 똑같이 선언(D-35). 저장소는 `application/<agg>/required/XxxRepository` 인터페이스를 `adapter/persistence/ExposedXxxRepository`가 구현. 트랜잭션은 Spring `@Transactional`(Exposed의 `transaction {}` 블록 금지). `ON CONFLICT`는 가능한 곳을 `upsert`로.** PostGIS·CTE·pgvector 거리는 raw SQL로 남기고, **raw SQL은 `exposedDataSource` 위의 `JdbcTemplate`**(S11 실측: 같은 트랜잭션 — txid 동일, 기존 raw SQL 어댑터는 DataSource만 바꾼다). **선언 규칙**(S11 실측): identity id는 `autoIncrement()` + 생성 값은 `insertReturning`, `DEFAULT now()` 컬럼은 `databaseGenerated()`, `timestamptz`는 `timestampWithTimeZone`, FK는 `onUpdate = NO_ACTION`과 DB의 제약 이름, CHECK·인덱스·UNIQUE는 DB와 같은 이름 | DAO는 Active Record라 JPA 엔티티=도메인의 문제가 돌아온다. `@Transactional`이어야 `AFTER_COMMIT` 이벤트·`REQUIRES_NEW` 센서가 그대로 동작한다. PostGIS·CTE raw SQL은 사용자가 불가피하다고 확인(2026-09-30). Exposed CTE 미지원(EXPOSED-868) | **DAO**: 위 이유. **`transaction {}` 블록**: Spring 이벤트 단계와 따로 논다. **raw SQL을 Exposed `exec`로**: 파라미터마다 컬럼 타입 쌍을 넘겨야 해 번거롭고 기존 어댑터를 다시 써야 한다. **id를 `databaseGenerated()`로**: insert 결과에 id가 돌아오지 않고 검증기가 identity 제거를 요구한다(실측) |
| D-43 | S12~S15 | **알려진 결함은 해당 context를 옮기면서 이 스펙 안에서 고친다**: 글 좋아요·댓글 수와 질문 답변 수 lost update, 대화 읽음 지점·`lastMessageAt` lost update, 탈퇴 시 카운터 미감소, 장소 반응 첫 insert 경합, 답글의 답글 id 공간 혼동, 비즈니스 예외가 `INTERNAL_ERROR`로 나가는 문제(tourism 조회·지도 변경·커서 디코드), `spotDetail.category.code` null. 보관 파기가 신고 증거를 지우는 문제는 Q04 답을 받아 고친다. **BR-001의 "동작을 바꿀 사유는 별도 스펙"을 이 목록에 한해 뒤집는다** | 사용자 지시(2026-09-30, "코드가 잘못된 것이니 코드를 바꾼다"). JPA에서 먼저 고치면 곧 다시 쓸 코드를 두 번 쓴다 | **JPA에서 먼저 핫픽스**: 이중 작업. **별도 스펙**: 이전 뒤에 같은 코드를 다시 연다 |
| D-44 | 전체 | **남은 작업은 `feature/SPEC-0039-domain-rules` 한 브랜치에 슬라이스 커밋으로 쌓는다**(S10~S16, 이어서 S8·S9). B3·B4 브랜치는 만들지 않는다. **D-29를 뒤집는다** | 사용자 지시(2026-09-30, "해당 브랜치에 context가 들어가면 된다"). ktlint 일괄 포맷과 주석 정리는 이미 머지돼 D-29가 막으려던 diff 혼재는 없다 | **context마다 브랜치**: 리뷰 회차는 늘지만 커밋 단위로 이미 나뉜다 |
| D-45 | S12 | **community 애그리거트: Post(post·photo·tag·chip·spot, version), Comment(version), SpotQuestion(question·사진, version), SpotAnswer·SpotAnswerReply(각자 루트, version — ~~D-58로 뒤집힘~~: 질문 루트의 자식), Conversation(version 없음 — `last_message_at`·읽음 지점은 컬럼별 `GREATEST` 원자 UPDATE), Message(~~D-59로 뒤집힘~~: 대화 루트의 자식), Report, MessageReport.** 좋아요·글 숨김·차단은 애그리거트가 아니라 저장소 연산(`INSERT … ON CONFLICT DO NOTHING`, 좋아요 수는 삽입 행 수만큼). 좋아요·댓글·조회·답변 수는 도메인 필드에서 빼고 원자 증감만. 자동 숨김은 신고 커밋 뒤(`AFTER_COMMIT`) 신고 수를 다시 세서 조건부 UPDATE. R2 HEAD는 트랜잭션 전, 입력 중 publish·보관 파기의 R2 삭제는 트랜잭션 밖. 탈퇴 정리는 애그리거트 종류별 `REQUIRES_NEW` 단계 + 카운터 보정. `OrphanPhotoService`·orphan 엔티티 삭제(테이블은 둔다). 숨겨진 답변에 답글 허용·답변/질문 물리 삭제·탈퇴 정리 실패 무재시도는 현재 동작 유지 | 사용자 승인(2026-10-01). 조사: 저장 시 카운터·`hidden`·`deleted_at` 되쓰기(`CommunityPersistenceAdapters.kt:82-85`, 확인), 해결 처리의 `answer_count` 되쓰기, 대화 읽음 지점 상호 덮어쓰기, 자동 숨김 vs 작성자 삭제의 이중 차감·부활, 동시 5·6번째 신고 모두 미숨김, exists→`saveAndFlush`→`catch DataIntegrityViolationException` 4곳(롤백 전용 표시로 500 가능 — 추정), 답변·답글 id 공간 혼동, 탈퇴 시 댓글·좋아요 수 미차감 | **Comment·SpotAnswer를 부모의 자식으로**: 댓글·답변마다 부모 version이 충돌. **Conversation에 version**: 메시지 전송과 상대 읽음 처리가 매번 충돌. **자동 숨김을 신고 트랜잭션 안에서**: 한 트랜잭션 세 애그리거트, 경쟁 잔존. **좋아요를 애그리거트로**: 지킬 상태가 없고 exists→insert 경쟁만 남는다 |
| D-46 | S12 | ~~D-56으로 뒤집힘~~ **신고된 DM은 신고 시점에 로그로 남긴다**: 메시지 신고 항목에 보낸 사람·보낸 시각·본문 암호문(인용 포함) 사본을 추가 전용으로 기록하고, 메시지 FK의 `ON DELETE CASCADE`를 끊는다. 원본 메시지는 지금처럼 6개월 뒤 파기한다. 기존 신고 항목은 마이그레이션에서 원본으로 사본을 채운다(BR-002) | Q04 사용자 답(2026-10-01, "로그 형식으로 남긴다"). POL-049(운영자는 신고된 메시지만 열람)·POL-050(6개월 파기, 본문 암호화) 둘 다 유지 — 사본도 암호문이다. 사본의 보관 기간은 정책에 없어 recorder 요청으로 남긴다 | **신고된 메시지를 파기에서 제외**: 그 메시지의 보관이 사실상 무기한이 된다. **지금처럼 함께 삭제**: 신고 처리 전에 증거가 사라진다 |
| D-47 | S12~S16 | **이전 기간의 센서는 두 구조를 함께 인식한다**: 층 판정에 `..adapter..`를 infrastructure로 더하고, 도메인 간 예외에 새 경로(`adapter.client` → `application..provided..`, `adapter.event` → `domain..event..`)를 더한다. 다른 context가 구독하는 이벤트는 `domain/event/`에 둔다(공개 계약). `SchemaPlacementTest`·`EnumColumnLengthTest`는 Exposed 테이블도 대상으로 넓힌다. 옛 경로 규칙은 S16에서 지운다 | CLAUDE.md "이전 중 목표 구조" — 옮긴 context와 안 옮긴 context가 공존한다 | **옮긴 context는 센서 제외**: 새 구조가 무방비로 들어온다. **새 센서를 S16에 한꺼번에**: 이전 중 위반이 쌓인다 |
| D-48 | S11 | **기동 시 스키마 검증을 두지 않는다.** `ExposedSchemaValidator`·테이블 목록(`ExposedTables`)을 지운다. 스키마 원본은 Flyway 마이그레이션이고, `Table` 선언이 어긋나면 그 컬럼을 쓰는 첫 요청에서 드러난다(배포 전에는 ieuri-QA 스위트가 잡는다). **D-35를 뒤집는다** | 사용자 방침(2026-10-04): 라이브러리·프레임워크가 지원하지 않는 검증은 넣지 않는다. 조사: Exposed(생성·차이 계산만), Flyway OSS `validate`(마이그레이션 체크섬만), Liquibase `diff`(DB↔DB), jOOQ(코드 생성 — 다른 라이브러리), Ktor+Exposed 샘플(`SchemaUtils.create` — 생성) 어디에도 "코드 정의 ↔ 실제 스키마" 기동 검증이 없다. 실무 사례(EU EUDI 지갑·digitalfabrik·NAV)는 모두 `MigrationUtils`를 감싼 자작 코드이고, Exposed를 감싼 서드파티 스타터(`hongque-pro/infra-orm`, Exposed 0.61·Boot 3)는 차이 SQL을 실행하는 자동 DDL이다(2026-10-04 조사). 손으로 나열한 테이블 목록은 누락 시 조용히 검증을 건너뛴다(사용자 지적) | **D-35 유지(Exposed 차이 계산 + 판정)**: 지원 기능이 아닌 판정 코드를 우리가 소유한다. **목록 대신 클래스패스 스캔**: 같은 판정 코드가 남는다 |
| D-49 | S11 | **Exposed 풀은 메인 풀 hikari 설정을 크기까지 그대로 복사한다**(`ieuri.exposed.pool` 삭제). 단계별 크기 조정은 하지 않는다 | 사용자 결정(2026-10-04): 단계마다 풀을 옮기면 배포·유지보수만 는다. **대가: 메인 풀에 더해지므로 운영 DB 연결이 파드당 쓰기 60 + 읽기 60만큼 늘어난다** — 매니페스트 기준 현재 약 273/500, 더하면 약 513으로 상한을 넘는다(canary 겹침 제외). 운영 배포 전에 `max_connections`(ieuri-manifest)를 올려야 한다(POL-056) | **단계별 이동(20/20 → 60/60)**: 배포·설정 변경이 단계마다 생긴다. **10/2 고정**: 근거 없는 값이었다 |
| D-50 | S12 | **동시 수정은 `version`이 아니라 열 분리로 막는다**: 편집 UPDATE는 내용 열만, 숨김·삭제는 각자 한 열만 바꾸는 조건부 UPDATE(`WHERE hidden = false AND deleted_at IS NULL`), 계수는 원자 증감만 쓴다. 편집 UPDATE가 0건이면(그 사이 숨겨짐) `POST_NOT_EDITABLE`. 댓글 삭제는 `UPDATE … RETURNING hidden`으로 이번 삭제가 보이는 댓글이었을 때만 댓글 수를 내린다. **D-40(3)·D-45의 `version`을 뒤집는다** | `version`은 편집과 자동 숨김이 부딪힐 때 한쪽을 실패시켜 재시도 장치가 필요하고, V8 마이그레이션이 공유 dev DB를 거쳐 dev 서버 기동을 막을 위험이 있다. 열이 겹치지 않으면 둘 다 성공한다. C1·C2·C4(조사)를 모두 해소한다(권장안으로 진행 — 사용자 확인 대기) | **`version` 낙관적 락**: 위 이유 |
| D-51 | S12 | **[D-54가 뒤집음 — 2026-10-04]** **외부 I/O(R2 HEAD, auth 회원 조회)가 앞서는 서비스는 `TransactionOperations`(Exposed 매니저)로 범위를 직접 정하고, DB만 쓰는 서비스는 `@Transactional(EXPOSED_TX)`를 쓴다.** 글 개인 숨김은 별도 서비스 없이 `PostModifyService`에 둔다(글 존재 확인이 필요하고 상태가 없다). 신고 자동 숨김은 `ReportFiled` → `AFTER_COMMIT` 리스너 → `REQUIRES_NEW`에서 신고자 수를 다시 센다 | 한 메서드 안에서 I/O 뒤 트랜잭션을 열려면 프록시 자기 호출을 피해야 한다(CLAUDE.md). `TransactionTemplate`은 Spring 관용구다. 커밋 뒤 재계수는 동시 5·6번째 신고 미숨김(C7)을 없앤다 — 마지막 커밋이 앞선 신고를 모두 본다 | **`@Transactional` 메서드를 별도 빈으로 쪼갬**: 서비스 하나가 둘로 나뉘어 절차가 흩어진다. **`PostHideModifyService` 분리(D-45 목록)**: 저장소 연산 두 개뿐이다 |
| D-52 | S12 | **쓰기 쪽은 판정에 필요한 최소 테이블만 읽고, 바꾸는 행만 쓴다.** `Post` 애그리게이트는 글 한 행(작성자·제목·본문·편집·숨김·삭제)이고 `findById`는 글 테이블 하나만 읽는다. 카테고리·장소·태그·사진은 도메인 값 객체(`PostTags`·`PostPhotos`·`PostSpot`)로 검증한 뒤, 서비스가 `?.let`으로 저장소의 의도별 메서드(`changeCategory`·`changeSpot`·`replaceTags`·`replacePhotos`)를 부른다. 장소는 입력의 `changeSpot` 플래그와 nullable `PostSpot`으로 다룬다(null이면 뗀다 — 2026-10-04 `SpotChange` 제거: 플래그 하나를 타입으로 감쌌을 뿐이다). 저장소 포트는 도메인 객체·값 객체만 받는다 — 저장 전달용 타입(`PostEdit` 등)을 두지 않는다 | 사용자 지적(2026-10-04): "최소한만 가져온다 — JPA 지연 로딩과 같은 맥락", 하위 항목은 GraphQL이 요청할 때 글 PK로 읽는다(읽기 쪽은 이미 필드 리졸버+DataLoader). 편집 입력의 null이 "건드리지 않는다"라 기존 자식을 알 필요가 없다. 도메인→행 변환은 어댑터의 일이고 의존이 바깥→안이라 경계 위반이 아니다 | **애그리게이트 전체 적재·전체 재기록(직전 구현)**: 테이블 5개 조회, 매번 자식 삭제·재삽입. **도메인이 변경 내용 타입(`PostEdit`)을 돌려줌**: 저장소에 넘기려고 만든 타입이라 DTO 성격(사용자 지적) |
| D-53 | S12 | **포트 구현 클래스는 `XxxPersistenceAdapter`**(기술 이름 접두사 없음). 한 파일 한 어댑터 | 헥사고날 표준 명명(Hombergs, *Get Your Hands Dirty on Clean Architecture* — `AccountPersistenceAdapter`)이고 이 레포 관례(D-14)와 같다. 사용자 판단 위임(2026-10-04) | **`ExposedXxxRepository`**: 기술이 이름에 새어 나온다. **`XxxRepositoryImpl`**: Spring Data 사용자 정의 구현 관례라 포트 구현이라는 역할이 드러나지 않는다 |
| D-54 | S12 | **트랜잭션은 `@Transactional(transactionManager = EXPOSED_TX)`로 통일하고, 외부 호출(R2·다른 context)은 메서드 앞 첫 쿼리 전에 둔다.** `TransactionOperations` 빈을 없앤다. **선언은 클래스 단위**이고 다른 전파가 필요한 메서드(커밋 뒤 자동 숨김의 `REQUIRES_NEW`)만 메서드에 둔다(사용자 지적). 매니저 이름 지정(`EXPOSED_TX`)은 JPA·Exposed 매니저가 함께 있는 이전 기간에만 필요하다 — 생략하면 `@Primary`인 JPA 매니저가 잡혀 Exposed 쿼리가 트랜잭션 없이 돈다. S16에서 JPA를 걷어내면 지정을 지운다. **신고 대상 판정·숨김 규칙은 응용 계층으로 올린다**: `ReportModifyService`가 대상 종류별로 각 저장소의 `exists`·`hideIfVisible`을 부르고, 숨겨진 댓글·답변이면 부모의 수를 내린다(답글은 수 없음). `ReportTargetPersistenceAdapter`를 없앤다. 장소 Q&A 대상은 Q&A 이전 커밋 전까지 옛 포트(`SpotQaModerationPort`·`SpotQuestionCounterPort`)를 쓴다. 도메인 파일은 한 파일 한 타입. **D-51을 뒤집는다** | 사용자 지적(2026-10-04): 코드베이스 표준은 `@Transactional`이고 primary/replica 라우팅도 그 `readOnly`를 따른다. 확인: Exposed는 연결을 첫 쿼리에서 잡는다(1.5.0 `TransactionManager.kt` 211행 `connectionLazy`) — 앞선 외부 호출 동안 연결·락을 잡지 않는다. `@Transactional`은 checked 예외를 감싸지 않는다. 신고 대상 어댑터는 숨김 규칙(어느 수를 내리는가)을 어댑터에 숨기고 있었다(사용자 지적). 새 Q&A 어댑터를 지금 만들면 옛 JPA 어댑터와 빈 이름이 겹친다 | **`TransactionOperations` 유지**: 방식이 둘로 갈린다. **어댑터가 대상 라우팅과 수 차감을 맡음(직전)**: 업무 규칙이 어댑터에 숨는다 |
| D-55 | S12 | **장소 Q&A: 질문·답변·답글은 각자 한 행짜리 애그리게이트**(~~D-58로 뒤집힘~~: 질문 루트 하나, 나머지는 유지)(사진 없음 — 쓰기에서 기존 사진을 읽지 않는다). 사진 규칙은 글과 같아 `PostPhotos`를 공유 값 객체 `AttachedPhotos`로 옮기고, 본문 규칙(500자·INVALID_INPUT, 질문/답변 문구)은 `SpotQaText` 한 곳에 둔다. 키 파싱·R2 존재 확인은 공용 `UploadedPhotoVerifier`. 해결 표시는 `resolved` 한 열만, 답변 수는 원자 증감, 답변 삭제는 `DELETE … RETURNING hidden`으로 이번에 지운 답변이 보이던 것이었을 때만 수를 내린다. 물리 삭제는 사진 → 주인 순서(사진은 다형 참조라 FK가 없다). **답글 대상 id 혼동은 고치지 못한다**: 입력이 `parentAnswerId` 숫자 하나이고 답변·답글 시퀀스가 달라 서버가 종류를 구별할 수 없다 — 구별하려면 id에 종류를 담는 스키마 변경이 필요하고 이 spec은 스키마를 바꾸지 않는다(FR-002). 동작은 기존 계약(답변이 없고 같은 번호 답글이 있으면 REPLY_TO_REPLY_NOT_ALLOWED) 그대로다 | 조사 C3(해결 표시의 answer_count 되쓰기)·C5(숨김·삭제 경쟁의 이중 차감) 해소. 기존 서비스는 답글 본문 한도를 답변 상수로 판정했다(FR-006 위반). 실측: 생성 시 답변 수 1, 해결 표시 뒤에도 1, 답글의 답글 거부, 답변 삭제 시 0·답글 함께 삭제, 질문 물리 삭제 | **id 혼동을 이번에 해결(전역 id·종류 접두)**: 스키마 변경이라 별도 spec. **질문 애그리게이트에 답변을 자식으로**: 여러 사람의 답변이 한 애그리게이트를 다투게 된다(D-45) |
| D-56 | S12~S16 | **이 리팩토링에서 파생되는 작업은 GraphQL 스키마와 DB 스키마를 바꾸지 않는다.** 마이그레이션을 추가하지 않는다. 그래서 D-46(신고 DM 사본·FK 연쇄 삭제 해제)을 뒤집고, 신고된 메시지도 지금처럼 6개월 뒤 함께 파기하는 동작을 그대로 옮긴다. 결함 수정(D-43)도 스키마를 건드리지 않는 범위에서만 한다 | 사용자 지시(2026-10-05, "graphql과 DB는 변경되지 않는다"). 리팩토링은 동작·계약을 보존하는 작업이고, 보관 정책 변경은 그 범위가 아니다 | **D-46 유지(V9 추가)**: 리팩토링 PR이 스키마 변경을 싣게 된다. **신고된 메시지를 파기에서 제외**: 처리 완료 표시가 없어 보관이 무기한이 되고 POL-050과 부딪힌다 |
| D-57 | S12 | ~~D-58로 뒤집힘~~ **장소 Q&A는 `domain/qa`, `application/qa` 한 패키지에 애그리게이트 셋(질문·답변·답글)을 둔다.** 본문 규칙 `SpotQaText`도 `domain/shared`에서 `qa`로 옮긴다(이름 유지). 사진 행 보조 함수의 `internal`은 뺀다 — 단일 모듈이라 효과가 없다 | 셋은 한 흐름이고 본문·사진 규칙과 서로의 참조(`SpotAnswer.write(question)`)를 공유한다. 패키지는 도메인 개념(Q&A)으로 이름 짓고 대상(spot)으로 짓지 않는다. 애그리게이트를 하나로 합치지 않는 이유는 D-45·D-55 | **애그리게이트마다 패키지(question/answer/reply)**: 공유 규칙이 `shared`로 밀려난다. **질문 하나에 답변·답글을 자식으로**: 동시 답변이 한 애그리게이트를 다툰다(D-45) |
| D-58 | S12 | **장소 Q&A는 질문(`SpotQuestion`)을 루트로 하는 애그리게이트 하나다.** 답변·답글은 루트에 속한 엔티티이고 루트를 통해서만 만들고 지운다(`answer`, `reply`, `removeAnswer`, `removeReply`). 저장소(`SpotQuestionRepository`)·서비스(`SpotQuestionModifyService`)·유스케이스·영속 어댑터는 하나씩이다. 패키지는 루트 이름인 `domain/question`, `application/question`. 루트는 질문 한 행만, 자식은 필요한 한 행만 읽는다(다른 답변은 읽지 않는다). 답변·답글 삭제는 루트를 함께 읽고(답글은 답변 id로 질문을 조인해 한 번에), 루트가 없으면 NOT_FOUND다 | 사용자 지적(2026-10-05): 셋은 한 애그리게이트이고 루트를 통해 들어가야 한다. D-45가 셋을 나눈 근거("여러 사람의 답변이 한 애그리게이트를 다툰다")는 애그리게이트를 통째로 읽고 저장하는 방식을 전제했다 — 최소 조회·원자 카운터(답변 수는 상태가 아니다)에서는 답변 insert가 루트 행을 쓰지 않아 다투지 않는다. 답변 숨김과 답변 수 감소도 한 애그리게이트 안의 일이 된다. 루트 없이 남은 답변·답글은 dev에 0건(2026-10-05 조회)이라 삭제 시 루트를 읽어도 동작 차이가 없다. 실측: 생성·해결(답변 수 1)·답글의 답글 거부·답글 삭제 후 재삭제 REPLY_NOT_FOUND·답변 삭제(답변 수 0)·질문 삭제 | **각자 루트(D-45·D-55)**: 자식이 루트 밖에서 만들어지고, 답변 수 감소가 애그리게이트 둘에 걸친다. **`qa` 묶음 패키지(D-57)**: 여러 애그리게이트를 묶는 패키지가 되어 "패키지 = 애그리게이트" 규칙과 어긋난다. **Spot(tourism) 아래**: context 경계를 넘고, 장소 하나의 모든 질문이 Spot 하나를 다툰다 — 장소는 `catalogId`로만 참조한다 |
| D-59 | S12 | **대화는 `Conversation`을 루트로, 메시지를 자식으로 하는 애그리게이트 하나다.** 메시지는 `conversation.send(...)`로만 만들고(참여자·본문·사진 키 판정), 루트는 대화 한 행만 읽는다. 메시지 신고(`MessageReport`)는 별도 애그리게이트이며 판정(상세 사유 → 존재 → 소유 → 대화 일관성 → 참여)은 `MessageReport.file`이 한다. 최근 활동 시각·읽음 지점은 **앞으로만 가는 조건부 UPDATE**(`WHERE col IS NULL OR col < :v`)로 쓴다 — `GREATEST`와 같은 결과이고 DSL로 표현된다. 대화 시작의 경합은 `insertIgnore` 뒤 쌍으로 다시 읽는다(기존 코드도 유일 제약 위반을 삼켰다). 차단 확인은 `BlockRepository.existsBetween`, 인용 원본은 `SpotQuestionRepository`로 읽는다 — 아직 JPA인 읽기 포트를 쓰면 같은 트랜잭션의 미커밋 읽음 지점을 보지 못한다. **보관 파기**: 행 삭제는 `ExpiredMessageDeleteUseCase`(트랜잭션)가 커밋하고, R2 삭제는 트랜잭션 없는 `MessageRetentionService`가 그 뒤에 한다(`ReportModifyService`가 유스케이스 둘을 구현하는 선례). 패키지는 `conversation`, `messagereport`. 쓰기 리졸버·입력 변환은 `adapter/graphql`로 옮겼다 | D-58과 같은 원칙(루트를 통해 들어간다, 사용자 확인 2026-10-05). 기존 `ExpiredMessageRowPurger`는 태스클릿의 JPA 트랜잭션에 합류해 R2 삭제가 커밋 전에 돌았다 — 주석과 동작이 달랐다. 실측(로컬): 오류 경로 38/39·쓰기 경로 43/46 dev 동일(이전과 같은 차이), 대화 재시작은 같은 id, 전송 시 `last_message_at`이 메시지 생성 시각으로·읽음 지점이 마지막 메시지로 오름, JPA 조회가 Exposed로 쓴 암호문을 복호화, 신고 성공·중복 id 하나로·재신고 ALREADY_REPORTED·혼재·상세 사유 누락 거부, 실패 시 행 없음 | **Message 별도 루트(D-45)**: 메시지가 루트 밖에서 만들어지고 참여자 판정이 서비스로 흩어진다. **이벤트(AFTER_COMMIT)로 R2 삭제**: 배치 태스클릿의 JPA 트랜잭션 안에서는 동기화가 바깥 트랜잭션에 걸려 태스클릿 끝까지 미뤄진다. **`GREATEST` 함수 호출**: DSL에 없어 사용자 정의 함수가 필요하다 |
| D-60 | S12~S16 | **같은 애그리게이트 안의 자식 컬렉션은 허용한다 — 작고 상한이 있을 때만.** 상한이 없는 자식(메시지·답변·답글)은 컬렉션으로 들지 않고, 루트 id를 갖고 루트의 팩토리 메서드로만 만든다. 애그리게이트 사이에는 여전히 id 참조만. 지금 코드는 바뀌는 곳이 없다(글의 사진·태그는 후보지만 기존 집합을 읽는 규칙이 없어 통째로 교체를 유지). CLAUDE.md "Target shape"에 반영, JPA 절의 `@OneToMany` 금지는 S16에서 함께 지운다 | 사용자 결정(2026-10-05). `@OneToMany` 금지의 이유는 JPA의 숨은 적재·N+1이었고 Exposed DSL은 아무것도 암묵적으로 읽지 않는다(일대다 자동 적재 없음, Exposed 1.5.0 문서). 읽기의 N+1은 필드 리졸버·DataLoader가 맡는다. 남는 위험은 크기다 — Vernon(IDDD, Effective Aggregate Design): 큰 컬렉션은 원소 하나를 더하려고 전부 읽게 만든다, 고친 모양은 `Product.planBacklogItem`(루트 팩토리 + id 참조). Spring Data JDBC도 애그리게이트 안의 자식 컬렉션을 한 단위로 다룬다 | **금지 유지**: 이유가 사라진 규칙이다. **무조건 허용**: 상한 없는 자식(6개월 보관의 DM 등)을 매번 전부 읽고, 동시 전송이 애그리게이트 저장에서 충돌한다 |
| D-61 | S12 | ~~D-65로 일부 뒤집힘~~ **탈퇴 정리는 애그리게이트별 단계(`MemberContentEraser`)다.** 글·댓글·질문·대화·차단 ModifyService가 각자 한 단계를 구현하고(`REQUIRES_NEW`), 트랜잭션 없는 `MemberDataEraseService`가 차례로 부른다. 한 단계가 실패해도 나머지는 진행하고 재시도하지 않는다(로그만). 카운터를 보정한다: 지운 좋아요만큼 좋아요 수, 수에 잡혀 있던(숨겨지지 않은) 댓글·답변만큼 댓글·답변 수를 `UPDATE … RETURNING`으로 받은 대상에 내린다 | D-45("애그리거트 종류별 REQUIRES_NEW 단계 + 카운터 보정")의 구현. D-43의 "탈퇴 시 카운터 미감소" 해소. 공용 계정이라 실측하지 못함 | **한 트랜잭션(기존)**: 한 표의 실패가 전체를 되돌려 아무것도 지워지지 않는다. **단계별 재시도**: 관찰된 실패가 없다(D-45 무재시도 유지) |
| D-62 | S12 | **raw SQL은 `exposedJdbcTemplate`(exposedDataSource 위 `JdbcTemplate`)로 Exposed 트랜잭션에 합류한다.** 거리순 피드(PostGIS)와 카탈로그-언어 사본(임시 테이블)이 쓴다. 주입은 파라미터 이름으로 한다(메인 `jdbcTemplate`과 같은 방식, `@Primary` 없음). 사본 동기화·장소 연결 정리는 `SpotCatalogSyncService`(REQUIRES_NEW, 커밋 뒤 리스너가 부름), `OrphanPhotoService`는 지운다 | D-42의 구현. 실측: 거리순 반경 안·밖·숨김 제외가 dev와 동일 | **임시 테이블을 Exposed DSL로**: 임시 테이블 선언·차집합 SQL을 DSL로 옮겨도 얻는 것이 없다 |
| D-63 | S12 | **조회는 애그리게이트별 QueryService(글·댓글·차단·질문·대화)이고 읽기 포트 `CommunityQueryPort`는 글·댓글·차단이 함께 쓴다.** 조회자 필터(`viewerContext`)·페이지 상한은 `application/shared`의 확장 함수. 다른 context(알림·지도)의 차단 판정은 `BlockQueryUseCase`로 간다. QueryDSL은 Exposed DSL로 옮기며 `CASE`는 같은 뜻의 조건으로(안 읽은 수: 낮은 쪽이면 low 지점, 아니면 high 지점), 대화별 마지막 메시지는 별칭 상관 서브쿼리로. community 패키지는 Target shape로 정리했다(`application/port·service·query`, `domain/vo·event`, `infrastructure` 없음) | Target shape("애그리게이트당 XxxQueryService 하나"). 읽기는 애그리게이트를 거치지 않으므로 포트를 애그리게이트마다 쪼갤 이유가 없다. 실측: 조회 루트 53/54(차이는 조회수 부수 효과), 거리순·카테고리·태그·장소·숨김·다음 페이지 커서 모두 dev와 동일 | **조회 포트도 애그리게이트별로**: 같은 모더레이션 조건을 세 어댑터가 나눠 들게 된다. **CommunityQueryService 유지**: 한 서비스가 세 애그리게이트를 맡는다 |
| D-64 | S12 | **장소 Q&A 글의 종류(`SpotQaKind`: QUESTION·ANSWER·REPLY)는 도메인 개념이다.** 세 엔티티가 클래스 본문의 `val kind`로 자기 종류를 고정으로 든다(생성자·`copy`·`equals`에 끼지 않아 바꿀 수 없다). 사진 저장은 `entity.kind`를 `owner_type`에 옮기기만 하고, 사진 행 쓰기는 질문 어댑터의 private 함수다(`SpotQaPhotoRows.kt`·`SpotQaPhotoOwner` 삭제). 사진 자체는 엔티티에 넣지 않고 지금처럼 옆에 넘긴다 | 사용자 지적(2026-10-05): 주인 종류를 호출자가 인자로 넘기면 질문 id에 ANSWER를 붙여도 막을 수 없는 숨은 규칙이다. 종류 구분은 신고 대상(`ReportTargetType`)·알림 이벤트에 이미 있는 도메인 구분이다. 실측: Q&A 생성·삭제(행 없음), 조회 dev 동일 | **어댑터의 저장 구분값(`SpotQaPhotoOwner`)으로 유지**: 짝을 타입이 보장하지 않는다. **생성자 파라미터**: 매퍼가 다른 값을 넣을 수 있다. **사진도 엔티티 필드로**: 판정에 쓰이지 않아 D-60 기준 밖이고, 읽을 때마다 사진을 읽어야 한다 |
| D-65 | S12 | **탈퇴 정리는 탈퇴 리스너(adapter/event)가 애그리게이트마다 그 유스케이스(`eraseMemberContent`)를 부르고, 각 서비스는 건별로 도메인을 거친다.** `application/withdrawal`(오케스트레이터·단계 인터페이스)은 없다. 글·댓글은 사용자 삭제와 같은 도메인 메서드·저장소 경로(댓글 수 보정은 `markDeletedWasCounted`), 질문·답변·답글은 루트의 탈퇴 메서드(`withdraw`·`withdrawAnswer`·`withdrawReply` — 사용자 삭제와 달리 삭제 표시만)로 답글 → 답변 → 질문 순서, 대화는 대화마다 루트가 참여자를 확인한 뒤 그 대화의 보낸 메시지를 표시한다. 좋아요·숨김·차단은 판정할 규칙이 없는 저장소 연산이라 한 번에 지운다. 삭제 시각은 이벤트의 `occurredAt`, 리스너 메서드마다 예외를 삼켜 한 애그리게이트의 실패가 나머지를 막지 않는다 | 사용자 지적(2026-10-05): 리스너(infra) → 유스케이스(application) → 도메인이어야 하고, 대응하는 도메인이 없는 withdrawal 패키지와 도메인을 건너뛰는 일괄 SQL은 맞지 않다. Khorikov(*DDD and bulk operations*): 건별 처리의 단점은 성능뿐이고, 명세+명령은 "정교한 도메인 로직을 메모리·일괄 양쪽에서 재사용할 때" — 한 회원 분량·커밋 뒤 비동기라 건별로 충분하다. 대용량이 확인되면 그때 명세로 모아 일괄로 나눈다. 공용 계정이라 실측하지 못함 | **일괄 SQL 유지(D-61)**: "수에 잡히는 댓글·답변" 규칙이 사용자 삭제와 탈퇴 SQL 두 곳에 중복된다. **명세+명령**: 지금 규모·규칙(조건 하나)에 비해 타입이 크게 는다. **오케스트레이터를 shared로**: 대응하는 도메인이 없는 application 계층이 남는다 |
## 부족한 정보

| # | 질문 (서비스 사실) | 막는 것 |
|---|---|---|
| Q03 | SPEC-0039의 새 완료 기한은 언제이며, 그동안 기능 개발 동결을 유지하는가? | 없음(일정만) |
| Q04 (답변됨 → D-46, D-56으로 뒤집힘) | 신고된 DM 메시지는 보관 기간(6개월, POL-050)이 지나도 신고 처리가 끝날 때까지 남아야 하는가? 남는다면 처리 후에는 언제 지우는가? POL-049(운영자는 신고된 메시지를 열람)와 POL-050(6개월 후 삭제)이 부딪히며, 지금 코드는 기간이 지나면 신고된 메시지도 함께 지운다 | S12의 메시지 보관 파기 수정 |

**승인 대기 (spec의 `[TBD]`)**

| 항목 | 막는 것 | 상태 |
|---|---|---|
| Q01 — FR-013 도구와 한도 | — | **detekt 2.0.0-alpha.6, 60/600/14 승인(2026-09-28)** — D-23 |
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
| S3~S7 | — | — | **S12~S15에 흡수(2026-09-30)** — 규칙 이동·설정 한도·변환 이동·개명을 context 이전과 함께 한다(D-36~D-41) | 폐기 |
| S10 | B2 | QR-001 · FR-001 | Boot 4.1.1·DGS 12.1.0·codegen 8.7.0·Kotlin 2.3.21, BOM 순서 수정(D-34). 동작 불변 | 완료. 해석 결과 reactor-core 3.8.7·context-propagation 1.2.1·json-path 3.0.0·Flyway 12.4.0. 18888 기동 5.9초, 조회 비교 53/54 동일(차이 1건은 `post.viewCount` 조회 부수 효과 — 이전과 같음) |
| S11 | B2 | FR-017 | Exposed 기반: 별도 DataSource·트랜잭션 매니저(D-33), 기동 시 스키마 검증(D-35), raw SQL 도구 실측(D-42) | 완료. 실측 코드는 커밋하지 않음. 로컬에 읽기 복제본이 없어 **읽기 전용 라우팅은 dev 배포 후 확인**. 실측 insert는 롤백 확인(잔여 0건) |
| S12 | B2 | FR-005~FR-009 · FR-019 · AC-001~AC-007 | community: 경계 합의 → 패키지(D-36) → 도메인 모델(D-37·D-38) → 서비스(D-39) → Exposed(D-40·D-42) → mapper(D-41) → 결함(D-43) | — 진행 중. ① 테이블 선언(`d7fa8b2`) ⑤ 탈퇴 정리(D-61)·장소 참조 정리·사본 동기화(D-62)·조회 Exposed 이전·조회 서비스 분할·패키지 정리(D-63), community JPA 엔티티 삭제: 조회 루트 53/54(조회수 부수 효과), 조회 분기 전부 동일, 오류 경로 38/39·쓰기 경로 43/46 dev 동일(이전과 같은 차이), Q&A 실측 통과. 탈퇴·보관 파기 배치·사본 동기화·자동 숨김은 실측하지 않음. ④ 대화·메시지·메시지 신고 쓰기 이전(D-59): 오류 경로 38/39·쓰기 경로 43/46 dev 동일(이전과 같은 차이), DM 실측 통과. 보관 파기 배치는 실측하지 않음. ③ 장소 Q&A 쓰기 이전(D-55): 오류 경로 38/39·쓰기 경로 43/46 dev 동일(이전과 같은 차이), Q&A 실측 통과. ② 글·댓글·좋아요·숨김·차단·신고 쓰기 이전(`1b14081`): 오류 경로 38/39 dev 동일(차이 1건 = 태그 6개 코드, 확정), 쓰기 단계 43/46 동일(차이 3건 = 하네스 닉네임 표기·지난 검증 잔여 데이터), 글·댓글·좋아요 수·숨김·차단 상태 검증 전부 통과 |
| S13 | B2 | 같음 | tourism (배치 트랜잭션 매니저 포함) | |
| S14 | B2 | 같음 | auth (트랜잭션 안 외부 I/O 분리) | |
| S15 | B2 | 같음 | alarm | |
| S16 | B2 | FR-010 · FR-011 | JPA·QueryDSL·kapt 제거, `@Transactional(transactionManager = EXPOSED_TX)`의 매니저 지정 제거(D-54), JPA 전제 센서·CLAUDE.md 규칙 정리, 새 구조 센서(애그리거트 순환·교차 호출·`copy` 제한) | |
| C1 | B1.5 | D-30 | 주석 정리(컨텍스트별), 주석 제외 토큰 비교 | 완료. Kotlin 주석 16,248 → 7,376줄, 15줄 이상 블록 67 → 0. 771파일 주석 외 차이 0건, 마이그레이션·스키마 변경 0 |
| C2 | B1.5 | D-31 · FR-010 | 주석 길이 센서 | 완료. 16줄 탐침으로 실패·제거 후 통과 확인, 문자열 안 주석 기호 오탐 없음 |
| S8 | B4 | FR-013 · VC-004(한도) | detekt 2.0.0-alpha.6, 60/600/14(D-23), outlier 분할 | |
| S9 | (머지 후 dev) | VC-001 ~ VC-007 · QR-001 | A/B 실행, 스키마 diff, 테스트 대응표, 센서별 위반 삽입·제거 확인, 시간 측정. 결과를 spec 변경 이력에 | |

브랜치: B1 = `feature/SPEC-0039-hexagonal-sensors`, B1.5 = `feature/SPEC-0039-comment-trim`, B2 = `feature/SPEC-0039-domain-rules`, B3 = `feature/SPEC-0039-boundary-naming`, B4 = `feature/SPEC-0039-complexity-limit`.

### S12에서 지운 테스트 (PR 전에 새 구조로 다시 쓴다 — VC-007 대응표의 입력)

`PostCommandServiceTest`, `CommentCommandServiceTest`, `PostLikeAndBlockServiceTest`, `PostHideCommandServiceTest`, `ReportCommandServiceTest`, `domain/PostTest`, `domain/PostTagTest`, `domain/PostLikeTest`, `domain/CommentAndBlockTest`, `domain/ReportTargetTest`, `infrastructure/mapper/CommunityMapperTest`, `SpotQuestionCommandServiceTest`, `SpotAnswerCommandServiceTest`, `domain/SpotQaTest`, `infrastructure/mapper/SpotQaMapperTest`, `infrastructure/SpotQaInputMapperTest`, `ConversationCommandServiceTest`, `ConversationSpotQaOriginTest`, `MessageReportCommandServiceTest`, `MessageRetentionServiceTest`, `domain/ConversationAndMessageTest`, `domain/ConversationReadStateTest`, `infrastructure/ConversationMapperTest`, `infrastructure/mapper/ConversationSpotQaOriginMapperTest`, `MemberDataEraseServiceTest`, `OrphanPhotoServiceTest`(대상 삭제), `PostSpotCleanupServiceTest`, `CommunityQueryServiceTest`. 검증하던 동작은 git 이력(`29d9040`)에 있다.

### S12에서 생긴 응답 차이 (VC-001에 의도한 차이로 기록)

- `createPost` 태그 6개: `INVALID_INPUT` → `TOO_MANY_TAGS`(메시지는 같다). 판정을 도메인 한 곳으로 모으면서 생성·수정 경로의 비대칭(D-05)이 없어졌다. **클라이언트 무영향 확인 후 확정(2026-10-04)**: 클라이언트는 입력 단계에서 태그를 5개로 막아(`maxPostTags = 5`) 6개를 보내지 않고, 글 작성 화면은 이미 `TOO_MANY_TAGS` 전용 문구를 둔다(서버가 그 코드를 낸다고 가정한 주석까지 있다). D-05를 뒤집는다.
- 위반이 여럿인 요청의 첫 오류 코드 순서(D-38, 허용됨). 예: `sendMessage`에 확정되지 않은 사진과 없는 대화를 함께 보내면 `CONVERSATION_NOT_FOUND` 대신 `PHOTO_NOT_CONFIRMED` — R2 확인을 첫 쿼리 전으로 옮겼다(D-59).

## 이번 범위 아님

- SPEC-0020 빈 카테고리 거절 메시지의 내부 식별자 노출 수정(FR-004가 명시적으로 제외)
- `createPost` 태그 개수 초과 코드 통일(D-05) — 별도 spec
- 오류 코드 통합·auth `ErrorCode` enum 확장·tourism/community enum화(POL-016)
- 리졸버 payload 조립을 mapper로 이동(D-10), 생성 타입 생성자 호출 금지 센서
- 도메인 상수 한도를 설정값으로 승격
- 애그리거트 공통 기반 클래스, 애그리거트 안의 이벤트 수집(경계 재설계 자체는 D-40으로 범위 안)
- id의 `value class` 감싸기(`PostId` 등) — 필요해질 때 도입
- 탈퇴 정리의 일괄 처리(명세+명령) — 한 회원 분량이 대용량으로 확인될 때(D-65)
- 장소 답변·답글 id 혼동 해소(전역 id·종류 구별) — 스키마 변경이라 하지 않는다(D-55·D-56)
- 신고된 DM 보관(신고 시점 사본) — DB 변경이라 하지 않는다. 기존처럼 6개월 뒤 함께 파기(D-56)
- Exposed 스키마 검증이 못 잡는 컬럼 타입 비교기(D-35)
- Boot 4.2·graphql-java 26 업그레이드
- 이전 기간의 CLAUDE.md JPA 규칙 삭제 — 마지막 context를 옮긴 뒤 S16에서 한 번에
- 사용자 정의 스테레오타입 애노테이션(D-19)
- 도메인모델·용어사전 문서 — 리팩토링 완료 후 마지막에 별도로 작성(사용자 결정 2026-09-28)
- Instancio(무작위 테스트 데이터) — 리팩토링 완료 후 도입(사용자 결정 2026-09-28)
- `!!` 제거, `inline fun <reified T>` 1건, `MemberAgreementPersistenceAdapter`의 루프 조회 — 이 spec의 FR이 아니다
- `out/event` 리스너를 `adapter/in/event`로 옮기는 구조 결정, 리졸버의 감사 이벤트 발행 정리
- 리졸버·DataLoader·in/graphql 보조 파일의 이름 규칙
- 단일 구현 인터페이스 센서, 루프 안 외부 호출 센서(1차 조사에서 부적합 판정)
- `.graphqls` 스키마 설명 정리(D-30 — 클라이언트 계약)
- 인터페이스를 둔 전략 패턴 도입(D-06·D-07)
- `LongParameterList` 류의 파라미터 수 한도(FR-013이 요구하지 않음)
- SPEC-0038 추천 조회 성능 재측정(spec이 요구하지 않음)
- ktlint 커스텀 룰

## 설계

> **2026-09-30 범위 확장.** 아래 절(경계 불변·판정 분류·영속성 영향 없음)은 확장 전 설계다. 경계·영속성·서비스 모양은 D-32~D-44가 대체하며, context별 애그리거트 경계는 S12~S15 착수 시 합의해 이 문서에 추가한다.

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
| 2026-09-28 | D-06·D-07 뒤집음(Policy 제거·규칙 객체), D-23 확정(Q01), D-20 dev PR lint job, D-30·D-31 주석 정리·센서 추가 | 사용자 결정 |
| 2026-09-28 | splearn 비교 후 D-12·D-19 확인, 도메인 문서·Instancio를 리팩토링 후로 미룸 | 사용자 결정 |
| 2026-09-28 | C1·C2 완료 기록, D-31에 연속 `//` 포함 | 구현 결과 |
| 2026-09-28 | B1.5 리뷰 반영: 풀 증설 경고·LIMIT 1·FCM env 경고 복원, 의미가 바뀐 주석 2곳 수정, 센서 제외 판정을 루트 기준으로 | reviewer PASS-with-warnings |
| 2026-09-29 | D-25 뒤집음: VC 실행 도구를 서버 레포에서 빼고 ieuri-QA 스위트로 | 사용자 지시(직접 만든 도구 금지, 검증은 코드와 분리) |
| 2026-10-05 | D-65(탈퇴 정리: 리스너 → 애그리게이트 유스케이스 → 건별 도메인, D-61 일부 뒤집음) | 사용자 결정 |
| 2026-10-05 | D-64(장소 Q&A 글 종류를 도메인으로) | 사용자 승인 |
| 2026-10-05 | D-61(탈퇴 정리 단계)·D-62(raw SQL exposedJdbcTemplate, 사본 동기화)·D-63(조회 서비스 분할·패키지 정리) | 구현 결과 |
| 2026-10-05 | D-60(애그리게이트 안 자식 컬렉션은 작고 상한이 있을 때만 허용) | 사용자 결정 |
| 2026-10-05 | D-59(대화 루트 애그리게이트·메시지 신고·보관 파기 분리, D-45 일부 뒤집음) | 구현 결과 |
| 2026-10-05 | D-58(장소 Q&A는 질문 루트 애그리게이트 하나, D-45 일부·D-55 일부·D-57 뒤집음) | 사용자 승인 |
| 2026-10-05 | D-56(GraphQL·DB 불변, D-46 뒤집음)·D-57(장소 Q&A `qa` 패키지) | 사용자 지시 |
| 2026-10-04 | D-55(장소 Q&A 이전, 답글 대상 id 혼동은 스키마 변경이 필요해 남김) | 구현 결과 |
| 2026-10-04 | D-54(`@Transactional` 통일·외부 호출은 첫 쿼리 전·신고 숨김 규칙을 서비스로), D-51 뒤집음, `SpotChange` 제거, 도메인 파일 분리 | 사용자 지적 |
| 2026-10-04 | D-52(최소 적재·의도별 저장, 전달용 타입 없음)·D-53(`*PersistenceAdapter`). 하위 항목 편집 경로 실측(생성 정리·무변경 유지·교체·장소 뗌/붙임·위치 트리거) | 사용자 지적 |
| 2026-10-04 | D-48(기동 스키마 검증 삭제, D-35 뒤집음)·D-49(Exposed 풀 = 메인 풀 설정) | 사용자 결정 |
| 2026-10-01 | D-45(community 애그리거트)·D-46(신고 DM 로그, Q04 답)·D-47(이전 기간 센서) | 사용자 승인 |
| 2026-09-30 | S10·S11 완료. D-33 풀(더하기로 변경)·D-35 검증 방식·D-42 raw SQL 도구와 선언 규칙을 실측으로 확정 | 구현 결과 |
| 2026-09-30 | 범위 확장: D-32~D-44 추가(Exposed 이전·Boot 4.1·패키지·불변 data class·domain 판정·서비스 모양·애그리거트 규칙·mapper·결함 수정·단일 브랜치). D-01~D-04·D-12~D-16·D-29 뒤집음. S3~S7을 S12~S15에 흡수, S10~S16 추가. Q03(기한)·Q04(신고된 DM 보관) 추가 | 사용자 결정 — splearn 대조와 재설계 합의 |
