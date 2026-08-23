# SPEC-0020 서버 변경 — 클라이언트 대응 요청

서버 PR: ieuri-server#77 (`feature/SPEC-0020-category-multi-select`)

카테고리를 **enum에서 데이터로** 옮겼습니다. 앞으로 새 카테고리(예: "관광 촬영지")는 서버 DB에 행만 넣으면 **앱 업데이트 없이** 나갑니다. 그 대신 계약이 몇 군데 바뀝니다.

운영 중인 앱이 없어 하위 호환 없이 정리했습니다. 아래 4개가 전부입니다.

---

## 1. `SpotCategory`가 enum → object

```graphql
# 전
category: SpotCategory          # enum 값 하나

# 후
type SpotCategory {
  code: String!                 # 안정 식별자 (TOURIST_SPOT 등)
  label: String!                # 요청 언어 표시 문구 — 그대로 그리면 됨
  iconUrl: String               # 칩·마커 이미지, 없으면 null
}
```

`category`를 고르던 쿼리는 하위 필드를 명시해야 합니다.

```graphql
# 전
spots(...) { items { id title category } }

# 후
spots(...) { items { id title category { code label iconUrl } } }
```

**중요 — `code`로 분기하지 마세요.** `switch(code)`로 아이콘·이름을 고르면 서버가 새 카테고리를 내려보내는 순간 그것만 안 보입니다. `label`과 `iconUrl`을 **받아서 그리기만** 하면 새 카테고리가 저절로 나옵니다. 이게 이번 변경의 목적 전부입니다.

`iconUrl`은 아직 전부 `null`입니다(에셋 준비 전). null이면 기존 아이콘/기본 표현으로 떨어뜨려 주세요.

---

## 2. 카테고리 필터가 단수 → 복수 (6개 연산)

```graphql
# 전
spots(category: RESTAURANT, ...)
posts(category: RESTAURANT, ...)

# 후
spots(categories: ["RESTAURANT", "LODGING"], ...)      # String 배열
posts(categories: [RESTAURANT, LODGING], ...)          # TargetCategory 배열
```

대상: `spots` · `likedSpots` · `searchSpots` · `popularSpots` · `mapViewport` · `posts`

**규칙은 6개 모두 같습니다.**

| 보낸 값 | 결과 |
|---|---|
| 생략 / `null` | 필터 없음 (전체) |
| `["A", "B"]` | A **또는** B (OR) |
| `[]` (빈 배열) | **에러** `EMPTY_CATEGORY_SET` |
| 없는 코드 | 에러 아님, 결과 0건 |

빈 배열이 거절되는 이유는 "전체"인지 "결과 없음"인지 서버가 알 수 없기 때문입니다. **선택 0개 상태를 만들지 마시고**(마지막 하나는 해제 불가), 전체를 원하면 인자를 아예 빼 주세요.

> 관광지 쪽은 `[String!]`, 커뮤니티 쪽은 `[TargetCategory!]`로 타입이 다릅니다. 커뮤니티 카테고리는 아직 닫힌 enum이라 그렇습니다.

---

## 3. 카테고리 목록은 `availableSpotCategories`로

`SpotCategory`가 enum이 아니라 **스키마만 봐서는 어떤 값이 있는지 알 수 없습니다.** 앱 시작 시 한 번 조회해 칩·필터를 구성해 주세요.

```graphql
query {
  availableSpotCategories { code label iconUrl }
}
```

- 앱(`X-App-Key`)·언어(`Accept-Language`)마다 다릅니다
- **순서를 정해 내려주지 않습니다** — 배열 순서는 클라이언트가 정하세요
- 구성이 없으면 빈 목록이며 오류가 아닙니다

카테고리 목록을 앱에 하드코딩해 두셨다면 이 쿼리 결과로 바꿔 주세요.

---

## 4. `communityChips` 응답이 평평해짐

```graphql
# 전
communityChips { category position }     # [CommunityChip!]!

# 후
communityChips                            # [TargetCategory!]!
```

`position`을 없앴습니다 — 서버가 노출 순서를 정할 근거가 없어서, **순서는 클라이언트가 정합니다.** 필드가 `category` 하나만 남아 래퍼 타입도 없앴습니다.

---

## 헤더 (기존과 동일, 확인만)

```http
X-App-Key: k-trip-spot
Accept-Language: ko-KR
```

`X-App-Key`가 카테고리 구성을 고릅니다. **안 보내도 기본 앱으로 폴백해 정상 동작하므로 누락돼도 티가 안 납니다** — 실제로 보내고 있는지 한 번 확인해 주세요. 앱이 하나뿐인 지금은 무해하지만 두 번째 앱이 생기면 조용히 잘못된 구성을 받습니다.

---

## 요약 체크리스트

- [ ] `category` → `category { code label iconUrl }` (응답 필드)
- [ ] `category:` → `categories:` (필터 인자 6곳)
- [ ] 카테고리 목록 하드코딩 제거 → `availableSpotCategories`
- [ ] `communityChips`에서 `{ category position }` 제거
- [ ] 선택 0개 방지 (마지막 하나는 해제 불가)
- [ ] `code`로 분기하는 곳이 없는지 확인 (아이콘·이름은 서버 값 사용)
- [ ] `X-App-Key` 헤더 전송 확인

질문이나 안 맞는 부분 있으면 서버 쪽에 알려 주세요.
