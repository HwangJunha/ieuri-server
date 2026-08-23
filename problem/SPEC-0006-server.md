> spec: SPEC-0006 · source: `Specs/SPEC-0006/spec.md` (Obsidian) · status: draft

# SPEC-0006 — 서버 설계

## 1. 이 저장소의 범위

요구사항 **R9·R10 두 건**만 이 저장소에 닿는다. 나머지 10건(R1~R8, R11, R12)은 k-trip-spot 단독이다.

변경의 성격은 **스키마 nullability 완화 하나**이며, 새 도메인 개념·애그리거트·커맨드·이벤트·영속성
변경이 없다. tourism 도메인의 기존 조회 경로를 그대로 두고 실패 전파 방식만 바꾼다.

## 2. 문제 — non-null 전파가 지연 조회를 무력화한다

SPEC-0003의 [AMEND 정정]은 상세 조회를 **필드별 지연 조회**로 재설계했다. `gallery`·`intro`·
`infoItems`는 요청된 경우에만 각자의 upstream 오퍼레이션을 호출한다. 의도는 실패를 필드 단위에
가두는 것이었다.

그런데 `gallery: [SpotImage!]!`와 `infoItems: [SpotInfoItem!]!`가 non-null이고 루트
`spotDetail: SpotDetail!`도 non-null이다. GraphQL의 null 전파 규칙상 하위 리졸버가 오류를 던지면
null을 담을 수 있는 가장 가까운 조상까지 거슬러 올라가는데, 여기엔 그런 조상이 없어 **오류가 쿼리
루트까지 올라가 `data.spotDetail` 전체가 null이 된다.**

결과적으로 `detailImage2` 한 번의 실패가 이미 성공적으로 받아온 `detailCommon2` 본문(제목·소개글·
주소·연락처)까지 통째로 버린다. 지연 조회로 얻으려던 격리가 계약 형태 때문에 성립하지 않는다.

SPEC-0003은 이를 `[TBD-3]`으로 남겨두고 두 선택지를 제시했다 — (a) 두 필드를 nullable로 완화, (b)
전체 실패를 의도된 동작으로 확정. **(a)로 결정되었다.**

## 3. 변경 — 두 필드의 nullability 완화

| 필드 | 현재 | 변경 후 | 근거 |
|---|---|---|---|
| `SpotDetail.gallery` | `[SpotImage!]!` | `[SpotImage!]` | R9 |
| `SpotDetail.infoItems` | `[SpotInfoItem!]!` | `[SpotInfoItem!]` | R10 |
| `SpotDetail.intro` | `SpotIntro` | 변경 없음 | 이미 nullable |
| `SpotDetail.likeCount` | `Int!` | 변경 없음 | 아래 §3.1 |
| `Query.spotDetail` | `SpotDetail!` | 변경 없음 | 아래 §3.2 |

리스트 요소는 non-null을 유지한다(`[SpotImage!]`). 목록 자체가 없을 수는 있어도 목록 안에 null
원소가 섞이는 상태는 의미가 없다.

### 3.1 `likeCount`를 완화하지 않는 이유

DB 집계이지 upstream 호출이 아니다. TourAPI의 rate limit·타임아웃·XML 파싱 실패 같은 실패 모드가
없고, DB 조회가 실패하는 상황은 이 필드 하나보다 훨씬 넓은 범위를 무너뜨린다. 인프라 장애를 계약상의
nullable 양보로 모델링하지 않는다는 것이 이 플랫폼의 기존 방향과도 일치한다.

검토 후 명시적으로 제외한 것이지 누락이 아니다.

### 3.2 루트를 완화하지 않는 이유

`spotDetail` 루트의 실패는 `SPOT_NOT_FOUND`이거나 본문(`detailCommon2`) 조회 실패다. 본문이 없으면
상세 화면이 성립하지 않으므로 전체 실패가 올바른 표현이다. R9가 요구하는 것은 "본문이 성공했을 때
하위 실패가 본문을 죽이지 않을 것"이며, 본문 자체의 실패는 범위 밖이다.

## 4. 오류 표면

하위 필드 리졸버가 실패하면 GraphQL 규약에 따라 다음이 성립해야 한다.

- `data.spotDetail`의 해당 필드는 `null`
- `data.spotDetail`의 나머지 형제 필드는 채워진 채 유지
- 최상위 `errors[]`에 그 필드를 가리키는 `path`(예: `["spotDetail", "gallery"]`)를 담은 항목 1건 추가

클라이언트는 `errors[]`가 비어있지 않다는 사실만으로 전체 실패를 단정하지 않고 `path`로 범위를
판별한다(AC-29·AC-30). 서버가 보장해야 하는 것은 **`path`가 실패한 필드를 정확히 가리키는 것**이다.

### 4.1 빈 배열과 null을 구분하는 수단을 추가하지 않는다

`gallery`가 `[]`(정상, 이미지 없음)인 경우와 `null`(조회 실패)인 경우를 클라이언트가 **동일하게**
처리하기로 결정했다 — 둘 다 `thumbnailImageUrl` 1장으로 폴백한다(AC-31·AC-32).

따라서 상태 enum, 구분 필드, 오류 payload를 **추가하지 않는다.** 이는 누락이 아니라 결정이며, 구현
중 "클라이언트가 둘을 구분하지 못하니 필드를 추가하자"는 판단이 서면 그것은 스펙과의 불일치이므로
`[AMEND]`로 보고한다.

### 4.2 `UPSTREAM_*` 코드는 전제하지 않는다

`UpstreamRateLimitedException`·`UpstreamUnavailableException`이 `TourismExceptions.kt`에 존재하나,
`SpotQueryService`의 지연 조회 4개 메서드 어디에서도 upstream 실패가 이 예외로 변환되지 않는다
(SPEC-0003 AC-3/15/16/17 미구현). 이 스펙은 두 코드의 존재를 전제하지 않으며, **이 스펙에서 구현을
요구하지도 않는다.** 별개 사안이다.

## 5. 영향 범위

- **도메인 계층** — 변경 없음. 도메인 모델의 형이 바뀌지 않는다.
- **애플리케이션 계층** — `SpotQueryService`의 지연 조회 메서드가 실패를 어떻게 표면화하는지가
  바뀐다. 조회 실패를 삼켜 빈 값으로 대체하지 **않는다** — 그러면 클라이언트가 실패를 알 수 없고
  `errors[]` 항목도 생기지 않는다.
- **영속성** — 변경 없음. 스키마 마이그레이션 없음.
- **캐시** — 변경 없음. 오퍼레이션별 캐시 키 분리와 TTL은 SPEC-0003 그대로다. 다만 **실패한 조회
  결과를 캐시에 적재하지 않는다** — 실패가 TTL 동안 고정되면 부분 실패가 복구되지 않는다.
- **이벤트** — 없음.
- **트랜잭션 경계** — 없음. 조회 전용이다.

## 6. 배포 순서

`gallery`·`infoItems`가 nullable이 되면 기존 클라이언트의 생성 타입은 non-null을 기대한 채로 남는다.
컴파일 타임에 드러나는 형 불일치이며 런타임 침묵 실패가 아니라 상대적으로 안전하지만, k-trip-spot
#26이 이 변경을 흡수한 뒤 배포되는 편이 자연스럽다.

두 번째 소비자가 생기기 전에 배포되어야 한다는 점이 breaking으로 분류하지 않은 근거의 일부이므로,
배포 시점에 `tourism.graphqls`의 소비자가 여전히 k-trip-spot 하나인지 확인한다.

## 7. 테스트

application 계층 단위 테스트로 다룬다. 포트는 스텁한다.

| 단위 | 검증 | 대응 AC |
|---|---|---|
| `SpotQueryService` 상세 조회 | 갤러리 포트가 실패를 던져도 본문 필드가 채워진 결과가 반환된다 | AC-27 |
| `SpotQueryService` 상세 조회 | 이용안내 포트가 실패를 던져도 결과가 오류로 대체되지 않는다 | AC-28 |
| `SpotQueryService` 상세 조회 | 갤러리·이용안내 포트가 모두 실패해도 본문이 반환된다 | AC-34 |
| `SpotQueryService` 상세 조회 | 본문 포트가 실패하면 전체가 실패한다 (부분 실패로 격하되지 않는다) | AC-30 |
| 캐시 적재 경로 | 실패한 조회 결과가 캐시에 적재되지 않는다 | §5 |

`errors[]`의 `path` 형태 자체는 DGS 런타임이 결정하는 부분이라 GraphQL 전송 계층을 세워야 관찰
가능하다. 이 플랫폼의 테스트 범위(adapter·infra 제외)를 벗어나므로 **단위 테스트로 명세하지 않고
남긴다.** application 계층에서는 "실패가 삼켜지지 않고 전파된다"까지만 검증한다.
