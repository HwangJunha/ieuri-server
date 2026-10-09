# SPEC-0039 — 애그리게이트 루트 감사 (2026-10-09)

규칙(D-122): 저장되는 도메인 데이터는 애그리게이트에 속하고, 쓰기는 루트를 거친다(루트를 읽는다 → 루트 메서드가 판정한다 → 저장한다).
예외는 셋뿐이다 — 조회는 애그리게이트를 건너뛴다(D-40), 도메인 규칙 없는 행의 일괄 삭제, 루트가 판정한 뒤 바뀐 것만 쓰기.

## tourism (작업 대상)

| 패키지 | 루트 | 판정 | 처리 |
|---|---|---|---|
| footprint | `Footprint` | 루트 경유 | — |
| relation | `MapRelation` | 루트 경유. `removeFromMyList`만 id로 지우지만 판정할 규칙이 없다 | — |
| catalog | `SpotCatalog` | 우회 4건: `assign`·`reassign`(catalog 저장소가 spot 테이블을 씀), `saveFestivalPeriod`(루트를 읽지 않음), `refreshTranslationVisibility`·`assignUnassignedDistricts`(규칙이 SQL에만 있음). `deleteTranslations`는 루트가 판정한 뒤라 예외 범위 | 소속·축제 기간은 루트 경유(D-124). 노출·시군구 일괄 반영은 SQL에만 규칙이 있는 한계로 기록(D-125) |
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
| 배치 DB 대조 | 아래 "최종 코드 배치 대조" |

### 최종 코드(D-125~D-127 반영) 재검증

| 항목 | 결과 |
|---|---|
| api-dev 조회 비교 | 깊이 4·6 모두 76/77 — 다른 1건은 `post` 조회수 부수 효과. 깊이 4 첫 실행은 api-dev 읽기 타임아웃으로 끊겨 재실행 |
| 오류 응답 | 39/39 |
| 쓰기(main-a) | 46/46 같음, 쓰기 뒤 상태 검증 40개 실패 0(양쪽) |
| 원상복구(main-b) | 2/2 같음 |

### 최종 코드 배치 대조 (runDate 2026-10-09, 사용자가 실행 기록을 지운 뒤 8개 잡 — 임베딩 제외)

실행 전 상태는 dev(옛 코드)가 약 17시간 전에 돌린 결과라 업스트림 변화가 섞인다. 그래서 수가 아니라 규칙 위반과 옛 SQL 재계산으로 본다.

| 잡 | 결과 |
|---|---|
| spotSyncJob | 규칙 위반 0 — 노출 ≠ 원본 존재 0, community 사본 누락·초과 0(노출 80,478 = 사본 80,478), 끝난 원본 축제 0, 원본 없는 축제 0, 번역 없는 카탈로그 0, 미배정 원본 2(전과 같음). 새 카탈로그 17개(루트 `admit`·`festivalFrom` 경로) 모두 원본이 붙었고, 축제 원본이 있는 5개의 기간이 옛 규칙(언어 우선순위 → 원본 id) 계산과 5/5 같다. 축제 원본 415 = 언어별 적재 수 합 |
| spotDistrictAssignmentJob | 경계 밖 배정 0, 경계 안 미배정 0 |
| districtRepresentativeLocationJob | dev SQL과 252개 시군구 모두 일치(선정 213, 빈 곳 39) |
| spotDetailPrefetchJob(한도 200) | 본문 29·운영 정보 30 실패 0, 갤러리·이용안내는 한도 소진. TourAPI 타임아웃 4건은 적재하지 않음(규칙). 원본 부재·미저장 예외 0 |
| KTO 4종 | 처리 수가 옛 코드 실행과 같다(집중률 227,010 / 방문자 39,964·미등록 8,457 / 체류·소비 221·미등록 25·23). 날짜별 행 수·합계 같고 `updated_at`이 움직이지 않았다 — 같은 값이면 고치지 않는 동작이 그대로다 |
