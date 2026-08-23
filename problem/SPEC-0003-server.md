> spec: SPEC-0003 · source: `Specs/SPEC-0003/spec.md` (Obsidian) · status: draft (v1.1, 개정 반영됨)

# SPEC-0003 · 서버 설계 — 관광지 상세보기

요구사항·수용 기준·컨트랙트는 스펙이 authority다. 이 문서는 **ieuri-server에 국한된 설계 결정**만 담는다.
반복하지 않고 링크한다: 컨트랙트 초안 `Specs/SPEC-0003/contract.graphql`, 수용 기준(AC-1~32)·오류 코드·정책 판정은 `spec.md`.

관련 이슈: #23. 브랜치: `feature/SPEC-0003-spot-detail` (dev에서 컷).
선행: SPEC-0002가 세운 tourism 도메인·`TourInfoPort` 격리·`spot_reaction` 저장·언어 라우팅을 그대로 딛는다. 여기서는
그 위에 얹히는 상세 조회만 설계한다.

> **v1.1 개정(2026-08-02, 이슈 #23 [AMEND 정정])** — 상세를 **필드별 지연 조회**로 재설계했다. 루트는
> `detailCommon2` 1콜만 하고, `gallery`·`intro`·`infoItems`는 선택될 때만 각각 1콜을 추가한다. 포트·캐시가
> 오퍼레이션 단위로 갈라졌다(§2·§4). 최초 [AMEND]가 제안했던 `spotDetail(id, category)` 루트 인자와 "진입당
> 항상 4콜" 고정은 철회됐다 — 루트 시그니처는 초안 그대로이고 계약은 additive다.

---

## 1. 헥사고날 배치 (SPEC-0002에 추가)

```
adapter/in/graphql        SpotDetailDataFetcher  (DGS, 신규) — 루트 + 필드별 @DgsData(지연 조회)
adapter/out/tourapi       TourApiClient — detailCommon2·detailImage2·detailIntro2·detailInfo2 메서드 추가
                          (함정 5종 기존 전담 재사용) + 카테고리별 intro 필드 정규화 매핑
adapter/out/cache         SpotDetailCacheAdapter (Valkey, 신규) — 오퍼레이션별 캐시
application/query          GetSpotDetailQueryHandler (읽기, 신규) — 오퍼레이션 단위로 분해
domain/tourism            SpotDetail(도메인 모델, 신규), SpotImage·SpotIntro·SpotInfoItem(값객체, 신규)
                          — 기존 Spot/SpotReaction 재사용
port/out                  TourInfoPort.findDetailCommon/findImages/findIntro/findInfoItems 추가
                          · SpotReactionPort.countLikes(...) 추가
```

**격리 원칙은 SPEC-0002와 동일**(§1 참조): 도메인·GraphQL은 TourAPI 필드명(`overview`, `homepage`, `tel`,
`originimgurl`, `smallimageurl`, `imgname`, `cpyrhtDivCd`, 카테고리별 `usetime`/`opentimefood`/`usetimeculture`,
`infoname`/`infotext`)과 `contentTypeId` 원본 코드를 직접 알지 않는다. 어댑터가 도메인 모델로 매핑한다(R8·AC-45).

## 2. TourInfoPort 확장 (도메인 지향, 오퍼레이션 단위)

지연 조회(spec R7)를 하려면 포트가 **오퍼레이션 단위로 갈라져 있어야 한다.** 단일 `findDetail`이 전부를 조립하면
호출부가 "본문만" 요청할 방법이 없다.

```
interface TourInfoPort {
  // ... 기존 findNearby, availableCategories ...
  fun findDetailCommon(q: SpotDetailQuery): SpotDetailCommon           // 신규. 없으면 도메인 NotFound.
  fun findImages(q: SpotDetailQuery): List<SpotImage>                  // 신규. 없으면 빈 목록.
  fun findIntro(q: SpotDetailQuery, c: SpotCategory): SpotIntro?       // 신규. 빈 껍데기면 null.
  fun findInfoItems(q: SpotDetailQuery, c: SpotCategory): List<SpotInfoItem>  // 신규. 없으면 빈 목록.
}
```

- `SpotDetailQuery`: contentId, locale(요청 스코프 `LocaleContext`에서). 언어별 base-url 선택은 SPEC-0002 설정 재사용.
- 각 메서드는 **자기 오퍼레이션의 정규화를 끝낸** 도메인 값을 반환한다: 이미지 https 정규화·10장 컷, 빈문자열
  null화, 저작권 문구 조립, intro 카테고리별 필드 매핑, info 불완전 항목 제외.
- `findIntro`/`findInfoItems`는 **카테고리를 인자로 받는다**(spec R15). 서버가 `findDetailCommon` 결과에서 꺼내
  되먹이지 않는다 — 그러면 본문 콜에 대한 순차 의존이 생겨 지연 조회의 이점이 사라진다.
- **현재 언어에 카테고리 코드가 없으면**(예: EN에서의 `TRAVEL_COURSE`) 어댑터가 upstream을 호출하지 않고
  `null`/빈 목록을 반환한다(AC-40). 이 판정은 카테고리→`contentTypeId` 변환표가 있는 어댑터의 책임이다.
- `likeCount`는 포트에 포함하지 않는다 — upstream이 아니라 DB 집계다(아래 5).

## 3. 상세 조회 파이프라인 (필드별 지연 조회 → 정규화 → 캐시)

**루트 데이터페처는 `detailCommon2` 1콜만 한다.** 나머지 오퍼레이션은 각 필드의 데이터페처가 자기 필드가
**선택됐을 때만** 발사한다. DGS에서 `SpotDetail.gallery`/`intro`/`infoItems`를 각각 `@DgsData`로 두면
필드가 쿼리에 없을 때 그 리졸버가 호출되지 않으므로, 지연 조회는 프레임워크가 보장한다.

| 단계 | 위치 | 트리거 | 근거 |
|---|---|---|---|
| `detailCommon2` 1콜 (overview·homepage·tel·addr·cpyrhtDivCd) | **adapter/out/tourapi** | 루트(항상) | TourAPI 세부는 어댑터에 가둠. |
| `detailImage2` 1콜 (다중 이미지) | **adapter/out/tourapi** | `gallery` 선택 시 | AC-33·AC-34. |
| `detailIntro2` 1콜 (운영 정보) | **adapter/out/tourapi** | `intro` 선택 시 | 카테고리 인자 필요(R15). |
| `detailInfo2` 1콜 (이용안내 반복 항목) | **adapter/out/tourapi** | `infoItems` 선택 시 | 상동. |
| 이미지 http→https, 10장 컷, TourAPI 순서 유지 | **adapter** | — | 데이터 정제. gallery는 반환 순서 앞 10장(AC-7·AC-8). 정렬 키 없음. |
| overview 등 빈 문자열 → null 정규화 | **adapter** | — | AC-3. TourAPI `items` 빈 문자열/단건 객체화 함정은 기존 §5 로직 재사용(AC-10). |
| 카테고리별 intro 필드 → 공통 이름 정규화 | **adapter → domain** | — | AC-36·AC-37. 아래 3.1. |
| intro 전 필드 null → `intro` 자체 null | **adapter** | — | AC-38. 빈 껍데기 금지. |
| info 항목명·내용 중 하나라도 빈 항목 제외 | **adapter** | — | AC-42. 둘 다 non-null이라 제외 외 선택지 없음. |
| 이미지별 cpyrhtDivCd → CopyrightType + attributionText | **adapter → domain** | — | 이미지별 저작권(SpotImage). Spot 상속 아님(contract 근거). |
| 미존재 contentId → 도메인 NotFound → `SPOT_NOT_FOUND` | **adapter/in** | 루트 | AC-15·AC-6. 언어 교차 id도 upstream이 못 찾으면 여기로. |

> **콜 수는 고정이 아니다(R7)**: 본문만 요청하면 1콜, 전 필드를 요청하면 4콜. 요청되지 않은 필드의 오퍼레이션은
> 호출되지 않는다(AC-33). 선택된 필드의 오퍼레이션은 정확히 1회씩만 호출된다(AC-34). 모두 캐시 미스에서만 발생한다.
>
> **지연 조회는 컴파일로 증명되지 않는다.** 필드 리졸버를 추가해 놓고 루트에서 미리 다 불러버려도 타입은 통과한다.
> 실제 GraphQL 쿼리를 실행해 **오퍼레이션별 호출 횟수를 세는 테스트**가 이 설계의 유일한 안전장치다(AC-33·AC-34).

### 3.1 intro 카테고리별 정규화

`detailIntro2`는 카테고리마다 응답 필드명이 완전히 다르다. 어댑터가 카테고리별 매핑표를 갖고 공통 도메인 필드로
옮긴다. 매핑표는 **어댑터에만** 존재하며 도메인·GraphQL은 원본 필드명을 모른다(R8·AC-45).

- 공통 필드: `businessHours` · `closedDays` · `parking` · `inquiryPhone` · `fee` · `petAllowed` · `creditCardAccepted`
- 해당 카테고리에 개념이 없으면 그 필드는 null(AC-37). 전부 null이면 `intro` 자체가 null(AC-38).
- 값은 TourAPI 원문 **문장**을 그대로 옮긴다(`String`). 반려동물·카드 가능 여부를 Boolean으로 접지 않는 이유는
  원문 값 집합이 확정되지 않았기 때문 — enum/Boolean화는 실데이터 표본 확인 후 별도 결정(spec POL-023 판정).
- **카테고리 고유 필드**(숙박 `checkintime`, 음식점 `firstmenu` 등)는 공통 틀에 없으므로 버린다(spec §제외).

## 4. 캐싱 (Valkey) — 오퍼레이션별 분리 + likeCount 경계 (R7·R11)

**이번 스펙에서 캐시는 필수다**(SPEC-0002의 "선택" 캐시와 다름). 이슈 #16이 이관하는 Valkey 인프라와 동일 인스턴스를 쓴다
(신규 인프라 아님).

**통짜 캐시로는 지연 조회를 표현할 수 없다.** 단일 키에 상세 전체를 넣으면 "본문만 캐시되고 갤러리는 아직"이라는
상태가 표현 불가능하고, 결국 부분 히트가 사라져 지연 조회의 이점이 캐시 계층에서 상쇄된다. 키를 오퍼레이션별로 나눈다.

```
ieuri:tourism:spot:{operation}:{language}[:{category}]:{contentId}    TTL 24h
```

| 오퍼레이션 | 키에 category 포함 | 근거 |
|---|---|---|
| `common` | 아니오 | 응답이 카테고리에 무관. |
| `image` | 아니오 | 상동. |
| `intro` | **예** | 카테고리가 응답 필드 구성을 가른다. |
| `info` | **예** | 상동. |

```
루트 리졸버:      common  = cache.getOrLoad(common:{lang}:{id})        { port.findDetailCommon(..) }
gallery 리졸버:   images  = cache.getOrLoad(image:{lang}:{id})         { port.findImages(..) }
intro 리졸버:     intro   = cache.getOrLoad(intro:{lang}:{cat}:{id})   { port.findIntro(.., cat) }
infoItems 리졸버: items   = cache.getOrLoad(info:{lang}:{cat}:{id})    { port.findInfoItems(.., cat) }
likeCount 리졸버: spotReactionPort.countLikes(contentId, locale)       # 항상 실시간, 캐시 무관 (R11·AC-14)
```

- **캐시 대상 = TourAPI 파생 필드만**. **likeCount는 절대 캐시하지 않는다**(AC-14: 캐시된 상태에서 LIKE +1 후
  재조회 시 현재값 반환). 이 경계가 캐시 설계의 핵심이며 오퍼레이션 분리 후에도 불변이다.
- **언어를 키에 넣는 이유**: contentId가 언어별로 갈리므로 KO/EN 상세가 섞이면 안 된다(AC-19).
- **null과 미스를 구분해야 한다(AC-35)**: `intro`는 정상적으로 null일 수 있다. "캐시에 없음"과 "캐시된 값이
  null"을 구분하지 않으면 운영 정보가 없는 spot마다 **매 조회 upstream을 두드린다** — 캐시가 가장 필요한
  케이스에서 정확히 무력해진다. 빈 값을 나타내는 sentinel을 저장하거나 존재 여부를 별도로 확인한다.
- **TTL 24h**: 준정적 콘텐츠라 최대 24h 지연 감수. 만료는 오퍼레이션별로 따로 일어나므로 **부분 만료·부분
  재호출이 정상 동작이다**(AC-20) — 갤러리만 만료되면 `detailImage2` 1콜만 재발한다.
- **캐시에 담기는 값은 TourAPI 공개 콘텐츠(개인정보 아님)** — POL-014 판정 근거(spec Applied policies). member 반응은 캐시에 없다.
- **데코레이터 배치**: `SpotDetailCacheAdapter`가 `TourInfoPort`를 감싸므로 도메인·GraphQL은 캐시 존재를 모른다.
  캐시 미도입 상태로도 동작(데코레이터 제거만) — 이때 콜 수만 늘고 동작은 같다.
- **에러는 캐시하지 않는다**: `UPSTREAM_UNAVAILABLE`/`RATE_LIMITED`는 저장 금지(다음 조회가 재시도돼야 함).
  위 null sentinel과 혼동하지 않도록 구분한다 — "값이 없음"은 캐시하고 "가져오기 실패"는 캐시하지 않는다.

## 5. likeCount 집계 (R5·R11)

```
interface SpotReactionPort {
  // ... 기존 upsert ...
  fun countLikes(contentId: String, language: DomainLocale): Long   // 신규
}
```

- `SELECT count(*) FROM spot_reaction WHERE content_id = ? AND language = ? AND reaction = 'LIKE'` (AC-11).
- **DISLIKE 제외, 언어별 분리**(AC-13): 키가 `(content_id, language)`라 언어 통합 합산은 하지 않는다 — SPEC-0002의
  "같은 장소 언어별 contentId 상이" 한계를 그대로 상속. 언어 통합은 장소 동일성 해소가 선행돼야 하는 별도 과제(spec §1 제외).
- 반응 없으면 0(AC-12). `(content_id, language, reaction)` 인덱스로 카운트 비용을 낮춘다(마이그레이션은 서버 하네스 소유).

## 6. 상세 반응 — 신규 없음 (R12)

상세 화면의 좋아요/싫어요는 기존 `reactToSpot`(SPEC-0002 §6)을 그대로 재사용한다. 식별자 공간·재평가 덮어쓰기·인증
(`UNAUTHENTICATED`)이 스와이프와 동일하다. 서버에 새 커맨드·뮤테이션 없음. 낙관적 갱신·재조회 선택은 클라이언트 몫이다.

## 7. 오류 매핑 (DGS)

- 쿼리 `spotDetail`: 쿼리 payload 컨벤션 없음(SPEC-0002 §7과 동일) → 최상위 `errors[]` + `ErrorType`:
  `SPOT_NOT_FOUND`→NOT_FOUND, `UPSTREAM_RATE_LIMITED`→(RESOURCE_EXHAUSTED 계열), `UPSTREAM_UNAVAILABLE`→UNAVAILABLE.
- **오류 XML 함정**: `detailCommon2`/`detailImage2`/`detailIntro2`/`detailInfo2` 모두 오류를 XML로 줄 수 있다. 기존
  §5 어댑터 로직(Content-Type/본문 스니핑 → `UpstreamException`)을 재사용해 원본 XML이 도메인·응답으로 새지 않게
  한다(AC-17).
- **하위 필드 부분 실패**(지연 조회로 생긴 신규 사안, spec `[TBD-3]`): 루트 `detailCommon2`는 성공했는데
  `gallery`·`intro`·`infoItems`의 콜만 실패하는 경우가 생긴다.

  **현행 초안 SDL은 이 경우 쿼리 전체를 실패시킨다.** `gallery: [SpotImage!]!`·`infoItems: [SpotInfoItem!]!`가
  non-null이므로 필드 오류의 null이 부모로 전파되는데, 부모 `SpotDetail`도 `spotDetail(id: ID!): SpotDetail!`로
  non-null이라 null 버블이 **쿼리 루트까지 올라간다**. 즉 갤러리 1콜만 실패해도 이미 성공한 본문까지 통째로
  버려진다 — 지연 조회로 실패를 필드 단위로 가두려던 의도와 정확히 반대다.

  따라서 `[TBD-3]`은 순수한 미결정이 아니라 **초안이 이미 원치 않는 답을 강제하고 있는 상태**다. 선택지는
  (a) `gallery`·`infoItems`를 nullable로 바꿔 본문을 살린다(계약 변경 — 이번 스펙 내 신규 필드라 breaking 아님),
  (b) 전체 실패를 의도된 동작으로 확정하고 `[TBD-3]`을 닫는다. 서버 하네스가 POL-016과 함께 결정한다.
  결정 전까지 이 문서는 표면을 확정하지 않으며, `intro`는 이미 nullable이라 이 문제에서 자유롭다.
- **한도 초과 구분**: TourAPI 한도 초과 응답을 `UPSTREAM_RATE_LIMITED`로 분류할지 `UPSTREAM_UNAVAILABLE`로 접을지는
  spec 열린 질문 `[TBD-2]`(서버 하네스). 초안은 분리를 전제.
- **쿼리 오류 표면 전역 결정은 서버 하네스 몫**(spec 미결정·열린 질문, POL-016).

## 8. 시크릿 위생 (R8·AC-21)

SPEC-0002 §4·§5와 동일: serviceKey는 환경변수(`IEURI_TOUR_API_KEY`, 4개 언어 공용 단일 키)로만, 응답·로그·GraphQL
어디에도 노출 금지. TourAPI 원본 필드명/코드도 로그에 남기지 않는다. `+` 이중 인코딩 방어 로직 재사용.

## 9. 미확정(서버 관점)

권위 있는 목록은 spec.md 미결정·열린 질문이다(재기술하지 않음). 서버 관점에서 착수 전 값이 필요한 것:
- 오류 코드 최종 문자열·`UPSTREAM_RATE_LIMITED` 통합 여부(spec `[TBD-2]`, POL-016) — 서버 하네스.
- 하위 필드 부분 실패 표면(spec `[TBD-3]`) — `gallery`·`infoItems`의 non-null 유지 여부와 직결(§7).
- 필드 선택이 upstream 콜 수를 결정하는 구조에서의 TourAPI 한도 보호(spec `[TBD-4]`) — 운영 실측 후 판단.
  전 필드 요청이 진입당 4콜이므로 이전 설계(2콜 고정)보다 최악값이 크다.
- `detailImage2` 파라미터(`imageYN`/`subImageYN`)로 원본·서브 이미지 중 무엇을 gallery에 담을지 — 실데이터로 확정.
  (spec R3은 "다중 이미지 최대 10장"만 규정. 원본 우선/서브 포함 여부는 표본 확인 후 어댑터 설정으로.)
- `SpotIntro`의 `petAllowed`·`creditCardAccepted`를 Boolean/enum으로 접을지 — 원문 값 집합 표본 확인 후(§3.1).
- 명시적 캐시 무효화(운영 이벤트) 필요 여부(spec 열린 질문) — 초안은 TTL 만료만.
