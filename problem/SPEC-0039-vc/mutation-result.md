# SPEC-0039 VC-001 — GraphQL 동작 비교 결과 (2026-09-28)

비교 대상: 로컬 = `feature/SPEC-0039-comment-trim`(#166, #165 포함) · dev = `api-dev.ieuri.co.kr`. 두 서버는 같은 dev DB·Valkey를 쓴다.
계획은 `mutation-plan.md`. 이번 실행은 임시 스크립트로 했고, 케이스는 `ieuri-QA/graphql` 스위트로 옮긴다(서버 레포에는 두지 않는다). 원본 응답은 커밋하지 않는다(POL-013).

## 요약

| 구분 | 요청 | 같음 | 다름(원인) |
|---|---|---|---|
| Query 전체(35개 루트, 성공·오류) | 53 | 52 | 1 — `post.viewCount`: `post` 조회가 조회수를 올려 뒤에 부른 쪽이 +1 (호출마다 재현) |
| Mutation 오류 경로 | 39 | 38 | 1 — 자기 댓글 신고: 로컬이 먼저 신고해 dev가 `ALREADY_REPORTED` (순서) |
| Mutation 실행(45개, 46단계) | 46 | 44 | 2 — 실행기 쪽 원인(아래) |
| 정리·교차 중복 | 4 | 4 | 0 |

**코드로 인한 응답 차이는 0건이다.** 상태 검증(Query 40개 + DB 조회)은 양쪽 모두 기대대로였다.

## 다름으로 나온 항목의 원인

- `4a changeNickname` — 실행기가 입력 닉네임에 쪽 이름을 붙였고(`VC검증local`/`VC검증dev`) 응답이 입력을 그대로 돌려준 것. 실행기를 같은 값으로 고쳤다.
- `23 confirmPostImageUploads` — 응답은 같다. 로컬 실행 뒤 검증식을 고쳐 검증 이름만 다르다(아래 계획 정정 3).
- `post.viewCount`, 자기 댓글 신고 — 실행 순서가 만든 차이. 코드와 무관.

## 상태 검증 (양쪽 동일)

- **Q**: 토큰 발급·재발급 뒤 `me`, 닉네임 변경·복구, 약관 MARKETING 변경·복구, 필수 약관 멱등, 프로필 이미지 업로드→확정→제거,
  글 생성·수정·좋아요·취소·댓글·삭제, 남의 글 숨김·해제, 글 사진 업로드→확정→삭제, 차단·해제, 장소 반응, 방문 기록·해제,
  공유 링크 발급·조회·폐기, 지도 관계 생성·해제, 장소 질문·해결·답변·답글·삭제, 대화 시작·메시지·읽음(unreadCount 0).
- **DB**: 기기 토큰 추가·삭제(기존 토큰 유지), 알림 설정 MESSAGE false→true, 신고 행, 반응 행, 대화·메시지 행, 방문 0건,
  활성 공유 링크 0건, 차단·숨김 0건, 약관·프로필 원래 값.

## 계획의 전제가 틀렸던 것 (서버 동작은 정상)

1. **자기 댓글도 신고할 수 있다.** 계획은 거절을 예상했다. 오류 경로가 아니라 실제 신고가 1건 생겼다(106).
2. **`removeMapRelation`은 내 목록에서만 지운다.** 스키마 설명대로다. 상대 목록의 55는 남는다.
3. **`confirmPostImageUploads`는 키를 `pending/…`에서 `images/…`로 옮기고 확정 키를 돌려준다.** 실행기가 로컬에서 옮기기 전 키로
   `deletePostImage`를 불러, 확정된 사진은 뒤에 확정 키로 따로 지웠다. 실행기를 확정 키를 쓰도록 고쳤다.

## 잔여물 (되돌리는 API가 없어 dev에 남은 것)

- 신고 3건: 회원 55 → 댓글 131(자기 댓글)·129·130 (자동 숨김 임계값 5명 미만)
- 장소 반응 2건: 55 → 341426·356978 LIKE
- 대화 2건·메시지 2건: 55↔101(대화 37), 55↔146(대화 38) — 사용자 본인 계정
- 지도 관계 2건: 101→55, 146→55 (상대 목록 쪽)
- 알림 설정 행: 55 MESSAGE enabled=true (기본값과 같은 상태)
- 소프트 삭제된 글·댓글·질문·답변 행

## 함께 발견한 기존 결함 (dev도 같음 — 이 spec 범위 밖, BR-001)

- **업무 예외가 `INTERNAL_ERROR`로 나간다.** `InvalidZoomException`·`BboxTooLargeException` 등이 `InvocationTargetException`에
  감싸여 오류 처리기가 알아보지 못한다. 영향: `mapViewport`(줌·bbox), `spots`(반경 초과), 없는 id의 `spotDetail`·`mapCell`·
  `spotAnswers`, 잘못된 토큰의 `sharedMap` — 클라이언트는 코드 대신 "잠시 후 다시 시도해 주세요"를 받는다.
- **`spotDetail`의 `category.code`가 null이라 non-null 위반 오류가 난다**(조회한 장소 기준).

## 호출하지 않은 것

- 회원가입 2종(지시), `withdrawMember`(공용 계정 삭제)
- 외부 발송·비가역 성공 경로(이메일 인증·재설정·변경, SNS 로그인, 관리자 권한 Mutation) — 오류 경로만
- Subscription 2종(WebSocket)

---

# 재실행 — community·tourism 전체 (2026-10-08, D-112 반영 후)

로컬 = `feature/SPEC-0039-tourism`(tourism 이전 + community `shared` 정리 + D-112) · dev = `api-dev.ieuri.co.kr`. 배치는 제외했다.
범위는 community·tourism 스키마의 루트 65개(Query 31 · Mutation 34 · Subscription 2) 전부다. 같은 계정(55), 같은 입력.

| 구분 | 요청 | 같음 | 다름(원인) |
|---|---|---|---|
| Query(응답 깊이 4·6 두 번, 깨진 커서 18건 포함) | 77 | 76 | 1 — `post.viewCount`(먼저 부른 쪽 조회가 +1) |
| `spotDetail` 필수 인자 필드(`intro`·`infoItems`·`gallery`, dev 오류를 피하려 `category` 제외) | 8 | 8 | 0 |
| Mutation 오류 경로 | 39 | 39 | 0 |
| Mutation 실행 | 46 | 46 | 0 |
| 조회 분기(필터·커서 다음 페이지) | 9 | 9 | 0 |
| Subscription(graphql-transport-ws) | 5 | 5 | 0 |

**dev와 응답이 다른 것은 조회수 부수 효과 1건뿐이다.**

- 처음 실행(같은 날, D-112 전)에는 Query 13건·Mutation 오류 1건이 달랐다 — 로컬이 dev의 결함(`category.code` null, 업무 예외의 INTERNAL_ERROR)을 고친 응답을 냈다.
  응답 변경은 클라이언트에게 장애라 D-112로 dev 응답으로 되돌렸다. 결함은 dev·로컬 모두 남아 있다(아래).
- **Mutation 뒤 상태 검증 40개**: 양쪽 모두 통과. 실행기의 두 가정을 고쳤다 — 닉네임 입력에 쪽 이름을 붙이던 것, 대화가 처음 생긴다고 보고 "메시지 1건"을 보던 것(이제 "최신 메시지가 보낸 것").
  신고 선행은 9월 실행의 신고가 남아 양쪽 모두 같은 중복 응답이다 — 일회성 동작이라 정상이다.
- **Subscription**: 토큰 없는 `connection_init`은 4401로 닫힘, 비참여 대화 구독은 두 종 모두 FORBIDDEN, 참여 대화의 `newMessage`는 HTTP로 보낸 메시지를 같은 id로 전달,
  자기 `notifyTyping`은 `typingIndicator`에서 걸러져 이벤트가 오지 않는다. 두 서버가 Valkey를 공유하므로 한쪽씩 차례로 돌렸다.
  상대의 타이핑이 실제로 전달되는지는 상대 계정(101·146, SNS 로그인) 토큰이 없어 확인하지 못했다.

## dev와 로컬 모두에 남아 있는 응답 결함 (고치려면 클라이언트와 합의한 별도 SPEC)

- `spotDetail`의 `category`를 고르면 `category.code` non-null 위반으로 `spotDetail` 전체가 null이다.
- 업무 예외가 INTERNAL_ERROR로 나간다: 없는 `spotDetail`·`mapCell`, 잘못된 `sharedMap` 토큰, `spots` 반경 초과, `mapViewport` 줌·bbox, `createMapRelation` 잘못된 URL, `spotAnswers` 없는 질문.
- 거리순·인기순(`ongoingFestivals`·`popularSpots`) 깨진 커서는 INVALID_CURSOR가 아니다.

## 이번 실행이 dev에 더 남긴 것

- 대화 37·38에 Mutation 실행 메시지, 대화 37에 구독 검증 메시지 — 사용자 본인 계정과의 대화
- 소프트 삭제된 글·댓글·질문·답변 행
