> spec: SPEC-0033 · source: `Specs/SPEC-0033/spec.md` (Obsidian) · status: approved

# SPEC-0033 — 좋아요 목록 내 검색 (서버 설계)

요구사항·수용 기준·계약 전문은 스펙 문서에 있습니다. 이 문서는 이 저장소에 한정된 설계 판단만 담습니다.

## 이 변경의 성격

`Query.likedSpots`에 선택 인자 `query: String` 하나를 더하고, 좋아요 집합에 텍스트 필터를 겁니다. 새 루트 필드도 새 타입도 없습니다.

## 반드시 먼저 읽을 것 — BM25를 재사용하지 않는다

`spot_reaction`이 검색 노드에 복제돼 있고 `idx_spot_reaction_liked_list`도 있으므로 "BM25 검색에 좋아요 조건을 붙이면 되겠다"는 접근이 자연스러워 보입니다. **그 길은 막혀 있습니다.** `SpotSearchJdbcAdapter.kt`가 이미 실측 결과를 주석으로 남겨두었습니다.

- 69-71행 — `@@@`·`pdb.score()`·`ORDER BY`·`LIMIT`이 전부 같은 질의 레벨에 있어야 pg_search가 BM25 인덱스로 밀어넣습니다. 서브쿼리·CTE·조인으로 감싸면 `query is incompatible with @@@ operator`로 **실패합니다**. `spot_reaction JOIN spot_catalog_translation ... WHERE title @@@ ...`은 성립하지 않습니다.
- `idx_spot_catalog_translation_bm25`는 `id`·`title`(4개 로케일)·`address`만 색인합니다. `member_id`가 색인에 없으므로 "내가 좋아요한 것"을 BM25 질의 안으로 넣을 방법이 없습니다.
- 76-77행 — `pdb.score()`는 SELECT 전용 자리표시자라 WHERE에 쓸 수 없고, 그래서 관련도 정렬로는 커서 seek이 불가능합니다.

SPEC-0013/0019가 좌표·카테고리를 번역 행에 **복사**해 이 제약을 우회했지만, 같은 수를 여기 쓸 수 없습니다. 좋아요는 회원 수 × 좋아요 수로 늘어나는 관계 데이터라 번역 행에 복사할 수 있는 성질이 아닙니다.

**그래서 방향이 반대입니다.** 코퍼스를 검색해 좋아요로 좁히는 것이 아니라, **회원의 좋아요 집합을 먼저 확정하고 그 유계 집합에 텍스트 필터를 겁니다.** BM25도, 관련도 점수도, `@@@`도 이 경로에 등장하지 않습니다. 평범한 대소문자 무시 부분 일치로 충분합니다 — 집합이 유계이기 때문입니다.

이 판단이 뒤집히면 정렬·페이지네이션·계약이 전부 따라 바뀝니다. 다르게 구현해야 할 근거를 찾으면 `[AMEND]`로 올려주세요.

## 조회 경로

기존 `likedSpots` 조회 경로를 그대로 쓰고 WHERE에 텍스트 조건을 더하는 형태입니다.

- 구동 인덱스는 기존 `idx_spot_reaction_liked_list(member_id, language, reaction, updated_at DESC, catalog_id DESC)`입니다. 정렬 키가 `likedAt DESC`로 유지되므로 이 인덱스가 계속 유효하고, 커서 조건도 기존과 같은 모양입니다.
- 텍스트 조건은 인덱스로 좁혀진 뒤 평가되는 잔여 술어입니다. 좋아요 집합이 회원당 유계라 이것으로 충분합니다 — 별도 인덱스를 새로 만들 근거가 지금은 없습니다.
- 마이그레이션이 필요한지 여부를 포함해 **스키마 판단은 이 저장소가 소유합니다.** 위 문단은 근거이지 지시가 아닙니다.

주소가 null인 행이 있으므로 텍스트 조건이 null에서 안전해야 합니다(AC2.3).

## 검증과 오류

`SpotQueryPolicy`에 이미 `maxSearchQueryLength`(100, POL-035)가 있습니다 — `searchSpots`가 쓰는 값입니다. **새 상수를 만들지 말고 이것을 재사용하세요.** 같은 정책의 같은 상한이 두 곳에 흩어지면 다음에 바뀔 때 한쪽만 바뀝니다.

- 트림 후 빈 문자열 → `EMPTY_QUERY`. 인자 생략·null과 구분해야 합니다 — 생략은 필터 없음이고 오류가 아닙니다.
- 트림 후 100자 초과 → `QUERY_TOO_LONG`.
- 검증은 application 계층에서 수행합니다. 이 위치라야 수용 기준이 어댑터 없이 단위 테스트로 검증됩니다.

**오류 전달 통로가 `searchSpots`와 다릅니다.** `searchSpots`는 `SpotSearchPayload{success, userErrors, connection}`로 감싸 `userErrors[]`에 담지만, `likedSpots`가 반환하는 `LikedSpotConnection`에는 그 래퍼가 없습니다. 따라서 신규 두 코드는 **최상위 `errors[]`**로 나가야 하며, 이는 이 필드의 기존 `EMPTY_CATEGORY_SET`과 같은 통로입니다.

**다만 `EmptyCategorySetException`을 본뜨지 마세요.** 그것은 `common` 패키지에 있고 `SecurityGraphqlExceptionHandler`의 **닫힌 `when` 블록**(37-46행)이 타입별로 하나씩 열거해 처리합니다 — 거기 없는 예외는 `InternalErrorSanitizingHandler`로 떨어져 코드 없이 `INTERNAL_ERROR`가 됩니다. 그 자리에 있는 이유는 카테고리 인자를 **두 도메인이 함께 쓰기 때문**이고(주석에 그렇게 적혀 있습니다), 이 스펙의 검색어는 tourism 전용입니다.

**`SpotQueryException`의 서브클래스로 만드세요.** `TourismExceptions.kt`의 이 추상 클래스가 tourism 조회 오류의 확장 지점이며, `TourismQueryExceptionHandler`가 `is SpotQueryException` 하나로 받아 `code`를 `extensions`에 실어 최상위 `errors[]`로 내보냅니다(34행). `RADIUS_TOO_LARGE`·`UNKNOWN_CATEGORY`·`INVALID_CURSOR` 등 기존 코드 전부가 이 경로이고, **핸들러를 고칠 필요가 없습니다.** 공유 인프라를 건드리지 않는다는 점에서도 이쪽이 맞습니다.

`Classification`은 둘 다 `BAD_REQUEST`가 적절합니다 — 기존 `RADIUS_TOO_LARGE`와 성격이 같은 입력 검증 실패입니다.

`LikedSpotConnection`을 payload 타입으로 바꿔 두 필드의 규약을 통일하고 싶어질 수 있는데, 배포된 클라이언트를 깨는 파기 변경이라 이 스펙의 범위 밖입니다. 필요하다고 판단되면 별도 이슈로 올려주세요.

검색어 원문을 로그·오류 메시지에 남기지 않습니다(POL-013, AC7.3). 길이 초과를 기록할 때도 길이만 남기고 내용은 남기지 않습니다.

## 테스트가 붙는 단위

단위 테스트만 작성합니다 — application·domain 계층에 한정하고, 어댑터·DB·실제 GraphQL 전송은 범위 밖입니다.

| 단위 | 검증 대상 |
|---|---|
| 좋아요 목록 조회 핸들러(application) | `query` 생략·null·값 있음의 분기(AC1.1~1.3), 카테고리와의 AND 결합(AC5.1~5.2), 커서 페이지네이션 유지(AC4.1~4.3) — 아웃바운드 포트는 스텁 |
| 검색어 검증(application, `SpotQueryPolicy` 사용) | 트림·빈 문자열·100자 경계(AC6.1~6.4) |

정렬(AC3.1~3.2)과 텍스트 매칭 규칙(AC2.1~2.3)은 포트 스텁이 돌려주는 데이터로 핸들러 수준에서 검증하거나, 매칭 판정이 도메인으로 내려온다면 그 도메인 단위에서 검증합니다. 어느 쪽이든 DB 없이 검증 가능한 위치라야 합니다.

실제 SQL의 인덱스 사용 여부는 인프라를 세워야만 관찰되는 성질이라 **테스트하지 않고 남겨둡니다.** 인프라 테스트 계획을 세우지 마세요.

## 이 슬라이스가 열어주는 것

`query` 인자가 선택적이므로 이 변경이 먼저 배포돼도 기존 클라이언트는 영향받지 않습니다. 배포 즉시 클라이언트(`k-trip-spot` #88)가 실제로 필터링되는 `likedSpots(query:)`에 대고 화면을 배선할 수 있습니다.

## 관련

- 이슈: #121 · 클라이언트 대응: `k-trip-spot` #88
- 브랜치: `feature/SPEC-0033-liked-spots-search` (`dev`에서 분기)
- 선행: SPEC-0008(좋아요 목록 신설), SPEC-0020(`categories` 인자 추가 — 같은 필드를 확장한 선례), SPEC-0032(`@auth(REQUIRED)`)
- 배경: SPEC-0013/0019(pg_search·BM25 — 여기서 **재사용하지 않는** 경로)
