# SPEC-0039 VC-001 — Mutation 검증 계획

리팩토링된 코드(로컬)와 dev 서버(`api-dev.ieuri.co.kr`)에 **같은 Mutation을 같은 입력으로** 보내고,
(1) 응답이 같은지, (2) 기대한 상태 변화가 실제로 일어났는지를 확인한다. 두 서버는 같은 dev DB·Valkey를 쓴다.

- 계정: dev 공용 계정 회원 **55**(`dev-account@example.com`). 상대가 필요한 경우 사용자 본인 계정 **101**(KAKAO)·**146**(GOOGLE).
- 실행 순서: 로컬 전체 → dev 전체. 되돌릴 수 있는 것은 단계 안에서 원상복구하므로 두 번째 실행도 같은 초기 상태에서 시작한다.
- 한 번만 가능한 동작(신고·대화 시작)은 **대상을 교차**한다: A는 로컬이 먼저(성공)·dev가 뒤(중복), B는 반대. 성공끼리·중복끼리 비교한다.
- 실행 도구는 서버 레포에 두지 않는다. 케이스는 `ieuri-QA/graphql` 스위트(Dart `test`)로 옮기고, 같은 스위트를 dev와 로컬에 돌려 둘 다 통과하는 것으로 확인한다. 원본 응답은 커밋하지 않는다(POL-013).

## 검증 방법의 두 종류

| 기호 | 방법 | 쓰는 경우 |
|---|---|---|
| **Q** | 같은 서버에 GraphQL Query를 보내 결과로 확인 | 상태가 Query로 드러나는 것(글·댓글·약관·차단·닉네임…) |
| **DB** | postgres MCP(읽기 전용)로 행을 조회 | Query로 보이지 않는 것(기기 토큰·알림 설정·신고·고아 사진·반응 행) |

## 응답 비교에서 정규화하는 값

호출마다 달라지는 값만 자리표시로 바꾸고, **배열 순서는 바꾸지 않는다**(POL-019·055):
새로 만든 엔티티 id(`id`·`postId`·`commentId`·`questionId`·`answerId`·`replyId`·`conversationId`·`messageId`),
시각(`createdAt`·`updatedAt`·`lastMessageAt`·`sentAt`), 토큰(`accessToken`·`refreshToken`), 발급 URL·키
(`uploadUrl`·`objectKey`·`profileImageUrl`·`shareUrl`·`expiresAt`), 교차 대상 id(`targetId`·`counterpart.id`·`nickname` — 교차 단계만).

## 목록 (62)

### 제외 (3)

| Mutation | 이유 |
|---|---|
| registerSnsMember · registerEmailMember | 회원가입 — 사용자 지시로 제외 |
| withdrawMember | 공용 계정이 삭제되어 다른 사람이 쓸 수 없게 된다 |

### 오류 경로만 호출 (성공 경로가 외부 발송·비가역·권한 필요) (14)

loginEmail·refreshToken·logout·createMapRelation의 오류 입력도 여기서 함께 부르지만, 이 넷은 아래에서 성공 경로도 실행한다.

모두 **같은 입력을 양쪽에 보내 응답(코드·메시지·위치)이 같은지**만 본다. 상태 변화 없음.

| Mutation | 입력 | 기대 |
|---|---|---|
| verifyEmail | 없는 verificationId | 오류 코드(인증 정보 없음) |
| resendVerification | 없는 verificationId | 오류 코드 |
| loginSns | GOOGLE + 형식이 틀린 idToken | 토큰 검증 실패 코드 |
| loginEmail | 없는 이메일 | 자격 증명 실패 코드(계정 존재를 드러내지 않음) |
| requestPasswordReset | 없는 이메일 | MEMBER_NOT_FOUND (메일 발송 없음) |
| resetPassword | 없는 이메일·임의 코드 | 실패 코드 |
| refreshToken | 형식이 틀린 토큰 | 토큰 오류 코드 |
| logout | 형식이 틀린 토큰 | 토큰 오류 또는 성공(멱등) — 양쪽 동일 |
| suspendMember · reactivateMember · assignRole · revokeRole | 대상 52 | FORBIDDEN (공용 계정에 관리 권한 없음) |
| changeEmail | 형식이 틀린 주소 / 현재와 같은 주소 | 형식 오류 / 같은 주소 오류 (메일 발송 없음) |
| confirmEmailChange · resendEmailChange | 없는 verificationId | 오류 코드 |
| retargetEmailVerification | 새 주소 | 이미 인증된 회원이라 거절 |
| reportMessages | 없는 메시지 id | 오류 코드 |
| createMapRelation (오류) | 형식이 틀린 URL | SHARE_LINK_NOT_FOUND |

### 실행 + 검증 (45개 Mutation, 40단계)

원상복구 칸이 `—`인 것은 되돌리는 API가 없어 **잔여물**로 남는다(아래 잔여물 절).

| # | Mutation | 입력 | 기대 응답 | 검증 | 원상복구 |
|---|---|---|---|---|---|
| 1 | loginEmail | 공용 계정 | success, 토큰 2개, tokenType Bearer | Q: 받은 토큰으로 `me.id = 55` | — |
| 2 | refreshToken | 1의 refreshToken | success, 새 토큰 | Q: 새 accessToken으로 `me` | — |
| 3 | logout | 2의 refreshToken | success | 응답만(재사용 검증은 회원 토큰 전체 폐기를 일으켜 하지 않는다) | — |
| 4 | changeNickname | 임시 닉네임 | success, 새 닉네임 | Q: `me.nickname` | 원래 닉네임으로 다시 변경 |
| 5 | updateAgreement | MARKETING true | success | Q: `agreements` MARKETING agreed=true | MARKETING false |
| 6 | agreeToRequiredTerms | SERVICE·PRIVACY·LOCATION | success(이미 동의 — 멱등) | Q: `agreements` 필수 3개 true | — (변화 없음) |
| 7 | createProfileImageUploadUrl | image/png, 67 bytes | success, uploadUrl·objectKey | 발급 URL로 1×1 PNG 업로드 | 11·12 |
| 8 | confirmProfileImageUpload | 7의 objectKey | success, profileImageUrl | Q: `me.profileImageUrl` 있음 | 9 |
| 9 | removeProfileImage | — | success | Q: `me.profileImageUrl = null` | — |
| 10 | registerDeviceToken | 새 deviceId, IOS, KO | success | DB: `alarm.device_token`에 (55, deviceId) | 11 |
| 11 | deleteDeviceToken | 10의 deviceId | success | DB: 행 없음, 기존 토큰 68은 그대로 | — |
| 12 | updateNotificationPreference | MESSAGE false | success | DB: `alarm.notification_preference` (55, MESSAGE, false) | MESSAGE true |
| 13 | createPost | 제목·본문·태그 2·카테고리, 사진 없음 | success, post | Q: `post(id)`·`myPosts` 첫 항목 | 21 |
| 14 | editPost | 13의 글, 제목·본문 변경 | success | Q: `post(id).title/body` | — |
| 15 | likePost | 13의 글 | success | Q: `post.likeCount=1`, 좋아요 표시 | 16 |
| 16 | unlikePost | 13의 글 | success | Q: `likeCount=0` | — |
| 17 | createComment | 13의 글 | success, comment | Q: `comments(postId)` 1건 | 18 |
| 18 | deleteComment | 17의 댓글 | success | Q: `comments` 0건 | — |
| 19 | hidePost | 남의 글 163 | success | Q: `posts(hiddenByViewer:true)`에 163 | 20 |
| 20 | unhidePost | 163 | success | Q: 목록에서 빠짐 | — |
| 21 | deletePost | 13의 글 | success | Q: `post(id) = null` | — |
| 22 | createPostImageUploadUrls | 1장 | success, url·key | 발급 URL로 업로드 | 24 |
| 23 | confirmPostImageUploads | 22의 key | success | 응답 | 24 |
| 24 | deletePostImage | 22의 key | success | DB: `community_orphan_photo_object` 확인 | — |
| 25 | blockUser | 101 | success | Q: `blockedUsers` 1건 | 26 |
| 26 | unblockUser | 101 | success | Q: 0건 | — |
| 27 | reactToSpot | 로컬: 장소 X, dev: 장소 Y · LIKE | success | Q: `likedSpots`에 포함 / DB: `spot_reaction` | — (취소 API 없음) |
| 28 | recordVisits | 시군구 11110 | success | Q: `mapCells` 칸 2의 방문 표시 | 29 |
| 29 | removeVisit | 칸 2 | success | Q: 방문 해제 / DB: `member_district_visit` 0건 | — |
| 30 | createMapShareLink | — | success, shareUrl | Q: `sharedMap(token)` 조회됨 | 31 |
| 31 | revokeMapShareLink | 30의 URL | success | Q: `sharedMap` 실패 | — |
| 32 | createMapRelation | 로컬: 101의 링크 / dev: 146의 링크 | success | Q: `mapRelations` 1건 | 33 |
| 33 | removeMapRelation | 상대 id | success | Q: 0건 / DB: `map_relation` 없음 | — |
| 34 | createSpotQuestion | 장소 X, 본문 | success | Q: `spotQuestions(mine:true)` | 38 |
| 35 | resolveSpotQuestion | 34, true | success, resolved | Q: `resolved=true` | — |
| 36 | createSpotAnswer | 질문 54(본인 계정 146의 질문) | success | Q: `spotAnswers(54)` 포함 | 37 |
| 37 | createSpotAnswerReply → deleteSpotAnswerReply → deleteSpotAnswer | 36의 답변 | success ×3 | Q: 답글·답변 사라짐 | — |
| 38 | deleteSpotQuestion | 34 | success | Q: `mine` 목록에서 빠짐 | — |
| 39 | startConversation → sendMessage → notifyTyping → markConversationRead | 로컬: 101, dev: 146 | success ×4 | Q: `conversations` 1건·`conversationMessages` 1건·unreadCount 0 | — (삭제 API 없음) |
| 40 | reportContent | 교차: A=댓글 129, B=댓글 130 | 선행 쪽 success / 후행 쪽 중복 거절 | DB: `community_report` (55, COMMENT, 129/130) | — |

### 입력 오류 경로 (실행 목록의 Mutation, 상태 변화 없음)

| Mutation | 입력 | 기대 |
|---|---|---|
| createPost | 빈 제목 / 51자 제목 / 태그 6개 | 제목 필수 / 길이 오류 / **INVALID_INPUT**(D-05의 생성 경로) |
| editPost | 남의 글 163 / 태그 6개 | NOT_POST_OWNER / **TOO_MANY_TAGS**(D-05의 수정 경로) |
| deletePost · likePost · createComment | 없는 글 | 글 없음 |
| blockUser · startConversation | 자기 자신(55) | 자기 자신 거절 |
| sendMessage | 없는 대화 | 대화 없음/권한 없음 |
| reactToSpot | 없는 장소 | 장소 없음 |
| recordVisits | 없는 시군구 코드 | 코드 오류 |
| createSpotQuestion | 빈 본문 | 본문 필수 |
| createSpotAnswer | 없는 질문 | 질문 없음 |
| reportContent | 자기 댓글 131 / 없는 대상 | 자기 신고 거절 / 대상 없음 |
| createProfileImageUploadUrl | image/gif | 허용되지 않은 형식 |
| changeNickname | 빈 닉네임 | 닉네임 오류 |
| createPost (비로그인) | — | UNAUTHENTICATED |

## 잔여물 (되돌리는 API가 없어 남는 것)

- 장소 반응 2건(로컬 X·dev Y, 회원 55)
- 대화 2건과 메시지 2건(55↔101, 55↔146 — 사용자 본인 계정)
- 신고 2건(회원 55 → 댓글 129·130). 자동 숨김 임계값은 서로 다른 신고자 5명이라 숨김은 일어나지 않는다
- 삭제 처리된 글·댓글·질문·답변 행(소프트 삭제)

## 결과

실행 후 `mutation-result.md`에 항목별 결과(같음/다름과 이유, 검증 통과 여부)를 기록한다.
