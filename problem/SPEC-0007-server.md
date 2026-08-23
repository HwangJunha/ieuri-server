> spec: SPEC-0007 · source: `specs/SPEC-0007/spec.md` (Obsidian) · status: draft (v1.1, 개정 반영됨)

# SPEC-0007 · 축제 정보 적재 — 서버 설계

요구사항과 인수 기준은 스펙 문서에 있다. 이 문서는 그것을 되풀이하지 않고 **이 레포에서 어떻게 실현하는지**만
다룬다. 요구사항은 번호로 인용한다.

---

## 1. 도메인 배치

`tourism` 도메인에 속한다. 새 도메인을 만들지 않는다 — 축제는 관광지의 한 종류이고, 이미
`SpotCategory.FESTIVAL_EVENT`로 표현되고 있다.

패키지는 기존 구조를 그대로 따른다.

```
tourism/
  domain/vo/                     FestivalPeriod (신규)
  application/command/           SyncFestivalCommand (신규)
  application/port/out/client/   FestivalSyncPort (신규)
  application/port/out/persistence/  FestivalStorePort (신규)
  infrastructure/adapter/out/tourapi/  TourApiFestivalSyncClient (신규)
  infrastructure/batch/          FestivalSyncTasklet (신규)
```

`SpotSyncPort`/`TourApiSyncClient`와 **같은 자리에 대칭으로** 놓는다. 기존 포트에 메서드를 추가하지 않는다 —
수집 오퍼레이션이 다르고(`searchFestival2` vs `areaBasedSyncList2`) 페이지네이션 성격도 다르므로,
`SpotSyncPort`가 `TourInfoPort`와 분리된 것과 같은 이유가 그대로 적용된다.

---

## 2. 마이그레이션

### 2.1 `V18__create_spot_festival.sql`

최신 적용 마이그레이션은 `V17`이므로 다음 번호는 `V18`이다.

컬럼은 축제 전용 4개와 키·감사 컬럼만 둔다. 제목·주소·좌표 등 공통 필드는 `spot`에 이미 있으므로
**중복 적재하지 않는다**(R-1, AC-3).

| 컬럼 | 타입 | 근거 |
|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY` | `spot`·`spot_detail`과 같은 surrogate PK 관례 |
| `content_id` | `VARCHAR(32) NOT NULL` | TourAPI contentId. 언어별로 다르다 |
| `language` | `VARCHAR(2) NOT NULL` | EN/KO/JA/ZH |
| `event_start_date` | `DATE NOT NULL` | `YYYYMMDD` 원본을 파싱해 저장. 해석 실패 항목은 적재 안 함(R-4) |
| `event_end_date` | `DATE NOT NULL` | 동일. 정리 판정의 기준이다(R-8) |
| `progress_type` | `VARCHAR(50)` | 원문 보존. nullable(R-5) |
| `festival_type` | `VARCHAR(50)` | 동일 |
| `last_synced_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | `spot`과 같은 관례 |
| `created_at` / `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT now()` | 동일 |

**날짜를 `DATE`로 저장하고 문자열로 두지 않는다.** R-8의 종료 판정이 문자열 비교가 되면 형식이 어긋난 값에서
조용히 틀린 결과가 나온다. 파싱 실패를 적재 시점에 드러내는 편이 옳다(R-4).

제약:

- `CONSTRAINT uq_spot_festival UNIQUE (content_id, language)` — upsert 단위(R-7)
- `CONSTRAINT fk_spot_festival_spot FOREIGN KEY (content_id, language) REFERENCES spot (content_id, language) ON DELETE CASCADE` — R-2
- `CONSTRAINT ck_spot_festival_language CHECK (language IN ('EN','KO','JA','ZH'))`
- `CONSTRAINT ck_spot_festival_period CHECK (event_end_date >= event_start_date)` — AC-15를 DB에서도 지킨다

인덱스:

- `CREATE INDEX idx_spot_festival_end_date ON spot_festival (language, event_end_date)` — R-8의 종료 축제
  조회가 매일 언어별로 수행된다.

> **⚠️ 2.1은 V18 시점의 설계이며 V19·V20이 이를 뒤집었다.** 아래 §2.3을 읽지 않고 2.1만 보면 틀린 스키마를
> 구현하게 된다. 이 절을 지우지 않고 남기는 것은 뒤집힌 판단이 무엇이었는지가 이력이기 때문이다.

원래의 판단 근거는 이랬다 — "**FK를 `spot.id`가 아니라 `(content_id, language)`에 건다.** `spot`의
자연키는 `(content_id, language)`이고 V17이 이미 `spot_detail`·`spot_reaction` 등 5개 자식 테이블을 이 복합키로
연결했다. 같은 축제라도 언어별 `contentId`가 달라 surrogate `id`를 쓰더라도 결국 언어당 한 행이 되므로
얻는 것이 없고, 관례만 깨진다."

**이 근거는 V19에서 기각됐다.** "관례를 지킨다"는 이유가 자연키 FK의 비용보다 작았다 — §2.3 참조.

### 2.2 적용 전 확인

V17이 그랬듯 **고아 행이 있으면 FK 생성이 실패한다.** `spot_festival`은 신규 테이블이라 비어 있으므로
이 문제는 없다.

### 2.3 V19·V20 — 자연키 FK 폐기와 외부 식별자 전환 (v1.1 개정)

*(이슈 #35 · PR #37, `dev`에 머지 완료. `spot_festival`을 포함한 자식 6개가 대상이다.)*

**V19 — 자식 FK를 surrogate PK로.** V17과 §2.1의 자연키 FK 판단을 뒤집는다. 자연키 FK는 두 컬럼을 모든
자식의 참조 경로에 고착시켜, 부모의 키 구성이 바뀌면 자식 6개가 연쇄로 바뀐다. surrogate 참조는 그 연결을 끊는다.

대상: `spot_detail` · `spot_detail_image` · `spot_detail_intro` · `spot_detail_info` · `spot_reaction` ·
**`spot_festival`**. CASCADE용 인덱스도 함께 추가됐다 — 자식 쪽 인덱스가 없으면 부모 1행 삭제마다 자식
전체 스캔이 발생하며, 관광지 정리는 한 번에 수천 행을 지운다(R-8이 바로 그 경로다).

**V20 — 자식에서 자연키 제거 + 외부 식별자 전환.** V19가 "조회가 전부 contentId로 들어오므로 두 컬럼을
남긴다"고 한 판단마저 뒤집는다. 그 전제가 바뀌었다 — 이제 클라이언트가 `spot.id`를 들고 오므로 해석 단계가
없다.

- 유니크 제약 6개를 `spot_id` 기반으로 재정의. `spot_festival`의 upsert 단위도 `(content_id, language)`가
  아니라 `spot_id`가 된다 — 판별력은 동일하다(`spot_id`가 `(content_id, language)`에 1:1).
- `content_id`·`language` 컬럼과 `ck_*_language` CHECK 제거. **§2.1 표의 `content_id`·`language` 행과
  `uq_spot_festival`·`ck_spot_festival_language`는 최종 스키마에 존재하지 않는다.**
- `idx_spot_festival_end_date`의 `language` 선행 컬럼도 함께 사라진다 — 언어는 이제 `spot`에만 있으므로
  R-8의 언어별 종료 조회는 `spot` 조인으로 언어를 얻는다.
- **`spot` 테이블은 두 컬럼을 유지한다** — 배치 적재의 upsert 키이자 TourAPI에 넘길 값이다.
  `contentId`는 upstream 경계 전용 내부 값이 된다.

`content_type_id`를 유니크 키에 포함하는 건은 **하지 않았다.** 실측상 `(content_id, language)`가 같으면서
그 값이 다른 행이 0건이라 자연키에 함수 종속이고, 키에 넣어도 유니크성이 달라지지 않으면서 재분류 시
중복 행만 생긴다. 이슈 #35의 `[AMEND]` 2번 항목이 여기서 종결된다.

**적재 경로에 미치는 영향.** `FestivalJdbcAdapter`의 삽입이 `VALUES`에서 `SELECT ... FROM spot`으로 바뀐다 —
부모 id를 가져오면서, 부모가 없으면 0행 삽입으로 조용히 건너뛴다. **이는 R-2·AC-5가 이미 요구하는 동작과
같다**(부모 없는 축제 레코드는 적재하지 않는다). 즉 §4.2의 Step 순서 전제는 그대로 유효하다.

**전량 삭제 후 삽입 패턴의 함정(리뷰에서 발견).** 삽입만 `SELECT ... FROM spot`으로 바꾸면 부모가 없을 때
삽입은 0행인데 삭제는 무조건 실행되어 **데이터 유실 경로**가 생긴다. V19 이전에는 FK 위반이 트랜잭션을
롤백시켜 이 상태가 불가능했다. `DELETE`에도 `EXISTS` 조건을 걸어 삭제와 삽입이 함께 건너뛰게 해야 한다.

### 2.4 R-13 · R-14 — 외부 식별자와 언어 교차 거부 (v1.1 신설)

**R-13.** GraphQL이 노출하는 식별자가 `spot.id`가 된다. `SpotQueryResolver`·`ReactToSpotResolver`에서
`contentId → spot.id` 해석 단계가 사라지고, `ID` 스칼라를 `kotlin.Long`으로 전역 매핑해
(`typeMapping = mapOf("ID" to "kotlin.Long")`) 생성 타입까지 Long이 된다. 형식이 틀린 id는 리졸버에
도달하기 전에 GraphQL이 거부한다 — 이것이 AC-49가 "별도 오류 코드를 만들지 않는다"고 한 이유다.

> **선결 조건 — auth 도메인.** `verificationId` 5곳이 `ID`로 선언돼 있으나 실제 값은 UUID다
> (`EmailVerification.publicId`). `ID`를 Long에 매핑하면 깨지므로 스키마에서 `String`으로 먼저 정정해야
> 한다. **SPEC-0007 범위 밖이지만 분리가 불가능한 선결 조건이다.**

**R-14 — 언어 교차 거부는 조회 진입점마다 걸어야 한다.** 이것이 리뷰에서 실제 버그로 드러난 지점이므로
설계에 명시한다.

검증을 캐시·DB를 모두 놓쳤을 때만 호출되는 경로(`query()` 안쪽)에 두면 **캐시 적중·저장소 적중이라는 가장
흔한 경로에서 그대로 통과한다.** V20 이후 `spot_detail`과 캐시 키 모두 언어를 들지 않으므로 그 단계에서는
스스로 판별할 수 없다. 게다가 지연 필드 4개는 각각 독립 진입점이라 상위 조회의 검증을 물려받지 않는다.

→ 언어 대조를 **네 진입점 각각의 첫 줄**로 올린다(AC-52·AC-53·AC-54). 폴백 경로만 확인하는 테스트는
검증이 실제로 도는지를 증명하지 못한다 — 스텁이 우연히 캐시·DB 미스를 강제하면 통과해 버린다.

**캐시 키.** 프리픽스에 `v2`를 붙이고 언어 세그먼트를 뺀다(`spotId`가 이미 언어별이므로). 같은 키 모양에
다른 의미가 담기므로 네임스페이스를 분리해 옛 항목이 잘못 읽히지 않게 한다 — 남은 `v1`은 TTL(24h)로 소멸한다.

**`SpotDetail.id`는 도메인에서 제거한다.** 어댑터 셋(TourAPI·DB·캐시) 중 누구도 채우지 않아 죽은 코드였다.
식별자는 읽기 DTO(`SpotDetailView`)가 소유하는 편이 CQRS상 맞다 — 요청이 이미 아는 값을 도메인이 다시 들
이유가 없다.

---

## 3. 수집 경로

### 3.1 `FestivalSyncPort`

```
fun fetchPage(language: DomainLocale, eventStartDate: LocalDate, pageNo: Int, numOfRows: Int): FestivalSyncPage
```

`SpotSyncPort.fetchPage`와 달리 **`eventStartDate`를 인자로 받는다.** `searchFestival2`의 필수 파라미터이며
(AC-8), 실행일을 잡 파라미터에서 받아 내려보내야 테스트에서 날짜를 고정할 수 있다. 어댑터 안에서
`LocalDate.now()`를 부르면 AC-8·AC-26~28을 단위 테스트로 검증할 수 없다.

`FestivalSyncPage`는 `SpotSyncPage`와 같은 모양으로 `records`·`totalCount`·`droppedCount`를 갖는다.
`categoryFallbackCount`는 없다 — `searchFestival2`는 축제만 돌려주므로 카테고리 해석이 필요 없다.

### 3.2 `TourApiFestivalSyncClient`

`TourApiSyncClient`와 같은 규율이다. TourAPI 필드명과 코드 체계를 이 클래스 안에 가두고 밖으로는 도메인
언어로만 내보낸다.

호출 파라미터:

- `eventStartDate` = 실행일(`yyyyMMdd`) — **필수**
- `arrange` = 기존 `properties.arrange`와 동일하게 둔다. 대표이미지 필수 조건이 `spot` 적재 기준과
  일치해야 자식 행만 뜨는 상황을 피할 수 있다
- `numOfRows`·`pageNo`·`MobileOS`·`MobileApp`·`_type=json`·`serviceKey` — 기존과 동일
- **`areaCode`·`sigunguCode`·`cat1~3`을 요청 필터로 쓰지 않는다.** 에러 없이 0건을 반환한다(스펙 §기록된 함정)

`baseUrls`는 기존 언어별 설정을 그대로 쓴다(`KorService2`/`EngService2`/`JpnService2`/`ChtService2`).

매핑 규칙 — 전부 `mapPage`에서 처리하며 HTTP와 분리해 단위 테스트 가능하게 둔다(`TourApiSyncClient.mapPage`와
같은 관례):

- `contentid` 공백 → 드랍 (AC-4)
- `eventstartdate`/`eventenddate`를 `yyyyMMdd`로 파싱, 실패·공백 → 드랍 (AC-13, AC-14)
- 종료일 < 시작일 → 드랍 (AC-15)
- 종료일 < 실행일 → 드랍 (AC-9). 업스트림이 유효한 것만 준다는 보장에만 기대지 않는다
- `progresstype`/`festivaltype` → `takeIf { it.isNotBlank() }`로 공백을 null로만 바꾸고 값은 보존 (AC-17~19)

### 3.3 `FestivalPeriod` 값 객체

`domain/vo/FestivalPeriod.kt`. `start`·`end: LocalDate`를 갖고 `init`에서 `end >= start`를 강제한다.
`isEndedAsOf(date: LocalDate): Boolean = end < date` 를 제공해 **종료 판정 로직이 도메인에 있게 한다**(R-8).

이 판정을 배치나 SQL에만 두면 AC-26~28을 도메인 단위 테스트로 검증할 수 없다. 경계 조건(종료일 == 실행일은
아직 유효)이 한 곳에만 있어야 어긋나지 않는다.

---

## 4. 배치 편입

### 4.1 별도 잡을 만들지 않는다

스펙 §선행 조사의 결론이다. 근거를 코드 레벨로 다시 적는다.

`TourApiSyncClient`는 `areaBasedSyncList2`를 **`contentTypeId` 없이** 호출하고 `resolveCategory`로 카테고리를
사후 해석한다. 즉 축제는 이미 이 경로로 `spot`에 적재된다. 별도 잡이 종료 축제를 지우면 다음 관광지 수집이
같은 행사를 다시 넣는다. 또한 `SpotStorePort.deleteMissing(language, collectedIds)`는 전달된 집합의 여집합을
전부 지우므로, 두 잡이 각자 정리하면 서로의 행을 삭제한다.

→ `SpotSyncJobConfig`의 기존 잡에 **Step을 추가**한다. CronJob 매니페스트는 그대로 둔다.

### 4.2 Step 순서

```
[언어별 spotSyncStep] → [언어별 festivalSyncStep] → [언어별 festivalCleanupStep]
```

축제 수집이 관광지 수집 **뒤에** 온다. `spot_festival`은 `spot`에 FK를 걸므로 부모 행이 먼저 있어야 한다
(R-2, AC-5). 부모가 없는 축제 레코드는 적재를 건너뛴다 — FK 위반으로 Step을 실패시키지 않는다.

정리는 수집이 모두 끝난 뒤 별도 Step으로 둔다. 수집 중간에 지우면 페이지 경계에서 방금 넣은 행을 지울 수 있다.

`SpotSyncTasklet`과 마찬가지로 **언어 간 병렬 실행은 하지 않는다** — serviceKey가 4개 언어 공용이다.

### 4.3 R-9: 재삽입 방지

관광지 수집 단계가 **종료된 축제를 적재 대상에서 제외**해야 한다(AC-31~34). 두 가지 방법이 있다.

- (a) `SpotSyncTasklet`이 매 페이지마다 종료 축제 여부를 조회 — 페이지당 추가 질의가 생긴다
- (b) 잡 시작 시 종료된 축제의 `contentId` 집합을 한 번 읽어 Step에 주입

**(b)를 택한다.** 축제는 절대 건수가 적어 집합이 작고, 하루 1회 배치에서 잡 시작 시점의 스냅샷으로 충분하다.

#### 주입 지점 (구현이 추측하지 않도록 명시한다)

`SpotSyncTasklet`은 현재 외부에서 제외 집합을 받을 자리가 없다. `collectedIds`는 오직
`syncPort.fetchPage`가 돌려준 `page.records`로만 채워진다. 따라서 다음을 변경한다.

1. **생성자에 `endedFestivalIds: Set<String>`를 추가한다.** `SpotSyncJobConfig`가 Step을 만들 때
   해당 언어의 종료 축제 `contentId` 집합을 조회해 넘긴다. 언어별 Step이므로 집합도 언어별이다.
   포트를 통해 조회하며(`FestivalStorePort`에 조회 메서드를 둔다) 타스클릿이 JPA를 직접 보지 않는다.

   **`SpotSyncJobConfig`도 함께 바뀐다** — `FestivalStorePort`를 새로 주입받아야 하며, 이것이 기존
   관광지 잡 설정에 대한 유일한 변경이다. §1의 패키지 목록에는 신규 파일만 적었으나 이 클래스는 수정 대상이다.
2. **`execute` 안에서 적재 직전에 거른다.** 어댑터(`TourApiSyncClient`)가 아니라 타스클릿에서 거른다 —
   어댑터에서 거르면 제외된 항목이 `page.records`에 아예 없어 `collectedIds`에도 들어가지 못하고,
   그것이 아래 AC-34 문제를 그대로 일으킨다.

```
val (excluded, toStore) = page.records.partition { it.contentId in endedFestivalIds }
val written = storePort.upsertAll(toStore, syncedAt)
page.records.forEach { collectedIds += it.contentId }   // 제외분도 포함 — 순서 주의
```

**`collectedIds`에는 `page.records` 전체를 넣는다**(제외분 포함). 이유는 아래 AC-34다.

**AC-34 — 제외분을 `collectedIds`에 넣어야 하는 이유.** `cleanup()`은 `collectedIds`를 두 곳에 쓴다:
`deleteMissing`의 인자(=생존 집합)와 급감 게이트의 분자(`collected`)다. 제외분이 빠지면 두 가지가 깨진다.

- 급감 게이트의 `collected`가 실제 수집량보다 작아져, 관광지 정리가 부당하게 스킵될 수 있다.
- `deleteMissing`이 제외분을 "사라진 항목"으로 보고 삭제 대상에 넣는다. R-8이 이미 지우므로 최종 결과는
  같지만, 삭제 건수 집계가 이중으로 잡혀 관측값이 틀어진다.

**AC-33 — 축제가 아닌 항목이 영향받지 않는 근거.** 제외는 `contentId` **집합 멤버십**으로만 판정한다.
카테고리나 날짜를 타스클릿에서 다시 해석하지 않는다. `endedFestivalIds`는 `spot_festival`에서
`event_end_date < 실행일`로 뽑은 것이므로 축제가 아닌 항목의 `contentId`는 애초에 들어올 수 없다.

### 4.4 R-10: 정리 게이트

`FestivalSyncTasklet`의 정리는 `SpotSyncTasklet.cleanup()`과 게이트가 다르다.

| 게이트 | 관광지 | 축제 |
|---|---|---|
| 재개 실행 스킵 | 적용 | 적용 (AC-37) |
| 수집 0건 스킵 | 적용 | 적용 (AC-35) |
| 급감 비율(`cleanupMinRatio`) | 적용 | **적용하지 않음** (AC-36) |

축제는 계절에 따라 건수가 절반 이하로 흔들리는 것이 정상이므로 비율 게이트를 걸면 정리가 영구히 막힌다.

**`FestivalSyncProperties`를 별도 클래스로 둔다**(`ieuri.tourapi.festival-sync` prefix). 기존
`SpotSyncProperties`에 키를 추가하지 않는다 — 두 잡의 수명과 튜닝 축이 다르고, 한 클래스에 두면
`cleanupMinRatio`가 축제에도 적용되는 것처럼 읽힌다. 축제용 프로퍼티에는 **`cleanupMinRatio` 자체를
두지 않는다.** 존재하지 않는 설정은 실수로 켤 수 없다.

담을 값: `pageSize`(기본 1000, §7 위험 1 참조), `zone`(기본 `Asia/Seoul`, 실행일 판정 기준).

### 4.5 삭제 실행

종료 축제 삭제는 **`spot` 행을 지우는 것**이며 `spot_festival`은 CASCADE로 따라 사라진다(R-8, AC-30).
`spot_festival`만 지우는 경로를 만들지 않는다 — 관광지 행이 남으면 축제 정보 없는 축제가 조회된다.

`spot_reaction`도 같은 CASCADE로 사라진다. **의도된 동작이며 사용자가 확정했다.**

삭제 대상 조회는 `spot_festival`에서 `language = ? AND event_end_date < ?` 로 키를 모은 뒤,
그 키로 `spot`을 지운다. `idx_spot_festival_end_date`가 이 조회를 받는다.

---

## 5. 테스트가 붙는 단위

스펙의 인수 기준 54건 중 서버 소관은 AC-1~AC-41과 **AC-45~AC-54**(v1.1 신설)다.
**애플리케이션·도메인 계층만 테스트한다** — DB·실제 TourAPI 호출·Spring 컨텍스트는 범위 밖이다.

| 단위 | 담당 기준 |
|---|---|
| `FestivalPeriod` (domain/vo) | AC-15, AC-16, AC-26~28 (종료 판정 경계) |
| `SyncFestivalCommand` (application/command) | AC-4, AC-12, AC-13 |
| `TourApiFestivalSyncClient.mapPage` | AC-9~AC-11, AC-14, AC-17~AC-19, AC-41 |
| `FestivalSyncTasklet` (포트 스텁) | AC-1~AC-3, AC-5, AC-20~AC-25, AC-29, AC-35~AC-40 |
| `SpotSyncTasklet` 변경분 (포트 스텁) | AC-31~AC-34 (재삽입 방지) |
| 상세 조회 application 서비스 (포트 스텁) | **AC-46, AC-48, AC-50~AC-54** (식별자 해소·언어 교차 거부) |
| 반응 저장 application 서비스 (포트 스텁) | **AC-47** |
| 목록 조회 read model | **AC-45** |

**AC-52·AC-53은 스텁 구성이 기준의 일부다.** 캐시 적중·저장소 적중을 **각각 강제한 상태**에서 거부를 확인해야
한다. 스텁이 미스를 돌려주면 폴백 경로만 검증되어 §2.4가 지적한 버그를 그대로 통과시킨다.
AC-54는 지연 필드 진입점을 직접 호출해 검증한다 — 상위 조회를 거쳐서는 독립 진입점임이 드러나지 않는다.

**AC-49는 v1.1에서 애플리케이션 경계 기준으로 다시 쓰였다.** "대응하는 관광지가 없는 식별자는
`SPOT_NOT_FOUND`이며 형식에 따라 다른 코드를 만들지 않는다"를 검증한다 — 스텁이 미해소를 돌려주게 하면
된다. 옛 `contentId` 값도 이 경로로 들어온다.

**형식 오류 자체는 검증하지 않는다.** `ID→Long` 매핑 이후 숫자가 아닌 값은 GraphQL 실행 엔진이 리졸버
이전에 거부하므로 애플리케이션 계층에 도달하지 않는다. 이는 요구사항이 아니라 구현 특성이므로 아래
"인프라로만 관찰 가능해 테스트하지 않는 것"에 속한다.

`TourApiFestivalSyncClient.mapPage`는 HTTP와 분리되어 있으므로 응답 객체를 직접 만들어 호출한다.
`RestClient`를 띄우지 않는다.

### 인프라로만 관찰 가능해 테스트하지 않는 것

솔직하게 적는다. 아래는 단위 테스트로 검증하지 않으며 인수 기준에도 넣지 않았다.

- FK `ON DELETE CASCADE`가 실제로 자식 행을 지우는지 (AC-6·AC-30은 **삭제 대상 키가 올바르게 산출되는지**를
  애플리케이션 계층에서 검증하며, CASCADE 동작 자체는 DB의 책임이다)
- `UNIQUE (content_id, language)` 충돌 시 upsert가 실제로 갱신하는지 (AC-23~25는 저장 포트에 전달되는
  **명령의 모양**을 검증한다)
- `CHECK` 제약의 실제 거부 동작
- Spring Batch의 Step 순서·재개 동작
- **숫자가 아닌 식별자를 GraphQL 실행 엔진이 리졸버 이전에 거부하는 것**(v1.1) — `ID→Long` 매핑의 결과이며
  애플리케이션 계층에 도달하지 않는다. AC-49는 이것이 아니라 "미해소 식별자의 오류 코드"를 검증한다

이들은 마이그레이션과 어댑터 구현이 맞으면 성립하고, 틀리면 통합 환경에서 즉시 드러난다.
스펙의 테스트 범위 표에 따라 인프라 테스트를 지어내지 않는다.

---

## 6. 관측

`SpotSyncTasklet`이 `ExecutionContext`에 남기는 것과 같은 방식으로 축제 Step도 남긴다(R-11).

- `written` / `dropped` — 언어별 (AC-39, AC-41)
- `deleted` — 정리 삭제 건수 (AC-40)
- `cleanupSkipped` — 건너뛴 사유 문자열 (AC-38)

**드랍 건수를 적재 건수와 구분해 남기는 것이 [TBD-2]의 해소 수단이다.** 날짜 파싱 실패가 유의미한 규모로
발생하면 R-4의 "드랍" 정책을 재검토해야 하는데, 이 값이 없으면 판단할 근거가 없다.

로그에 serviceKey와 TourAPI 원본 필드명을 남기지 않는다(POL-013).

---

## 7. 남는 위험

1. **`searchFestival2`의 `numOfRows` 상한이 실측되지 않았다.** 이 레포에서 호출된 적이 없는 오퍼레이션이다.
   `SpotSyncProperties.pageSize`(1000)와 같은 값으로 시작하되 설정으로 두어 코드 수정 없이 낮출 수 있게 한다.
2. **`arrange=Q`의 정렬 이동.** 관광지 수집과 같은 문제가 축제에도 있다. 다만 축제는 건수가 적어 페이지 수가
   적고, 정리 게이트가 0건만 막으므로 경계 누락이 곧 삭제로 이어질 수 있다. 건수가 적다는 것이 위험을 줄이는
   동시에 급감 게이트를 못 쓰게 만든 원인이기도 하다 — 실데이터로 관측이 필요하다.
3. **[TBD-1]** `progress_type`/`festival_type`의 값 분포. 적재 후 질의로 해소한다.
4. **종료 축제 삭제가 `spot_reaction`을 함께 지운다.** 확정된 정책이지만, 좋아요가 쌓인 뒤 첫 대량 삭제가
   발생할 때 건수를 확인해 두는 편이 좋다.
