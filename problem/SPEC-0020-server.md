> spec: SPEC-0020 · source: `Specs/SPEC-0020/spec.md` (Obsidian) · status: draft

요구사항·수용 기준·계약 초안은 위 spec을 따릅니다. 이 문서는 서버 설계 결정만 담습니다.

## 범위

spot 조회 5개 연산에 다중 카테고리 필터를 추가합니다. 스키마 변경은 가산적이며 breaking이 없습니다.

| 연산 | 페이지네이션 | 반환 타입 | `hasOrigin` |
|---|---|---|---|
| `spots` | offset (`page`/`size`) | `SpotPage!` | 있음 |
| `likedSpots` | cursor (`first`/`after`) | `LikedSpotConnection!` | 없음 |
| `searchSpots` | offset (`page`/`size`) | `SpotSearchPayload!` | 없음 |
| `popularSpots` | cursor (`first`/`after`) | `PopularSpotConnection!` | 있음 |
| `mapViewport` | 없음 (상한 + `truncated`) | `MapViewportResult!` | 있음 |

**기준 스키마는 SPEC-0019 병합 후(dev, PR #76)입니다.** 페이지네이션 형태가 연산마다 다르며 이 스펙은 어느 것도 변경하지 않습니다.

`posts(category: TargetCategory)`의 **필터 인자**는 이 스펙에서 다중 선택으로 넓히지 않습니다 — 다른 enum·다른 도메인입니다.

### 추가 범위 — 앱별 카테고리 구성 통합 (2026-08-15)

사용자 요청으로 범위가 하나 늘었습니다. `community_chip`을 폐기하고 **앱별 카테고리 구성을 한 테이블로 통합**합니다.

통합 전에는 "이 맥락에서 어떤 카테고리를 보여줄 것인가"의 답이 두 곳에 따로 있었습니다.

| 위치 | 형태 | 앱별 | 언어별 |
|---|---|---|---|
| `SpotCategory.availableIn(locale)` | 코드(enum의 `koreanOnly`/`foreignOnly`) | 불가 | 가능 |
| `community_chip` | DB 행 | 가능 | 가능 |

같은 9개 값을 쓰면서 노출 규칙만 갈라져 있어, 카테고리를 하나 조정하려면 코드와 데이터를 각각 고쳐야 했고 둘이 어긋나도 드러나지 않았습니다. `availableSpotCategories`는 앱을 구분하지도 못했습니다.

`app_category(app_key, language, category, position, enabled)` 하나가 두 곳의 유일한 출처가 됩니다(V57). 언어 비대칭도 코드가 아니라 행이 되므로 서버 배포 없이 조정됩니다.

## 설계 결정

### 카테고리 필터를 값 객체로 올린다

`categories`/`category` 두 인자의 해석 규칙(우선순위, null, 빈 배열)은 5개 연산에 **동일하게** 적용됩니다. 이 규칙을 5개 핸들러에 각각 두면 다섯 번 어긋날 기회가 생깁니다.

application 계층에 카테고리 선택을 표현하는 값 객체를 하나 두고, GraphQL 인자 → 값 객체 변환을 한 곳에서 수행합니다. 핸들러는 이미 해석된 값 객체만 받습니다.

- 빈 배열 거부(`EMPTY_CATEGORY_SET`)는 이 값 객체 생성 시점에서 일어납니다 — 실패를 경계에서 끝내고 도메인으로 들여보내지 않습니다.
- 생략/`null`/단일 `category`/복수 `categories`가 모두 이 한 지점에서 정규화되므로, 하위 호환 로직이 조회 로직에 섞이지 않습니다.

값 객체는 도메인 모델이며 JPA 엔티티가 아닙니다.

### 필터는 상한 이전에 적용한다 (mapViewport)

`mapViewport`는 줌별 결과 상한과 `truncated` 플래그를 갖습니다(POL-037). 카테고리 조건은 상한을 적용하기 **전에** 질의 조건으로 들어가야 합니다.

상한을 먼저 적용하고 나서 카테고리로 거르면, 사용자가 보지도 않을 카테고리가 상한을 채운 뒤 잘려나가 `truncated`가 참인데 실제 관광지는 몇 개 없는 상태가 됩니다. 잘림 플래그의 의미가 무너집니다(spec R8/AC8.1).

상한 값 자체는 계약에 노출하지 않습니다(POL-037).

### OR 결합, 그리고 hasOrigin과의 AND

선택된 카테고리들은 OR입니다. 단일 값 경로(`category`)는 원소가 1개인 집합과 동일하게 처리되어, 별도 분기 없이 같은 조건 생성기를 지납니다.

SPEC-0019가 추가한 `hasOrigin`은 카테고리 조건과 **AND**로 결합됩니다 — 카테고리 집합 안에서 원문 보유 여부를 다시 거르는 것이며, 두 필터는 서로의 의미를 바꾸지 않습니다. 조건 생성기가 두 필터를 각각 독립적으로 얹도록 두면 결합 순서에 의존하지 않습니다.

### 통합 테이블은 tourism이 소유하고, community는 port/in으로 읽는다

카테고리 축은 `SpotCategory`이며 관광지가 원래 소유한 개념입니다. 커뮤니티의 `TargetCategory`는 같은 9개 값을 옮겨 담은 것이고 칩은 그 축으로 글을 거르는 화면일 뿐이므로, 표의 주인은 tourism입니다.

- `app_category` 엔티티·리포지토리·조회 어댑터는 tourism에 둡니다.
- community는 `CategoryConfigPort`(자기 아웃바운드 포트)만 알고, 그 구현이 tourism의 `SpotQueryUseCase.availableCategories`를 호출합니다. `SpotLookupAdapter`가 이미 쓰는 모양 그대로이며, community의 application·domain은 tourism이라는 이름조차 모릅니다.
- 두 도메인이 각자 배포되려면 한쪽의 확장이 다른 쪽의 실패가 되면 안 되므로, tourism에만 있는 카테고리는 커뮤니티 칩에서 **조용히 빠집니다**(`toChipViewOrNull`). 칩 하나 때문에 커뮤니티 화면 전체가 오류가 되는 것이 더 나쁩니다.

`AppKey`는 두 도메인이 함께 쓰므로 `community/domain/vo`에서 `common/context`로 옮겼습니다. 헤더(`X-App-Key`) 추출도 `common/graphql`의 `acceptLanguage()` 옆으로 옮겨 한 벌만 남깁니다.

### availableSpotCategories의 의미가 바뀐다

"현재 언어에서 **유효한**" 목록에서 "이 앱·이 언어에 **노출하기로 구성된**" 목록이 됩니다. 반환 타입(`[SpotCategory!]!`)과 필드 이름은 그대로라 클라이언트 계약은 깨지지 않으며, 구성 순서대로 내려가므로 클라이언트가 다시 정렬하지 않습니다.

구성이 없으면 빈 목록이며 오류가 아닙니다 — 알 수 없는 앱 키가 그대로 도착할 수 있기 때문입니다.

### 카테고리를 enum에서 데이터로 옮긴다 (2026-08-15, 사용자 요청)

목표는 **신규 카테고리에 앱 업데이트도 서버 배포도 필요 없게** 하는 것입니다. "관광 촬영지" 같은 카테고리가 생겼을 때 데이터만 넣으면 끝나야 합니다.

앱 업데이트만 없애는 것으로는 부족했습니다. 표시 정보를 서버가 내려주면 프론트 하드코딩은 사라지지만, `SpotCategory`가 **닫힌 enum**이라 새 값은 여전히 서버 배포를 요구했습니다 — Kotlin enum, GraphQL enum, CHECK 제약 3개에 같은 9개 값이 박혀 있었고, `availableSpotCategories`에 모르는 코드를 넣으면 `valueOf`가 터졌습니다.

그래서 enum을 폐기합니다.

| | 전 | 후 |
|---|---|---|
| Kotlin | `enum class SpotCategory` | `value class SpotCategory(code: String)` |
| GraphQL | `enum SpotCategory` | `type SpotCategory { code, label, iconUrl }` |
| 필터 인자 | `[SpotCategory!]` | `[String!]` |
| 값 목록 | CHECK 제약 3곳 | `category` 테이블 + FK |
| 표시 문구 | 앱 하드코딩 | `category_translation` |

신규 카테고리 = `category`·`category_translation`·`app_category`에 INSERT. 코드 변경 없음.

**표시 문구를 서버가 갖는 것은 POL-023의 예외입니다.** enum 값은 식별자이지 표시 문자열이 아니라는 원칙 자체는 유지되어 — 그래서 `code`와 `label`을 분리했습니다 — 다만 앱이 *모르는* 카테고리의 라벨까지 앱이 가질 수는 없습니다. 이 예외가 없으면 이 변경의 목적이 사라집니다. 정책 승격 요청서가 필요합니다.

### 언어 가용성 검증을 없앤다

`CATEGORY_NOT_AVAILABLE_FOR_LOCALE`(여행코스=KO 전용, 교통=외국어 전용)은 enum에 박힌 규칙이었습니다. 카테고리가 열린 값이 되면 이 규칙을 코드가 알 수 없고, 안다면 그것이 곧 새 카테고리마다 배포가 필요하다는 뜻입니다.

없는 카테고리로 거른 조회는 **결과가 0건일 뿐**이며 그것으로 충분합니다. 노출 구성은 이미 `app_category`가 언어별로 갖고 있어, 클라이언트는 애초에 그 언어에 없는 칩을 그리지 않습니다.

### 표시 정보는 필드 리졸버가 붙인다

`SpotCategory.label`·`iconUrl`은 `@DgsData` 필드 리졸버입니다 — `{ category { code } }`만 물으면 카테고리 테이블을 읽지 않습니다.

한 응답에 같은 카테고리가 수십 번 나오므로(카드 100장에 카테고리는 몇 종류뿐) 요청 컨텍스트에 코드별로 접어 둡니다. DataLoader를 쓰지 않은 이유는 조회가 이미 코드 집합 단위라 요청당 1회로 접히고, 로더 컨텍스트 배선이 그만한 값을 하지 않기 때문입니다.

### `category` 인자는 제거한다 (deprecated 아님)

운영 중인 클라이언트가 없으므로 하위 호환 인자를 남길 이유가 없습니다. `@deprecated`로 두면 지워야 할 코드가 스펙 하나를 더 기다립니다.

### deprecation

`category`는 `@deprecated`로 남기되 동작은 그대로입니다. 제거 시점은 spec의 미결 사항이며 이 스펙에서 정하지 않습니다. 제거 전에 실제 사용량 확인이 필요합니다.

## 테스트 대상 단위

**application / domain 계층만.** GraphQL resolver, JPA 리포지토리, 외부 API 어댑터, DB·컨테이너는 범위 밖입니다(포트는 목으로 대체).

| 단위 | 검증 내용 | 대응 AC |
|---|---|---|
| 카테고리 선택 값 객체 | 복수 값 정규화, 단일 `category` 흡수, 우선순위(`categories` 우선), 생략·`null` = 필터 없음 | AC5.2, AC5.3, AC5.4 |
| 카테고리 선택 값 객체 | 빈 배열 → `EMPTY_CATEGORY_SET` | AC6.1 |
| 조회 조건 생성 | 다중 카테고리 OR 결합 | AC2.3, AC5.1 |
| 조회 조건 생성 | 카테고리 집합과 `hasOrigin`의 AND 결합 | AC5.5 |
| 뷰포트 조회 핸들러 | 필터 → 상한 → 잘림 플래그 순서, 필터 후 상한 미만이면 `truncated` false | AC8.1 |
| GraphQL 인자 변환 | 생성 타입 → 값 객체, 빈 배열 → `EMPTY_CATEGORY_SET` | AC5.2~5.4, AC6.1 |
| `app_category` 엔티티 → read DTO | 필드별 변환 | 추가 범위 |
| 카테고리 구성 → 커뮤니티 칩 | 필드별 변환, 9개 값 전부 대응, 미지원 값은 null | 추가 범위 |

`truncated` 순서 검증은 상한 값을 주입 가능하게 두면 application 계층에서 포트 목만으로 충분히 검증됩니다 — DB가 필요하지 않습니다.

## 범위 밖

- 카테고리 표시 문자열·번역 — enum은 식별자이며 번역하지 않습니다(POL-023). 라벨은 클라이언트 책임입니다.
- 페이지네이션 방식 변경 — 기존 연산의 커서/offset 규약을 건드리지 않습니다.
- `categories` 개수 상한 — **두지 않기로 확정**(spec §결정 사항). enum이 9개뿐이라 자연 상한이 낮고, 근거 없는 수치를 계약에 넣지 않습니다. 빈 배열만 R6으로 방어합니다.
- `category` 인자 제거 — **이 스펙에서 하지 않습니다.** 제거는 breaking이며 배포된 클라이언트의 실사용량 확인이 선행되어야 합니다. 별도 스펙으로 분리합니다.
- `posts(category:)`의 다중 선택 — 커뮤니티 글 필터는 넓히지 않았습니다. 통합한 것은 **카테고리 구성(무엇을 노출할 것인가)**이지 필터 인자가 아닙니다.
- 카테고리 구성 관리 화면·API — 지금은 시드(`R__seed_community.sql`)로만 넣습니다. 운영 중 조정 수단은 필요해지면 별도로 다룹니다.

## 변경 이력

| 날짜 | 변경 | 트리거 |
|---|---|---|
| 2026-08-15 | 다중 카테고리 필터 구현. 5개 연산에 `categories` 추가, 6개 조건 생성 지점을 `=`에서 `IN`으로 전환 | SPEC-0020 |
| 2026-08-15 | **범위 추가** — `community_chip` 폐기, `app_category`로 앱별 카테고리 구성 통합(V57). `availableSpotCategories`가 앱을 구분하고 DB 구성을 따르게 됨 | 사용자 요청 |
| 2026-08-15 | **범위 추가** — 카테고리 enum 폐기(V58). `category`·`category_translation` 테이블이 정의와 표시 문구를 갖고, GraphQL `SpotCategory`가 enum에서 type이 됨. 신규 카테고리가 데이터만으로 추가됨 | 사용자 요청 |
| 2026-08-15 | `category` 단수 인자를 deprecated 없이 **제거**. 운영 중인 클라이언트가 없어 하위 호환 인자를 남길 이유가 없음 | 사용자 요청 |
| 2026-08-15 | `CATEGORY_NOT_AVAILABLE_FOR_LOCALE` 검증 제거 — enum에 박힌 규칙이라 열린 카테고리와 양립하지 않음. 없는 카테고리 필터는 결과 0건으로 충분 | 위 변경의 귀결 |
