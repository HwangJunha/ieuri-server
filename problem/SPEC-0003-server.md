> spec: SPEC-0003 · source: `Specs/SPEC-0003/spec.md` (Obsidian) · status: draft

# SPEC-0003 · 서버 설계 — 관광지 상세보기

요구사항·수용 기준·컨트랙트는 스펙이 authority다. 이 문서는 **ieuri-server에 국한된 설계 결정**만 담는다.
반복하지 않고 링크한다: 컨트랙트 초안 `Specs/SPEC-0003/contract.graphql`, 수용 기준(AC-1~32)·오류 코드·정책 판정은 `spec.md`.

관련 이슈: #23. 브랜치: `feature/SPEC-0003-spot-detail` (dev에서 컷).
선행: SPEC-0002가 세운 tourism 도메인·`TourInfoPort` 격리·`spot_reaction` 저장·언어 라우팅을 그대로 딛는다. 여기서는
그 위에 얹히는 상세 조회만 설계한다.

---

## 1. 헥사고날 배치 (SPEC-0002에 추가)

```
adapter/in/graphql        SpotDetailDataFetcher  (DGS, 신규)
adapter/out/tourapi       TourApiClient — detailCommon2·detailImage2 메서드 추가(함정 5종 기존 전담 재사용)
adapter/out/cache         SpotDetailCacheAdapter (Valkey, 신규) — TourInfoPort 데코레이터
application/query          GetSpotDetailQueryHandler (읽기, 신규)
domain/tourism            SpotDetail(도메인 모델, 신규), SpotImage(값객체, 신규) — 기존 Spot/SpotReaction 재사용
port/out                  TourInfoPort.findDetail(...) 추가 · SpotReactionPort.countLikes(...) 추가
```

**격리 원칙은 SPEC-0002와 동일**(§1 참조): 도메인·GraphQL은 TourAPI 필드명(`overview`, `homepage`, `tel`,
`originimgurl`, `smallimageurl`, `imgname`, `cpyrhtDivCd`)을 직접 알지 않는다. 어댑터가 도메인 `SpotDetail`로 매핑한다.

## 2. TourInfoPort 확장 (도메인 지향)

```
interface TourInfoPort {
  // ... 기존 findNearby, availableCategories ...
  fun findDetail(query: SpotDetailQuery): SpotDetail    // 신규. 도메인 모델 반환. 없으면 도메인 NotFound.
}
```

- `SpotDetailQuery`: contentId, locale(요청 스코프 `LocaleContext`에서). 언어별 base-url 선택은 SPEC-0002 설정 재사용.
- 반환 `SpotDetail`은 이미 **2콜 병합·이미지 https 정규화·10장 컷·빈문자열 null화·저작권 문구 조립을 어댑터가 끝낸**
  도메인 모델이다. `likeCount`는 여기 포함하지 않는다 — 아래 4에서 별도 조립(캐시 경계 분리 때문).

## 3. 상세 조회 파이프라인 (호출 → 병합 → 정규화 → 캐시)

| 단계 | 위치 | 근거 |
|---|---|---|
| `detailCommon2` 1콜 (overview·homepage·tel·addr·cpyrhtDivCd) | **adapter/out/tourapi** | TourAPI 세부는 어댑터에 가둠. |
| `detailImage2` 1콜 (다중 이미지) | **adapter/out/tourapi** | 상동. 두 콜은 병렬 발사 가능(동일 contentId, 상호 무관). |
| 이미지 http→https, 10장 컷, TourAPI 순서 유지 | **adapter** | 데이터 정제. gallery는 반환 순서 앞 10장(AC-7·AC-8). 정렬 키 없음. |
| overview 등 빈 문자열 → null 정규화 | **adapter** | AC-3. TourAPI `items` 빈 문자열/단건 객체화 함정은 기존 §5 로직 재사용(AC-10). |
| 이미지별 cpyrhtDivCd → CopyrightType + attributionText | **adapter → domain** | 이미지별 저작권(SpotImage). Spot 상속 아님(contract 근거). |
| 미존재 contentId → 도메인 NotFound → `SPOT_NOT_FOUND` | **adapter/in** | AC-15·AC-6. 언어 교차 id도 upstream이 못 찾으면 여기로. |

> **2콜 확정(R7)**: 진입 1회는 정확히 `detailCommon2`+`detailImage2` 2콜. 캐시 미스에서만 발생. 병렬 발사로 지연을
> 줄이되 콜 수는 2로 고정한다. 셋 이상(예: `detailIntro2`)은 이번 범위 밖(spec §1 제외).

## 4. 캐싱 (Valkey) — likeCount 경계 분리 (R7·R11)

**이번 스펙에서 캐시는 필수다**(SPEC-0002의 "선택" 캐시와 다름). 이슈 #16이 이관하는 Valkey 인프라와 동일 인스턴스를 쓴다
(신규 인프라 아님).

```
GetSpotDetailQueryHandler.handle(contentId, locale):
  derived = spotDetailCache.get(key=(contentId, locale))          # SpotDetailCacheAdapter
            ?: run {
                 d = tourInfoPort.findDetail(...)                   # 캐시 미스 → TourAPI 2콜
                 spotDetailCache.put((contentId, locale), d, TTL=24h)
                 d
               }
  likeCount = spotReactionPort.countLikes(contentId, locale)       # 항상 실시간, 캐시 무관 (R11·AC-14)
  return derived.withLikeCount(likeCount)
```

- **캐시 대상 = TourAPI 파생 필드만**(overview·gallery·contacts·copyright). **likeCount는 절대 캐시하지 않는다**
  (AC-14: 캐시된 상태에서 LIKE +1 후 재조회 시 현재값 반환). 이 경계가 캐시 키 설계의 핵심이다.
- **캐시 키 = `(contentId, language)`**: contentId가 언어별로 갈리므로 언어를 키에 넣어야 KO/EN 상세가 안 섞인다(AC-19).
- **TTL 24h**: 준정적 콘텐츠라 최대 24h 지연 감수. TTL 만료 시 다음 조회가 미스로 2콜 재발(AC-20).
- **캐시에 담기는 값은 TourAPI 공개 콘텐츠(개인정보 아님)** — POL-014 판정 근거(spec Applied policies). member 반응은 캐시에 없다.
- **데코레이터 배치**: `SpotDetailCacheAdapter`는 `TourInfoPort`(또는 그 앞 application 계층)를 감싸므로 도메인·GraphQL은
  캐시 존재를 모른다. 캐시 미도입 상태로도 동작(데코레이터 제거만).
- **에러는 캐시하지 않는다**: `UPSTREAM_UNAVAILABLE`/`RATE_LIMITED`는 저장 금지(다음 조회가 재시도돼야 함).

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
- **오류 XML 함정**: `detailCommon2`/`detailImage2`도 오류를 XML로 줄 수 있다. 기존 §5 어댑터 로직(Content-Type/본문
  스니핑 → `UpstreamException`)을 재사용해 원본 XML이 도메인·응답으로 새지 않게 한다(AC-17).
- **한도 초과 구분**: TourAPI 한도 초과 응답을 `UPSTREAM_RATE_LIMITED`로 분류할지 `UPSTREAM_UNAVAILABLE`로 접을지는
  spec 열린 질문 `[TBD-2]`(서버 하네스). 초안은 분리를 전제.
- **쿼리 오류 표면 전역 결정은 서버 하네스 몫**(spec 미결정·열린 질문, POL-016).

## 8. 시크릿 위생 (R8·AC-21)

SPEC-0002 §4·§5와 동일: serviceKey는 환경변수(`IEURI_TOUR_API_KEY`, 4개 언어 공용 단일 키)로만, 응답·로그·GraphQL
어디에도 노출 금지. TourAPI 원본 필드명/코드도 로그에 남기지 않는다. `+` 이중 인코딩 방어 로직 재사용.

## 9. 미확정(서버 관점)

권위 있는 목록은 spec.md 미결정·열린 질문이다(재기술하지 않음). 서버 관점에서 착수 전 값이 필요한 것:
- 오류 코드 최종 문자열·`UPSTREAM_RATE_LIMITED` 통합 여부(spec `[TBD-2]`, POL-016) — 서버 하네스.
- `detailImage2` 파라미터(`imageYN`/`subImageYN`)로 원본·서브 이미지 중 무엇을 gallery에 담을지 — 실데이터로 확정.
  (spec R3은 "다중 이미지 최대 10장"만 규정. 원본 우선/서브 포함 여부는 표본 확인 후 어댑터 설정으로.)
- 명시적 캐시 무효화(운영 이벤트) 필요 여부(spec 열린 질문) — 초안은 TTL 만료만.
