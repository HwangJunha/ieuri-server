# SPEC-0030 전국 지도 — 클라이언트 작업 지시

서버 구현이 끝났습니다. **최초 스펙(v1.0)에서 계약이 여러 번 바뀌었으니 이 문서를 기준으로
작업해 주세요.** 스펙은 v1.2로 개정되어 있고, 아래 내용이 그 요약입니다.

---

## 0. 가장 먼저 알아야 할 것 — 좌표 판정이 클라이언트로 왔습니다

**`resolveDistricts` API가 삭제됐습니다. 좌표를 서버로 보내지 않습니다.**

이유는 규모입니다. 사진첩에 좌표가 있는 사진이 수십만 장인 사용자가 있고, 1km 격자로
축약해도 이동 패턴에 따라 수천 격자가 남습니다 — 고속도로 400km를 달리며 찍으면 그것만으로
400격자입니다. 서버 왕복이 수십 회가 됩니다. POL-045 관점에서도 좌표가 서버 경계를 넘지
않는 편이 낫습니다(약속이 아니라 구조로 보장).

### 경계 데이터 (별도 전달)

- 파일: `district-boundaries.geojson` · **3.8MB** · GeoJSON FeatureCollection
- 252개 시군구, 전부 `MultiPolygon`
- **좌표 순서는 `[경도, 위도]`** (GeoJSON 표준) — 뒤집으면 전 국토가 엉뚱한 곳으로 갑니다
- **구멍(hole)이 있습니다.** `polygon[0]`이 외곽 링, `polygon[1:]`이 구멍입니다.
  외곽 안이어도 구멍 안이면 **미포함**으로 처리해야 합니다
- `properties`: `code`(시군구 5자리) · `areaCd`(시도 2자리) · `nameKo` · `nameEn` · `nameJa` · `nameZh`

**검증 완료** — 이 파일로 순수 point-in-polygon을 돌려 서버 PostGIS와 결과가 일치함을
확인했습니다: 서울시청 → `11140` 중구, 수원 → `41111` 장안구, 동해·도쿄 → 미판정.

단순화하지 않은 원본이라 서버가 하던 판정과 결과가 같습니다. 3.8MB 파싱 시간이 문제가 되면
그때 최적화를 논의하되, **정확도를 먼저 확인한 뒤에** 하십시오.

---

## 1. 두 종류의 식별자 — 혼동하면 조용히 틀립니다

| | 타입 | 예 | 쓰임 |
|---|---|---|---|
| **칸 id** | `ID!` | `1`, `161` | **화면 단위.** 상세 진입·해제·공유 |
| **시군구 코드** | `String` | `"11140"` | **저장 단위.** 로컬 판정 결과 |

- 등록(`recordVisits`)은 **시군구 코드**를 보냅니다
- 해제(`removeVisit`)는 **칸 id**를 보냅니다
- **비대칭이 의도입니다.** 사용자는 지도에서 칸을 눌러 해제하지만, 저장은 시군구 단위여야
  나중에 칸 규칙이 바뀌어도(예: 서울을 25칸으로 분할) 기록이 살아남습니다

칸 id는 **행정구역 코드가 아닙니다.** 서버가 정한 표시 단위라 대응하는 공식 코드가 없습니다.
시군구 코드는 `MapCell.districtCodes`로 따로 옵니다.

---

## 2. 확정된 API 7개

### 조회 (인증 선택 — 비로그인이면 `viewerVisited`가 전부 false)

```graphql
mapCells: [MapCell!]!
mapCell(id: ID!): MapCell!
sharedMap(token: String!): SharedMap!
```

### 뮤테이션 (전부 인증 필수)

```graphql
recordVisits(input: { districtCodes: [String!]! }): RecordVisitsPayload!
removeVisit(input: { cellId: ID! }): RemoveVisitPayload!
createMapShareLink: CreateMapShareLinkPayload!
revokeMapShareLink(input: { shareUrl: String! }): RevokeMapShareLinkPayload!
```

### MapCell

```graphql
type MapCell {
  id: ID!                          # 칸 식별자 (행정코드 아님)
  districtCodes: [String!]!        # 이 칸이 묶은 시군구 — 로컬 판정 결과를 칸에 대응시킬 때 씀
  name: String!                    # 활성 언어. 없으면 EN → KO 폴백
  provinceName: String!
  provinceCode: String!            # 시도 2자리 — 진척 계산에 씀
  center: Coordinates!             # 지도를 이 칸으로 옮길 때
  viewerVisited: Boolean!
  representativeImage: CellImage   # null일 수 있음
  spots(first: Int = 20, after: String): CellSpotConnection!
}
```

---

## 3. 사진 동기화 (R25~R30 개정)

```
앱 시작:  에셋에서 경계 로드 (1회)
   ↓
스캔:     사진첩 → EXIF 좌표
   ↓      한국 영역 밖 버림
   ↓      1km 격자 축약  ← 이제 전송량이 아니라 로컬 판정 횟수를 줄이는 최적화
   ↓      point-in-polygon 판정 → 시군구 코드 집합
   ↓      이미 방문한 시군구 제외
   ↓
확인:     "이 지역들 맞나요?" ← 필수. 확인 없이 자동 등록 금지(R30)
   ↓
서버:     recordVisits(districtCodes: [...])   ← 요청 1회
   ↓
갱신:     응답의 updatedCells로 지도 색칠 (재조회 불필요)
```

**사진이 몇 장이든 서버 요청은 1회입니다.** 판정 결과가 시군구 코드로 축약되어 최대 252개이기
때문입니다.

`updatedCells`는 **이번 요청으로 방문이 된 칸만** 담습니다. 같은 코드를 다시 보내면 멱등이라
오류는 아니지만 `updatedCells`가 비어 옵니다.

없는 시군구 코드가 섞이면 나머지는 정상 기록되고 `userErrors`에 `DISTRICT_NOT_FOUND`가
1건 담깁니다(어떤 코드였는지 메시지에 포함).

---

## 4. 현재 위치 등록 (R33~R35)

사진 동기화와 **같은 경로**이고 좌표만 1건입니다.

```
현재 위치 → 로컬 경계로 판정 → 시군구 코드 1개
  → 이미 방문인지 확인(R34) → 사용자 확인 → recordVisits
```

한국 밖이면 로컬 판정에서 아무 시군구도 안 나옵니다 → "국내만 등록할 수 있습니다"(R35).

---

## 5. 공유 — 링크는 서버가 완성합니다

**`createMapShareLink`가 완성된 URL을 줍니다. 조립하지 마세요.**

```graphql
type MapShareLink {
  shareUrl: String!    # https://www.ieuri.co.kr/tour/korea-map/kJ3xQ9mR7wZ2...
  createdAt: String!
}
```

### 링크 형태

```
https://www.ieuri.co.kr/tour/korea-map/<token>
```

**커스텀 스킴(`ieuri://`)을 쓰지 않습니다.** 카카오톡을 비롯한 메신저가 `http`/`https`만
링크로 인식해 커스텀 스킴은 눌리지 않기 때문입니다. 공유가 카톡으로 이뤄지는 이상 의미가
없습니다.

**앱은 이 경로를 App Link(Android) / Universal Link(iOS)로 가로채야 합니다.** 웹 페이지는
아직 없지만 나중에 같은 경로가 미설치자를 받게 됩니다 — 같은 링크로 두 경우가 처리됩니다.

도메인·경로가 바뀌어도 서버 설정만 바꾸면 되고 앱 배포가 필요 없습니다.

### 클라이언트가 할 일

| 상황 | 처리 |
|---|---|
| 공유 | `shareUrl`을 시스템 공유 시트에 **그대로** 전달 |
| 폐기 | 받았던 `shareUrl`을 **그대로** 되돌려줌 |
| 링크 열기 | 경로 **마지막 조각**을 꺼내 `sharedMap(token:)`에 넘김 |

마지막 조각을 꺼내는 방식이면 스킴이 바뀌어도 코드가 안 바뀝니다.

**딥링크 라우팅이 필요합니다** — SPEC-0028에서 만든 기존 인프라에 경로를 추가하십시오.

**현재 한계:** 웹 페이지가 아직 없어, 앱 미설치자가 링크를 열면 해당 URL에서 404를 봅니다.
링크 자체는 카톡에서 정상적으로 눌립니다. 웹 페이지가 생기면 그때 해소됩니다.

### SharedMap — null과 빈 배열이 다릅니다

```graphql
type SharedMap {
  ownerNickname: String!         # 닉네임만. 이메일 등 없음
  visitedCellIds: [ID!]!         # 상대가 방문한 칸
  overlappingCellIds: [ID!]      # 나와 겹치는 칸 — nullable!
  visitedCount: Int!
}
```

| `overlappingCellIds` | 뜻 | 화면 |
|---|---|---|
| `null` | 비로그인이라 비교 불가 | "로그인하면 겹치는 곳을 볼 수 있어요" |
| `[]` | 비교했는데 겹침 없음 | "겹치는 곳이 없네요" |

**둘을 같게 처리하면 안 됩니다.**

---

## 6. 알아둘 동작

**종로구를 가면 서울 칸 전체가 칠해집니다.**
칸에 속한 시군구 중 하나라도 방문이면 그 칸은 방문입니다(R6). 강남구 미방문은 지금 지도에서
표현되지 않습니다 — 의도된 동작입니다.

**`representativeImage`는 null일 수 있습니다.**
특산품 이미지 14건이 아직 확보되지 않아 실제로 발생합니다. 그 칸도 조회에서 빠지지 않고
이미지 필드만 null입니다(R10). 지역명만 표시하고 빈 칸으로 두지 마십시오.

**저작권 유형으로 렌더링이 갈립니다(R23).**

| `copyright` | 처리 |
|---|---|
| `TYPE1` | 크롭·리사이즈 가능 |
| `TYPE3` · `UNSPECIFIED` | **원본 비율 유지** |

`attributionText`는 유형과 무관하게 항상 노출합니다(R24).

**`spots`는 `mapCell`에서만 고르십시오.**
`mapCells`에서 함께 고르면 칸마다 질의가 나가 **161회**가 됩니다. 지도를 그릴 때는 고르지
않습니다.

**시도별 진척은 클라이언트 계산입니다(R21).**
161칸을 이미 전부 갖고 있으므로 `provinceCode`로 묶어 세면 됩니다. 서버가 같은 값을 다시
내려주지 않습니다.

---

## 7. 오류

| 위치 | 코드 |
|---|---|
| 최상위 `errors[]` | `UNAUTHENTICATED` · `MAP_CELL_NOT_FOUND` · `SHARE_LINK_NOT_FOUND` · `SHARE_LINK_BLOCKED` · `INVALID_CURSOR` |
| payload `userErrors[]` | `DISTRICT_NOT_FOUND` |

- **인증 실패는 항상 최상위** — payload에 담기지 않습니다(토큰 자동 갱신이 최상위를 봅니다)
- `SHARE_LINK_NOT_FOUND`는 **폐기됨과 없음을 구분하지 않습니다** (구분하면 토큰을 넣어보며
  그 사람이 공유했었는지 알아낼 수 있습니다)
- **삭제된 코드:** `INVALID_COORDINATES` · `TOO_MANY_COORDINATES` (좌표를 안 받으므로)

---

## 8. 최초 스펙에서 바뀐 것 (v1.0 → v1.2)

| 항목 | v1.0 | 지금 |
|---|---|---|
| 좌표 판정 | 서버 `resolveDistricts` | **클라이언트 로컬** |
| 경계 데이터 | 서버 보유 | **앱 에셋** |
| 칸 식별자 | `code: String!` (`"11"`) | **`id: ID!`** (`1`) |
| 공유 링크 | `token` | **`shareUrl`** (`https://www.ieuri.co.kr/tour/korea-map/<token>`) |
| 폐기 입력 | `token` | **`shareUrl`** |
| 해제 입력 | `cellCode` | **`cellId`** |
| 공유 응답 | `visitedCellCodes` | **`visitedCellIds`** |
| spot 노드 | `Spot` | **`CellSpot`** (거리 필드 없음) |

---

## 9. 서버가 배후에서 하는 일 (참고)

```
04:00  spotSyncJob                 TourAPI 수집
06:00  spotDistrictAssignmentJob   장소를 시군구에 귀속
```

`mapCell.spots`의 데이터 원천입니다. 새로 수집된 장소는 배치가 돈 뒤에 지역 상세에
나타납니다.
