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

`categories` 인자의 해석 규칙(null, 빈 배열)은 5개 연산에 **동일하게** 적용됩니다. 이 규칙을 5개 핸들러에 각각 두면 다섯 번 어긋날 기회가 생깁니다.

application 계층에 카테고리 선택을 표현하는 값 객체를 하나 두고, GraphQL 인자 → 값 객체 변환을 한 곳에서 수행합니다. 핸들러는 이미 해석된 값 객체만 받습니다.

- 빈 배열 거부(`EMPTY_CATEGORY_SET`)는 이 값 객체 생성 시점에서 일어납니다 — 실패를 경계에서 끝내고 도메인으로 들여보내지 않습니다.
- 생략/`null`/복수 값이 모두 이 한 지점에서 정규화됩니다.

값 객체는 도메인 모델이며 JPA 엔티티가 아닙니다.

### 필터는 상한 이전에 적용한다 (mapViewport)

`mapViewport`는 줌별 결과 상한과 `truncated` 플래그를 갖습니다(POL-037). 카테고리 조건은 상한을 적용하기 **전에** 질의 조건으로 들어가야 합니다.

상한을 먼저 적용하고 나서 카테고리로 거르면, 사용자가 보지도 않을 카테고리가 상한을 채운 뒤 잘려나가 `truncated`가 참인데 실제 관광지는 몇 개 없는 상태가 됩니다. 잘림 플래그의 의미가 무너집니다(spec R8/AC8.1).

상한 값 자체는 계약에 노출하지 않습니다(POL-037).

### OR 결합, 그리고 hasOrigin과의 AND

선택된 카테고리들은 OR입니다. 값이 하나여도 원소 1개인 집합이라 별도 분기 없이 같은 조건 생성기를 지납니다.

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

### 상세 하위 필드의 카테고리는 검증한다 (필터는 하지 않는다)

`intro(categoryCode:)`·`infoItems(categoryCode:)`는 코드가 **캐시 키와 적재 행의 일부**가 됩니다. 검증하지 않으면 비로그인 사용자가 임의의 문자열을 바꿔 가며 호출하는 것만으로 캐시 키와 빈 행이 무한히 쌓입니다(두 테이블에 CHECK도 FK도 없습니다).

그래서 이 두 곳만 `category` 테이블에 있는 코드인지 확인하고, 아니면 `UNKNOWN_CATEGORY`로 거절합니다. 유효 집합을 **코드가 아니라 테이블이** 정하므로 새 카테고리는 넣는 즉시 통과하며, 이 검증이 "데이터만으로 추가"를 되돌리지는 않습니다.

목록 필터에는 걸지 않습니다 — 없는 코드로 거른 결과는 0건일 뿐이라 해가 없고, 막으면 새 카테고리가 배포를 기다리게 됩니다.

### 카테고리 구조 정비 (V59, 2026-08-15 사용자 지적)

V58을 쓰고 나서 네 가지를 바로잡았습니다.

**대리 키를 둡니다.** V58은 `code`를 PK로 썼는데, 그러면 코드 문자열이 곧 신원이라 오타 하나 고치는 데 참조하는 모든 행을 함께 갱신해야 합니다. `id`를 분리하고 `code`는 UNIQUE로 남겨 조회 축을 유지합니다.

**외부 코드 매핑을 테이블로 내립니다.** 우리 카테고리는 우리만의 것이 아니라 TourAPI의 `contentTypeId`와 엮여 있고, 그 매핑이 `application.yaml`에 있었습니다. 설정에 있으면 신규 카테고리의 spot을 **수집**하려 할 때마다 배포가 필요합니다 — 저장·조회는 데이터로 되는데 수집만 배포가 필요한 반쪽이 됩니다.

`category_external_mapping(category_id, provider, codeset, external_code)`로 옮겼습니다. 별도 테이블인 이유:

- 매핑은 **(제공자, 코드셋)마다** 있고 **비어 있는 칸이 있습니다** — 여행코스는 외국어 서비스에, 교통은 국문 서비스에 대응 코드가 없습니다. 별도 테이블이면 그 비대칭이 "행이 없다"로 자연히 표현됩니다.
- TourAPI 아닌 제공자가 늘어도 컬럼이 아니라 행이 늘어납니다.
- 역방향(`외부 코드 → 우리 카테고리`)에도 UNIQUE를 걸어, 같은 외부 코드가 두 카테고리를 가리키는 상태를 DB가 막습니다.

**position을 뺍니다.** 서버가 노출 순서까지 정할 근거가 없습니다. 서버는 "이 앱·이 언어에 무엇을 보일 것인가"만 정하고, 배열 순서는 클라이언트가 자기 화면 기준으로 정합니다. `CommunityChip.position`도 함께 제거했습니다.

**`enabled` → `active`.** 상태를 나타내는 이름이 더 곧습니다.

### 테이블이 왜 셋인가 (2026-08-15 검토)

`category`·`category_translation`·`category_external_mapping` 셋이 많다는 지적이 있었고, 하나씩 따져 유지하기로 했습니다. 셋은 **서로 다른 축으로 늘어나기 때문에** 나뉘어 있습니다.

| 테이블 | 늘어나는 축 | 없앴다면 |
|---|---|---|
| `category` | 카테고리 자체 | — |
| `category_translation` | 카테고리 × **언어** | 라벨을 앱이 매핑 → 신규 카테고리에 앱 업데이트 필요 |
| `category_external_mapping` | 카테고리 × **(제공자, 코드셋)** | 설정으로 회귀 → TourAPI 코드 보정에 배포 필요 |

**`category_external_mapping`은 프론트로 옮길 수 없습니다.** `contentTypeId`는 서버가 TourAPI를 호출할 때만 쓰는 값이고 클라이언트 응답에 나가지 않습니다. 선택지는 "테이블이냐 설정이냐"뿐이었습니다.

**다만 이 테이블의 가치는 처음 생각보다 좁습니다.** 배치는 카테고리로 필터링하지 않고 전량을 긁은 뒤 `contentTypeId`를 읽기만 하며(`areaBasedSyncList2`), "관광 촬영지" 같은 신규 카테고리는 애초에 TourAPI에 없어 매핑 행이 있든 없든 수집 경로가 다릅니다. 실제로 사는 것은 **TourAPI가 코드 체계를 바꿨을 때 배포 없이 대응**하는 것입니다. 그 값이 테이블 하나를 유지할 만하다고 판단해 남깁니다(사용자 결정 2026-08-15).

`codeset`은 언어가 아니라 **제공자 안에서 코드가 갈리는 단위**입니다. TourAPI는 엔드포인트가 언어별로 4개(Kor/Eng/Jpn/Cht)이지만 `contentTypeId` 체계는 국문 1벌 + 외국어 공용 1벌로 2벌입니다 — 관광지가 KO에서 `12`, 외국어에서 `76`입니다. 언어별로 뒀다면 EN·JA·ZH가 같은 값을 세 번 저장하고 보정 시 셋을 다 고쳐야 합니다.

### 이 변경이 닿지 않는 곳

"데이터만으로 신규 카테고리"는 tourism 표면(목록·검색·지도·`availableSpotCategories`·적재·필터)에서 성립합니다. 두 가지는 아직 아니며, 가정하지 말고 알고 있어야 합니다.

- **커뮤니티** — `TargetCategory`는 여전히 닫힌 enum입니다. 새 카테고리로 글을 쓰거나 거를 수 없고, 칩에서도 조용히 빠집니다(`toChipViewOrNull`). 즉 `availableSpotCategories`에는 보이는데 커뮤니티 칩에는 없는 상태가 생길 수 있습니다.
~~- **TourAPI 적재**~~ — V59에서 해소되었습니다. 매핑이 `category_external_mapping` 행이 되어 수집도 배포 없이 됩니다.

## 테스트 대상 단위

**application / domain 계층만.** GraphQL resolver, JPA 리포지토리, 외부 API 어댑터, DB·컨테이너는 범위 밖입니다(포트는 목으로 대체).

| 단위 | 검증 내용 | 대응 AC |
|---|---|---|
| 카테고리 선택 값 객체 | 복수 값 정규화, 중복 제거, 생략·`null` = 필터 없음, 코드 목록 순서 고정 | AC5.3, AC5.4 |
| 카테고리 선택 값 객체 | 빈 배열 → `EMPTY_CATEGORY_SET` | AC6.1 |
| 조회 조건 생성 | 다중 카테고리 OR 결합 | AC2.3, AC5.1 |
| 조회 조건 생성 | 카테고리 집합과 `hasOrigin`의 AND 결합 | AC5.5 |
| 뷰포트 조회 핸들러 | 필터 → 상한 → 잘림 플래그 순서, 필터 후 상한 미만이면 `truncated` false | AC8.1 |
| GraphQL 인자 변환 | 문자열 → 값 객체, 알 수 없는 코드 통과, 빈 배열 → `EMPTY_CATEGORY_SET` | AC5.3, AC5.4, AC6.1 |
| `app_category` 엔티티 → read DTO | 필드별 변환, 표시 정의 없으면 코드가 라벨이 되는 폴백 | 추가 범위 |
| 카테고리 구성 → 커뮤니티 칩 | 필드별 변환, 커뮤니티가 아는 값 전부 대응, 미지원 값은 null | 추가 범위 |

`truncated` 순서 검증은 상한 값을 주입 가능하게 두면 application 계층에서 포트 목만으로 충분히 검증됩니다 — DB가 필요하지 않습니다.

## 범위 밖

- 페이지네이션 방식 변경 — 기존 연산의 커서/offset 규약을 건드리지 않습니다.
- `categories` 개수 상한 — **두지 않기로 확정**(spec §결정 사항). enum이 9개뿐이라 자연 상한이 낮고, 근거 없는 수치를 계약에 넣지 않습니다. 빈 배열만 R6으로 방어합니다.
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
