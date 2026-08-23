# SPEC-0017 클라이언트 작업 프롬프트 (서버 계약 확정본)

> 이 문서를 클라이언트 세션에 그대로 붙여넣는다. 서버 구현이 끝난 뒤 **실제 스키마와 코드에서
> 추출한 사실**만 담았다. 아래에 없는 필드·인자·에러 코드는 **존재하지 않는다** — 필요해 보이면
> 만들지 말고 서버에 물어라.

---

## 0. 먼저 알아야 할 것

`problem/SPEC-0017-client.md`는 **서버 구현 전에 쓰였다.** 대조 결과 **틀린 내용은 없었지만,
서버가 구현 중 확정한 4가지가 통째로 빠져 있다.** 아래 1~4가 그것이다.

문서에 이미 맞게 적힌 것(총 개수 미제공·`hasNextPage` 판정, 좋아요 멱등, 타이핑 유실 허용과
`emittedAt` 타임아웃 해제, DM 진입점을 피드에만, DM 신고의 다중 선택 규약, spot 필수·사진 1~5장)은
**그대로 유효하다.** 다시 설계하지 마라.

---

## 1. 피드 인용은 **대화방이 아니라 메시지**에 붙는다

`Conversation`에 `originFeedItem` 같은 필드는 **없다.** 인용은 `Message.feedItem`이다.

```graphql
Message {
  feedItem: FeedItem   # nullable — 피드가 삭제되면 null, 메시지는 남는다
}
```

- 인용은 **말풍선마다** 그린다. 채팅방 상단 고정 배너가 아니다 (인스타그램 스토리 답장과 같은 형태).
- 같은 상대와의 방은 하나인데 피드는 여러 개다. 방에 고정하면 **틀린 맥락**이 뜬다.
- `feedItem`이 null인 말풍선(삭제된 피드)도 **말풍선 자체는 보여야 한다.** 숨기면 대화 기록이 사라진다.

## 2. DM 사진 전송이 있다

```graphql
input SendMessageInput {
  conversationId: ID!
  body: String!        # ← non-null. 아래 주의사항 필수
  feedItemId: ID       # 인용할 피드
  photoKey: String     # 첨부 사진
}
```

**⚠️ 가장 흔히 틀리는 지점: 사진만 보낼 때도 `body`에 빈 문자열 `""`을 넣어야 한다.**
`body`는 `String!`(non-null)이라 생략하면 GraphQL 검증 단계에서 실패한다.
`body`·`feedItemId`·`photoKey`가 **셋 다** 비면 서버가 거부한다.

업로드 경로는 **피드와 완전히 동일하다. DM 전용 업로드 뮤테이션은 없다:**
`createPostImageUploadUrls` → R2에 직접 PUT → `confirmPostImageUploads` → 확정 키를 `photoKey`로 제출.

수신 시 `Message.photoKey`가 있으면 이미지로 렌더한다(CDN 베이스 URL과 조합. 서버는 바이트를 중계하지 않는다).

## 3. `newMessage` 구독이 있다 — **폴링을 만들지 마라**

```graphql
subscription {
  newMessage(conversationId: ID!): Message!
}
```

문서에는 `typingIndicator`만 있지만 서버에는 **둘 다** 있다. 채팅방에서 구독은 **2개**를 연다.

**세 가지를 반드시 처리해야 한다:**

1. **재연결 시 `conversationMessages`로 공백을 메운다.** 이 구독은 "새 메시지가 있다"는 **신호이지
   전달 보장이 아니다.** 끊긴 동안의 메시지는 오지 않는다. 원본은 언제나 DB다.
   (타이핑과 결정적으로 다른 점 — 타이핑은 버려도 되지만 메시지는 안 된다.)
2. **자기 메시지도 에코된다.** 다른 기기에서 보낸 경우가 있어서다. `ownedByViewer`로 구분하고,
   **낙관적으로 그린 말풍선과 `Message.id` 기준으로 dedupe**해야 한다. 안 하면 내 메시지가 두 번 뜬다.
3. **구독 실패는 `errors[]`에 온다** (`userErrors`가 아니다). 아래 6번 참고.

## 4. `unreadCount`가 실제로 동작한다 + 읽음 처리 뮤테이션이 생겼다

예전 설계에서는 "항상 0"이었으나 **뒤집혔다.** 이제 진짜 센다.

```graphql
Conversation { unreadCount: Int! }   # 내가 안 읽은 수. 내가 보낸 건 세지 않는다

mutation {
  markConversationRead(input: { conversationId: ID!, upToMessageId: ID }): MarkConversationReadPayload!
}
type MarkConversationReadPayload { success: Boolean!, userErrors: [UserError!]!, unreadCount: Int! }
```

- **메시지를 조회하는 것만으로는 읽음이 되지 않는다.** 반드시 이 뮤테이션을 호출해야 줄어든다.
  (목록 미리보기나 알림에서 스쳐 본 것이 읽음 처리되면 사용자가 메시지를 놓치기 때문이다.)
- **⚠️ 언제 호출할지는 클라이언트가 정한다** — 서버가 의도적으로 위임했다.
  채팅방 진입 / 스크롤 하단 도달 / 포그라운드 복귀 중 어디서 부를지 **결정해서 문서에 적어라.**
- `upToMessageId`는 **생략이 "모두 읽음"이다.** 마지막 메시지 id를 몰라도 부를 수 있으므로
  대부분 생략이 정답이다.
- **재조회가 필요 없다** — 페이로드가 처리 후 잔여 수를 돌려준다.
- **멱등이다.** 읽음 지점은 뒤로 가지 않으므로 중복 호출·재시도가 안전하다.

---

## 5. 페이징 기본값 — **메시지는 30이다**

| 쿼리 | 기본 | 상한 | 정렬 |
|---|---|---|---|
| `feed(first, after)` | 20 | 50 | 최신순 고정 (정렬 인자 **없음**) |
| `conversations(first, after)` | 20 | 50 | 최근 활동순 |
| `conversationMessages(conversationId, first, after)` | **30** | 50 | **최신순(내림차순)** |

- 상한 초과는 **오류가 아니라 절삭**이다. 51을 보내면 성공하고 50개가 온다 — 이걸 "누락"으로 오판하지 마라.
- 메시지가 **내림차순**이므로 채팅 화면에서는 뒤집어 그리고 `after`로 과거 방향으로 이어읽는다.
- Connection에 `totalCount`는 **없다.** `hasNextPage`로만 판정한다.

## 6. 에러가 두 갈래로 나온다 — `userErrors`만 보면 조용히 실패한다

| 연산 | 권한 실패 위치 |
|---|---|
| `conversationMessages` 등 **조회** | 최상위 **`errors[]`** 의 `FORBIDDEN` |
| `typingIndicator`·`newMessage` **구독** | 구독 시작 시점의 최상위 **`errors[]`** |
| `sendMessage`·`notifyTyping` 등 **뮤테이션** | **`userErrors[]`** 의 `NOT_CONVERSATION_PARTICIPANT` / `CONVERSATION_NOT_FOUND` |

**⚠️ 공통 에러 핸들러가 `userErrors`만 검사하면 조회 실패가 빈 화면이 된다.**
**⚠️ 구독 재연결 로직은 `FORBIDDEN`에 대해 재시도하지 않아야 한다** — 권한 문제는 재시도로 풀리지 않고 상한까지 헛돈다.

실제로 나오는 에러 코드는 이게 전부다:
`SPOT_REQUIRED`, `SPOT_NOT_FOUND`, `PHOTO_REQUIRED`, `TOO_MANY_PHOTOS`, `PHOTO_NOT_CONFIRMED`,
`FEED_ITEM_NOT_FOUND`, `NOT_FEED_AUTHOR`, `BLOCKED`, `CONVERSATION_NOT_FOUND`,
`NOT_CONVERSATION_PARTICIPANT`, `ALREADY_REPORTED`, `FORBIDDEN`(errors[] 전용)

## 7. DM 신고는 `ReportTargetType`을 쓰지 않는다

`ReportTargetType`은 `POST`, `COMMENT`, `MEMBER`, `FEED_ITEM` **4개뿐이고 `MESSAGE`가 없다.**

- 피드 신고 → `reportContent(targetType: FEED_ITEM)`
- **DM 신고 → 별도 뮤테이션 `reportMessages(messageIds: [ID!]!, reason, detail)`**

`FEED_ITEM`이 실제로 추가되었으므로, `ReportTargetType`을 default 없이 전수 분기하는 코드가 있으면 지금 깨진다.

## 8. `Message.body`는 non-null이며 빈 문자열일 수 있다

```graphql
Message { body: String! }   # null 아님. 첨부만 보낸 메시지는 ""
```

빈 문자열을 "본문 없음"으로 해석해 말풍선을 접거나 숨기지 마라 — 첨부만 렌더한다.
도메인 모델에서 `body`를 nullable로 두면 서버 계약과 어긋난다.

---

## 9. WebSocket 연결 (구독용)

- **경로: `/graphql`** — HTTP GraphQL과 **같은 경로**이고 스킴만 `ws://` / `wss://`다.
  `/subscriptions` 같은 별도 경로가 **아니다.**
- **프로토콜: `graphql-transport-ws`** (graphql-ws 라이브러리). 구형 `subscriptions-transport-ws`가 아니다.
- **`connection_init`을 60초 안에** 보내야 한다. 안 보내면 서버가 닫는다.

**인증 — 두 방법 다 지원한다:**

```json
{ "type": "connection_init", "payload": { "Authorization": "Bearer <accessToken>" } }
```
키는 `Authorization` / `authorization` 둘 다 받는다. 또는 핸드셰이크 `Authorization` 헤더도 된다.
(브라우저 WebSocket API는 커스텀 헤더를 못 붙이므로 페이로드가 유일한 수단이다.)

- **⚠️ `Bearer ` 접두어가 필수다.** 접두어 없는 raw 토큰은 **토큰 없음으로 취급되어 연결이 거부된다.**
- 인증 실패 시 **연결 자체가 거부된다**(구독 시점이 아니라). 연결에 성공했는데 이벤트가 안 오는 상황을
  방지하려는 설계다.

**토큰 만료 (중요):**
- accessToken은 5분 만료지만 **서버는 만료로 기존 연결을 끊지 않는다**(끊으면 타이핑 표시가 주기적으로 사라진다).
- 만료는 **새 `subscribe`를 열 때부터** 적용된다.
- **토큰을 갱신했다면 새 구독을 열기 전에 연결을 다시 맺는 편이 안전하다** — 세션에 박힌 신원은
  `connection_init` 시점의 것이고 갱신 수단이 없다.

**인가는 2단계다:** 연결 시 인증 1회 + **구독마다 참여자 검사**. 연결이 살아 있다는 것이
특정 대화를 볼 자격의 증명은 아니다.

---

## 10. 전체 시그니처 (이대로 쓰면 된다)

### Query
```graphql
feed(first: Int = 20, after: String): FeedConnection!
feedItem(id: ID!): FeedItem                      # 삭제·차단이면 오류가 아니라 null
conversations(first: Int = 20, after: String): ConversationConnection!
conversationMessages(conversationId: ID!, first: Int = 30, after: String): MessageConnection!
```

### Mutation — 전부 `success: Boolean!` + `userErrors: [UserError!]!` 포함
```graphql
createFeedItem(input: { body: String!, spotId: ID!, photoKeys: [String!]! })  # body 1~500, 사진 1~5
    → feedItemId: ID
likeFeedItem(input: { feedItemId: ID! })     → likeCount: Int    # 멱등
unlikeFeedItem(input: { feedItemId: ID! })   → likeCount: Int    # 멱등, 음수 불가
startConversation(input: { feedItemId: ID! }) → conversationId: ID  # 기존 대화가 있으면 그것을 반환
sendMessage(input: { conversationId: ID!, body: String!, feedItemId: ID, photoKey: String })
    → messageId: ID
notifyTyping(input: { conversationId: ID! })
markConversationRead(input: { conversationId: ID!, upToMessageId: ID }) → unreadCount: Int!
reportMessages(input: { messageIds: [ID!]!, reason: ReportReason!, detail: String })
    # 1건 이상, 동일 대화, 자기 메시지 불가. reason이 OTHER면 detail 필수
```

### Subscription
```graphql
typingIndicator(conversationId: ID!): TypingEvent!   # 유실 허용. 재전송 없음
newMessage(conversationId: ID!): Message!            # 유실 불가 → 재연결 시 조회로 보충
```

### 타입
```graphql
FeedItem  { id: ID!  body: String!  spot: PostSpot!  photoKeys: [String!]!
            author: PostAuthor!  likeCount: Int!  likedByViewer: Boolean!
            ownedByViewer: Boolean!  createdAt: String! }

Conversation { id: ID!  counterpart: PostAuthor!  lastMessage: Message    # 빈 대화 가능 → null
               unreadCount: Int!  updatedAt: String! }

Message   { id: ID!  body: String!        # 빈 문자열 가능
            feedItem: FeedItem            # nullable — 삭제된 피드
            photoKey: String              # nullable
            sender: PostAuthor!  ownedByViewer: Boolean!  createdAt: String! }

TypingEvent { sender: PostAuthor!  emittedAt: String! }
PostAuthor  { id: ID!  nickname: String!  profileImageUrl: String }
PostSpot    { id: ID!  title: String!  thumbnailImageUrl: String!  category: TargetCategory }
PageInfo    { hasNextPage: Boolean!  hasPreviousPage: Boolean!  startCursor: String  endCursor: String }
UserError   { code: String!  message: String!  field: String }
```

모든 시각은 **ISO-8601 문자열**이다.

---

## 11. 작업 지시

1. `problem/SPEC-0017-client.md`를 위 내용으로 **갱신한다.** 기존 서술 중 맞는 것은 지우지 말고,
   빠진 4가지(1~4절)를 추가한다.
2. **미결 사항을 결정해서 문서에 적는다**: `markConversationRead`를 언제 호출할 것인가.
3. 구현 순서는 클라이언트가 판단하되, **`newMessage` 구독을 빠뜨리지 마라** — 이걸 놓치면
   메시지 수신이 폴링이나 수동 새로고침으로 남고, 서버가 WebSocket을 들여놓은 이유가 무산된다.

**추측해서 만들지 마라.** 위에 없는 필드나 뮤테이션이 필요해 보이면 그것은 서버에 없는 것이다.
서버 스키마 원본은 `ieuri-server/src/main/resources/schema/feed.graphqls`이다.
