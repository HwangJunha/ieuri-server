> spec: SPEC-0002 · source: `Specs/SPEC-0002/spec.md` (Obsidian) · status: draft

# SPEC-0002 · 서버 설계 — 관광지 스와이프 추천

요구사항·수용 기준·컨트랙트는 스펙이 authority다. 이 문서는 **ieuri-server에 국한된 설계 결정**만 담는다.
반복하지 않고 링크한다: 컨트랙트 초안 `Specs/SPEC-0002/contract.graphql`, 수용 기준(AC-1~22)·오류 코드·정책 판정은 `spec.md`.

관련 이슈: #21. 브랜치: `feature/SPEC-0002-spot-swipe` (dev에서 컷).

---

## 1. 헥사고날 배치

tourism 도메인. DDD + 헥사고날, application 계층 CQRS. 도메인 모델과 JPA 엔티티 분리(전 도메인 규약).

```
adapter/in/graphql        SpotDataFetcher, ReactToSpotDataFetcher  (DGS)
adapter/out/tourapi       TourApiClient (구현체) — 포트 뒤 격리, TourAPI 함정 5종 전담
adapter/out/persistence   SpotReactionJpaEntity, SpotReactionRepository
application/query          FindSpotsQueryHandler   (읽기)
application/command        ReactToSpotCommandHandler (쓰기)
domain/tourism            Spot(도메인 모델), SpotReaction, SpotCategory, CopyrightType, Distance
port/out                  TourInfoPort (조회), SpotReactionPort (저장)
```

**핵심 격리 원칙**: 도메인 계층과 GraphQL 스키마는 TourAPI 필드명(`contentid`, `firstimage`, `lclsSystm2`,
`cpyrhtDivCd`, `mapx`…)·코드 체계(12/76/38/79…)를 **직접 알지 않는다**. `TourInfoPort`가 도메인 모델
(`Spot`)을 반환하고, 어댑터가 TourAPI 응답을 그 모델로 매핑한다. **향후 DB 적재 방식 전환 = `TourInfoPort`
구현체 교체만.** 도메인·application·GraphQL은 무변경.

## 2. TourInfoPort 계약 (도메인 지향)

```
interface TourInfoPort {
  // 언어는 요청 스코프 LocaleContext에서 주입(파라미터 아님 or 명시 파라미터 — 설계 선택)
  fun findNearby(query: NearbySpotQuery): NearbySpotResult   // 도메인 모델 반환
  fun availableCategories(locale: DomainLocale): List<SpotCategory>
}
```

- `NearbySpotQuery`: 위경도, 반경(m), 카테고리(도메인 enum, nullable), 페이지 커서/크기.
- 반환은 이미 **필터·정규화·거리계산·정렬을 어댑터가 끝낸 도메인 `Spot`** — 이 순서 중 "필터·정규화·거리·정렬"이
  어댑터 책임인지 application 책임인지는 아래 3에서 분배한다.

## 3. 조회 파이프라인 책임 분배

| 단계 | 위치 | 근거 |
|---|---|---|
| TourAPI 호출·역직렬화·함정 5종 처리 | **adapter/out/tourapi** | TourAPI 세부는 어댑터에 가둔다(누출 금지). |
| 비관광 필터(`lclsSystm2` 코드) | **adapter → 도메인 규칙** | 제외 코드 목록은 설정. 코드값이 TourAPI 체계라 어댑터가 도메인 `Spot`로 매핑하며 비관광 항목을 드랍한다. 제외 코드 목록만 설정 주입. |
| 이미지 http→https 정규화 | **adapter** | TourAPI 데이터 정제. |
| Haversine 거리 계산 | **domain** | 사용자 좌표는 요청 파라미터. 거리 = 도메인 값객체 `Distance.between(a, b)` 순수 함수(TourAPI 무관). |
| 정렬((거리 ASC, id)) | **application** | 정렬은 계약(POL-019). 커서 안정성 책임. AC-1. |
| 커서 페이지네이션 | **application/adapter/in** | Relay connection 조립. |

> 필터로 100건 미만이 되어도 **보충하지 않고 반환**(spec §4.4-3, AC-4). 클라이언트 선제 조회가 스택을 채운다.

## 4. 언어 라우팅 · 설정 구조

- 요청 스코프 `LocaleContext`를 서블릿 필터/DGS 인터셉터에서 `Accept-Language` 파싱해 채운다. 폴백 EN(AC-11).
- **언어별 엔드포인트·키 매핑을 설정으로**:

```yaml
tourapi:
  service-key: ${IEURI_TOUR_API_KEY}   # 4개 언어 공용 단일 키 (실측 확인, spec B-4)
  base-urls:
    KO: "https://apis.data.go.kr/B551011/KorService2"
    EN: "https://apis.data.go.kr/B551011/EngService2"
    JA: "https://apis.data.go.kr/B551011/JpnService2"
    ZH: "https://apis.data.go.kr/B551011/ChtService2"
  category-codes:            # 도메인 enum → 언어별 TourAPI contentTypeId
    KO:      { TOURIST_SPOT: 12, CULTURAL_FACILITY: 14, FESTIVAL_EVENT: 15, TRAVEL_COURSE: 25, LEISURE_SPORTS: 28, LODGING: 32, SHOPPING: 38, RESTAURANT: 39 }
    FOREIGN: { TOURIST_SPOT: 76, CULTURAL_FACILITY: 78, FESTIVAL_EVENT: 85, LEISURE_SPORTS: 75, LODGING: 80, SHOPPING: 79, RESTAURANT: 82, TRANSPORT: 77 }
  exclude-lclssystm2: [ ]    # [TBD] 비관광 제외 코드 — 실데이터로 확정
  arrange: Q                 # 이미지 보유 항목만 + 수정일순(거리는 서버 계산이라 S 불필요)
  max-page-size: 100         # POL-018 잠정 캡: first>100 클램프. 공통값 확정 시 대체
```

- `availableCategories`는 이 설정에서 해당 언어(KO vs FOREIGN)의 키 집합을 그대로 반환한다(AC-8). KO는 `TRANSPORT` 없음, FOREIGN은 `TRAVEL_COURSE` 없음.
- **serviceKey는 4개 언어 공용 단일 키로 확정**(spec B-4 실측). 언어는 base-url 선택에만 영향. 환경변수 `IEURI_TOUR_API_KEY`(direnv → `~/.config/ieuri/secrets.env`).
- **`TOURAPI_KEY_*`는 환경변수로만.** 응답·로그·GraphQL 어디에도 노출 금지(절대 조건). serviceKey `+` 이중
  인코딩 함정: 키를 사전 인코딩 상태로 보관하고 HTTP 클라이언트가 재인코딩하지 않도록 raw 쿼리스트링 제어. (실측한 현재 키에는 `+`가 없어 이 함정이 발현되지 않았으나, 키가 교체될 수 있으므로 방어 로직은 유지 — 인코딩 키 수신 시 재인코딩 금지, 미인코딩 키는 1회 인코딩.)

## 5. TourAPI 함정 5종 — 어댑터 전담 (누출 금지, AC-7)

`adapter/out/tourapi` 구현체 안에서만:
1. serviceKey `+` 이중 인코딩 → 인코딩 직접 제어.
2. JSON 요청에도 오류는 XML → Content-Type/본문 스니핑, XML 오류를 `UpstreamException`으로 변환.
3. 0건 시 `items`가 빈 문자열 → 빈 리스트로 정규화.
4. 1건 시 `item`이 객체 → 단일 원소 리스트로 정규화.
5. 미정의 필드 → 관대한 역직렬화(unknown 무시, Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false` 국소 적용).

이 예외들은 도메인·application으로 새지 않는다. 어댑터가 도메인 오류(`UpstreamUnavailable` 등)로 번역해 던진다.

## 6. 저장 모델 (PostgreSQL)

DB에 쓰는 유일한 데이터. 관광 데이터는 적재하지 않는다.

```
spot_reaction
  member_id     FK member
  content_id    varchar   -- TourAPI contentId (언어별로 다름)
  language      varchar   -- 판단이 기록된 언어(EN/KO/JA/ZH)
  reaction      varchar   -- LIKE | DISLIKE
  created_at, updated_at
  PRIMARY KEY (member_id, content_id, language)   -- upsert 단위 (AC-12, AC-13)
```

- **재평가 = upsert**(동일 PK, reaction 갱신). 이력 없음(AC-13).
- clear(취소) 없음 — 되돌리기 범위 밖(spec §7).
- 언어를 PK에 포함해 "같은 장소 언어별 contentId 상이"를 정면으로 수용. 언어 간 취향 이어붙이기는 알려진 한계(spec §7).
- `reactToSpot`은 로그인 회원 전제(기존 accessToken 인증 재사용). 비로그인 → `UNAUTHENTICATED`(AC-14).

## 7. 오류 매핑 (DGS)

- 뮤테이션: `BaseResponse.userErrors`로 `UNAUTHENTICATED`/`SPOT_NOT_FOUND`.
- 쿼리: 이 스키마엔 쿼리 payload 컨벤션이 없다 → 최상위 `errors[]` + `ErrorType`(라이브 스키마 존재):
  `INVALID_COORDINATES`/`RADIUS_TOO_LARGE`→BAD_REQUEST, `CATEGORY_NOT_AVAILABLE_FOR_LOCALE`→FAILED_PRECONDITION,
  `UPSTREAM_UNAVAILABLE`→UNAVAILABLE. DGS `DataFetcherExceptionHandler`에서 도메인 예외→ErrorType 변환.
- **쿼리 오류 표면 전역 결정은 서버 하네스 몫**(spec §6·§9, POL-016). 초안은 `errors[]` 전제.

## 8. 캐싱 (Valkey) — 선택

관광 데이터는 DB 적재 안 함이나, **동일 (반경·좌표버킷·카테고리·언어) 조회의 단기 캐시**를 Valkey에 둘 수 있다
(TourAPI 호출·일일 한도 절약). 이는 "적재"가 아닌 **일시 캐시**로 구분 — TTL 짧게(예: 수 분), 좌표를 그리드
버킷팅해 키 폭발 방지. **이번 스펙 필수 아님**; 호출량 관측 후 도입 판단. 도입 시에도 `TourInfoPort` 데코레이터로
어댑터 뒤에 둔다(도메인 무변경).

## 9. 미확정(서버 관점)

미결정 항목의 권위 있는 목록은 spec.md §6·§9다(여기서 재기술하지 않음). 서버 관점에서 구현 착수 전 반드시 값이 필요한 것:
- `exclude-lclssystm2` 초기 코드 목록 — 실데이터 표본으로 확정. (실측상 외국어 관광지(76)에도 성형외과 등 비관광 항목이 섞여 필터 필요성 재확인됨.)
- `max-page-size` 확정값(POL-018 공통값) — 잠정 100.

해소된 것(실측): 반경 상한 없음(`RADIUS_TOO_LARGE`=서버 UX 상한 100,000m 초과) · serviceKey 단일 키 4개 언어 공용 · arrange=Q 이미지 필터.
