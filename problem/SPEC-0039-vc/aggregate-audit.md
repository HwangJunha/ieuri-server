# SPEC-0039 — 애그리게이트 루트 감사 (2026-10-09)

규칙(D-122): 저장되는 도메인 데이터는 애그리게이트에 속하고, 쓰기는 루트를 거친다(루트를 읽는다 → 루트 메서드가 판정한다 → 저장한다).
예외는 셋뿐이다 — 조회는 애그리게이트를 건너뛴다(D-40), 도메인 규칙 없는 행의 일괄 삭제, 루트가 판정한 뒤 바뀐 것만 쓰기.

## tourism (작업 대상)

| 패키지 | 루트 | 판정 | 처리 |
|---|---|---|---|
| footprint | `Footprint` | 루트 경유 | — |
| relation | `MapRelation` | 루트 경유. `removeFromMyList`만 id로 지우지만 판정할 규칙이 없다 | — |
| catalog | `SpotCatalog` | 우회 4건: `assign`·`reassign`(catalog 저장소가 spot 테이블을 씀), `saveFestivalPeriod`(루트를 읽지 않음), `refreshTranslationVisibility`·`assignUnassignedDistricts`(규칙이 SQL에만 있음). `deleteTranslations`는 루트가 판정한 뒤라 예외 범위 | 루트 경유·Specification+Command(D-124) |
| spot | `Spot`(자연키 contentId+language, 행동 없음) | 상세(본문·갤러리·운영정보·안내사항)를 `spotId`로 바로 씀 | 루트를 읽어 자식을 만든다(D-123) |
| region | 없었음 | KTO 지표·대표 위치를 명령 DTO·코드로 바로 씀 | `District` 루트(D-122, 9ca01a6d) |
| recommendation | 없었음 | 임베딩을 application DTO로 바로 저장 | `SpotEmbedding` 루트(D-122, 438dd3c1) |
| congestion | — | DB 쓰기 없음(Valkey 캐시) | 대상 아님 |
| category | — | 쓰기 없음(기준 데이터) | 대상 아님 |

## community (조사만 — tourism 다음에 작업)

| 구분 | 내용 |
|---|---|
| 루트가 제대로 있음 | post, comment, question, conversation |
| 루트를 우회하는 쓰기 | ① 숨김 전이: post·comment·question(답변·답글 포함)에 `hide()`가 없고 "삭제되지 않고 보이는 것만 숨긴다"가 SQL에만 있다. 실행은 `ReportModifyService.applyAutoHide`가 남의 테이블 셋을 쓴다. ② conversation `advanceLastMessageAt`: 루트 메서드가 없고 "앞으로만 간다"가 SQL에만 있다. ③ catalog `SpotCatalogSyncService`가 post 자식(post_spot)을 일괄 삭제. ④ "숨긴 것은 이미 수에서 빠졌다"가 어댑터 반환식에만 있다. ⑤ messagereport 중복 금지가 어댑터에만 있다 |
| 이름뿐인 루트 | report(id·행동 없음), block(id·행동 없음), messagereport(행동 없음) |
| 루트 없음 | catalog(사본 데이터, 쓰기 있음), photo(R2만), lookup·shared(쓰기 없음) |
| 기타 | `Message`에 `deletedAt`이 없어 `markMessagesDeletedBySender`가 쓰는 상태가 도메인에 없다. `Post`가 카테고리·장소를 들지 않아 그 변경은 루트 판정 밖이다. required 포트 `deleteMessagesCreatedBefore`가 provided DTO를 돌려준다 |

## alarm (조사만 — alarm 브랜치에서 작업)

| 구분 | 내용 |
|---|---|
| 루트가 제대로 있음 | device(`DeviceToken`) — 쓰기 4건 중 2건 루트 경유, 탈퇴 정리는 예외. `removeInvalid`는 루트 없는 일괄 삭제이고 무효 판정은 FCM 어댑터에 있다 |
| 이름뿐에 가까운 루트 | preference(`NotificationPreference`) — `change`가 `copy`뿐 |
| 루트 없음 | push — 쓰기 없음(읽기 전용 문구, FCM 외부 I/O, 차단 조회) |
| 규칙이 SQL·어댑터에만 | "값이 바뀔 때만 고친다"(UPDATE WHERE), 유일성(기기당 토큰 하나·회원·카테고리당 설정 하나 — DB 제약), 무효 토큰 판정(FCM 어댑터) |
| 사실과 다른 주석 | `notification_message` 시드가 마이그레이션에 있다는 주석(`PushDispatchService`·`NotificationMessageRepository`) — 어느 마이그레이션도 행을 넣지 않는다. 운영자가 넣는 데이터다 |

## 검증 (2026-10-09, D-121~D-124 반영 뒤)

| 항목 | 결과 |
|---|---|
| `./gradlew check` | 통과 |
| api-dev 조회 비교(깊이 4·6) | 76/77 — 다른 1건은 `post` 조회수 부수 효과(전과 같음) |
| 오류 응답 | 39/39 |
| 상세 write-behind(원본 루트 경유) | 로컬이 운영정보 행 2개를 적재(`LEISURE_SPORTS`), 서버 로그에 적재 실패 없음. 같은 요청의 응답이 dev와 5/5 같음 |
| 저장된 원본의 `Spot` 불변식 | 위반 행 0(https 썸네일·제목·좌표 범위) — 읽을 때 다시 검사해도 실패하지 않는다 |
| reviewer | PASS-with-warnings — 응답·DB를 바꾸는 확정 결함 없음 |
| 배치 DB 대조 | 미실행 — 오늘 실행분이 끝나 있어 실행 기록 삭제 후 진행 |
