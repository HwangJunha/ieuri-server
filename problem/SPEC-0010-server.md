> spec: SPEC-0010 · source: `specs/SPEC-0010/spec.md` (Obsidian) · status: draft

# SPEC-0010 서버 설계 — 커뮤니티 Spot Talk

요구사항과 인수 조건은 스펙이 정본이다. 이 문서는 요구사항을 다시 쓰지 않고 번호로 인용한다.

## 1. 도메인 배치

새 바운디드 컨텍스트 `community` 를 연다. 기존 `tourism` 과 `auth` 어디에도 넣지 않는다 —
글·댓글·차단은 관광지 데이터의 생명주기와 무관하고, 회원 인증과도 무관하기 때문이다.

`community` 는 `tourism` 의 spot 을 참조하지만 소유하지 않는다. 참조는 spotId 값으로만 이뤄지며
`tourism` 의 도메인 모델을 직접 끌어다 쓰지 않는다. 반대 방향 참조는 없다 — `tourism` 은
커뮤니티의 존재를 모른다.

`auth` 의 회원도 같은 방식으로 id 참조만 한다.

## 2. 애그리게이트

| 애그리게이트 | 루트 | 불변식 |
|---|---|---|
| Post | Post | 본문 1~500자, 주제 태그 정확히 하나, 사진 키 최대 5개, spotId·authorId 필수 (R1~R4) |
| Comment | Comment | 본문 1~500자, 부모 댓글 없음, postId·authorId 필수 (R13) |
| PostLike | PostLike | (postId, memberId) 유일 (R12) |
| Block | Block | (blockerId, blockedId) 유일, 자기 자신 불가 (R15) |
| Report | Report | (reporterId, targetType, targetId) 유일 (R16) |
| DiscoveryTile | DiscoveryTile | title 비어 있지 않음 (R10) |

Post 와 Comment 를 하나의 애그리게이트로 묶지 않는다. 댓글 수가 늘어도 글을 통째로 적재할
이유가 없고, 댓글 작성이 글의 버전을 건드릴 필요도 없다. 대신 `commentCount` 는 Post 가
들고 있는 파생값으로 두고 댓글 생성·삭제 시 갱신한다.

`likeCount` 도 같은 이유로 Post 의 파생값이다. 매 조회마다 세지 않는다.

### 차단의 양방향성 (R15)

Block 은 단방향 레코드로 저장하고, **조회 시점에 양방향으로 해석한다.** A가 B를 차단하면
`Block(blockerId=A, blockedId=B)` 한 건만 쓰고, 피드 필터는 "내가 차단했거나 나를 차단한"
양쪽을 모두 제외한다.

양방향 레코드 두 건을 쓰지 않는 이유는 해제 시 정합성 때문이다. 두 건을 쓰면 A가 해제할 때
B가 건 차단까지 지워질 위험이 있고, "누가 먼저 차단했는가"라는 정보도 잃는다.

## 3. 애플리케이션 계층

CQRS 로 나눈다. 커맨드 핸들러가 단위 테스트의 주된 대상이다 (R1~R5, R12, R15, R16, R18, R19).

### 커맨드

| 커맨드 | 요구사항 | 주요 실패 |
|---|---|---|
| `CreatePostCommand` | R1~R4, R19 | `POST_BODY_REQUIRED`, `POST_BODY_LENGTH_INVALID`, `SPOT_NOT_FOUND`, `TOPIC_REQUIRED`, `TOO_MANY_PHOTOS`, `PHOTO_NOT_UPLOADED` |
| `DeletePostCommand` | R5 | `NOT_POST_OWNER`, `POST_NOT_FOUND` |
| `CreateCommentCommand` | R13 | `POST_NOT_FOUND`, `COMMENT_BODY_REQUIRED`, `COMMENT_BODY_LENGTH_INVALID` |
| `DeleteCommentCommand` | R5 | `NOT_COMMENT_OWNER`, `COMMENT_NOT_FOUND` |
| `LikePostCommand` / `UnlikePostCommand` | R12 | `POST_NOT_FOUND` |
| `IssuePostImageUploadUrlCommand` | R18 | — |
| `BlockUserCommand` / `UnblockUserCommand` | R15 | `CANNOT_BLOCK_SELF`, `MEMBER_NOT_FOUND` |
| `ReportContentCommand` | R16, R20 | `ALREADY_REPORTED`, `REPORT_DETAIL_REQUIRED`, `*_NOT_FOUND` |

### 쿼리

| 쿼리 | 요구사항 |
|---|---|
| `SpotPostsQuery` | R6, R8, R14, R20 |
| `NearbyPostsQuery` | R7, R8, R14, R20 |
| `PostByIdQuery` | R21. 피드와 동일한 필터를 거친다 |
| `PostCommentsQuery` | R13, R14, R20 |
| `BlockedUsersQuery` | R15 |
| `DiscoveryTilesQuery` | R10 |

### 포트

| 포트 | 역할 | 단위 테스트에서 |
|---|---|---|
| `PostRepositoryPort` | 글 적재·조회 | 스텁 |
| `CommentRepositoryPort` | 댓글 적재·조회 | 스텁 |
| `PostLikeRepositoryPort` | 좋아요 유일성 | 스텁 |
| `BlockRepositoryPort` | 차단 관계 조회 | 스텁 |
| `ReportRepositoryPort` | 신고 유일성·집계 | 스텁 |
| `SpotLookupPort` | spotId 유효성과 피드 카드용 spot 요약 조회. `tourism` 이 구현한다 | 스텁 |
| `ObjectStoragePort` | presigned URL 발급, 오브젝트 존재 확인 (R18, R19) | 모의 |
| `ClockPort` | 만료 시각 계산 (R18-3) | 고정 시계 |

`ClockPort` 를 두는 이유는 R18-3 인수 조건 때문이다. "발급 시각 + 5분"을 검증하려면 시계가
주입 가능해야 한다. `Instant.now()` 를 직접 부르면 그 조건은 단위 테스트로 고정할 수 없다.

`SpotLookupPort` 는 `community` 가 선언하고 `tourism` 이 구현한다. 의존 방향을 이렇게 두면
`tourism` 은 커뮤니티를 계속 모른 채로 있을 수 있다.

## 4. 차단·숨김 필터링 (R14, R20, R21)

세 피드 쿼리와 **단건 조회까지** 네 경로가 모두 같은 필터를 거친다. 이것이 이 스펙에서 가장 흔한
구현 실수 지점이다 — 한 곳만 빠뜨려도 차단이 무력화된다. 특히 단건 조회는 딥링크의 진입점이라
여기가 뚫리면 피드 필터가 아무리 정확해도 소용이 없다 (R21).

필터를 각 쿼리 핸들러에 흩어 두지 말고 한 곳에 모아 네 경로가 같은 구현을 공유하게 한다.
경로마다 조건을 다시 쓰면 나중에 하나만 고쳐지는 사고가 난다.

필터는 두 단계다.

1. **차단 제외** — 호출자와 차단 관계(양방향)에 있는 작성자의 글·댓글을 제외한다.
   미인증 호출자는 차단 관계가 없으므로 이 단계를 건너뛴다 (R14-5).
2. **숨김 제외** — `hidden = true` 인 글·댓글을 제외하되, **작성자 본인에게는 남긴다** (R20-3, R20-4).

두 단계를 쿼리 조건으로 밀어 넣을지 조회 후 걸러낼지는 구현 판단이지만, 페이지네이션과
상호작용한다는 점만 유의한다. 조회 후 걸러내면 `first: 20` 을 요청했는데 15건만 남는 일이
생긴다. 커서 기반이므로 치명적이지는 않으나, 필터를 조건으로 밀어 넣는 편이 자연스럽다.

`hidden` 은 신고 시점에 갱신되는 저장값으로 둔다. 조회할 때마다 신고 수를 세지 않는다.
`ReportContentCommand` 가 신고를 기록한 뒤 서로 다른 신고자 수가 5에 도달했는지 확인하고
도달했으면 대상의 `hidden` 을 올린다 (R20-2, R20-5).

**대상 종류에 따라 갈린다** (R22). `POST` 와 `COMMENT` 만 임계값 도달 시 `hidden` 이 올라간다.
`MEMBER` 신고는 기록만 하고 어떤 자동 조치도 하지 않는다 — 신고 몇 건으로 한 사람의 글이 일괄로
사라지면 조직적 신고가 곧 침묵 수단이 된다. 검토 도구는 별도 스펙이며, 그때 이 기록이 입력이 된다.

`commentCount` 는 숨겨진 댓글을 제외한다 (R23). 댓글이 숨김으로 전환되는 시점에 글의
`commentCount` 를 함께 내린다. 모든 호출자에게 같은 값이어야 하며, 자기 댓글이 숨겨진 사용자에게만
다른 숫자를 보여주지 않는다 — 목록은 본인에게 보이더라도 숫자는 동일하다 (R23-4).

## 5. 사진 업로드 (R3, R18, R19)

이 경로는 **POL-021(REST 미설계)의 명시적 예외**다. 서버-클라이언트 통신은 GraphQL만 쓴다는
원칙에서 클라이언트가 R2로 직접 PUT하는 것만 예외로 인정받았다 (사용자 승인 2026-08-05,
스펙 §7의 신규 정책 후보). 이 예외를 근거로 다른 REST 경로를 열지 않는다.

서버는 이미지 바이트를 절대 중계하지 않는다. 흐름은 세 단계다.

1. 클라이언트가 `createPostImageUploadUrl` 로 키 하나당 URL 하나를 발급받는다. 발급 시
   `{memberId}/{uuid}` 형태로 키를 서버가 정하고, 클라이언트가 키를 지정할 수 없게 한다.
   키에 memberId 를 넣는 것이 R19-4(타인이 발급받은 키 거부)의 근거가 된다.
2. 클라이언트가 R2 에 직접 PUT 한다. 서버는 관여하지 않는다.
3. `createPost` 가 각 키에 대해 `ObjectStoragePort.exists(key)` 를 확인하고, 하나라도 없으면
   글 전체를 생성하지 않는다 (R19-3). 부분 생성은 없다.

**두 검사는 계층이 다르다** (R19, R24). 헷갈리면 단위 테스트가 무엇을 스텁해야 할지 모호해진다.

| 검사 | 계층 | 수단 | 요구사항 |
|---|---|---|---|
| 키 소유권 — 이 키가 호출자에게 발급된 것인가 | 도메인 | 키 문자열의 memberId 구간과 호출자 비교. 포트 호출 없음 | R24 |
| 업로드 여부 — 그 키에 실제 객체가 있는가 | 애플리케이션 | `ObjectStoragePort.exists(key)` | R19 |

소유권을 먼저 본다. 남의 키인지 확인하는 데 저장소를 왕복할 이유가 없고, 저장소를 먼저 물으면
타인의 키가 존재하는지를 응답 시간으로 알아낼 수 있다.

둘 다 실패 코드는 `PHOTO_NOT_UPLOADED` 하나로 통일한다. 소유권 실패와 부재를 구분해 알려 주면
남의 오브젝트 키가 존재하는지 캐내는 수단이 된다.

**고아 오브젝트**는 이 구조에서 필연적으로 생긴다 — 업로드했지만 글을 등록하지 않은 경우다.
이 스펙에서는 정리하지 않는다. R2 의 lifecycle rule 로 처리할 사안이며, 애플리케이션 로직으로
풀 문제가 아니다. 필요해지면 별도 이슈로 연다.

## 6. 선행 조건 — 저장소 소유자가 처리해야 함

코드 작업 전에 아래가 갖춰져야 한다. 하네스가 만들 수 있는 것이 아니다.

| 항목 | 비고 |
|---|---|
| R2 버킷 생성 | 이름은 환경별로 분리 (dev/prod) |
| API 토큰 발급 | Access Key / Secret. presigned 서명에 필요 |
| 공개 읽기 정책 결정 | 이미지 조회를 공개 URL 로 할지 조회도 presigned 로 할지. 공개 쪽이 훨씬 싸고 단순하다 |
| 커스텀 도메인 연결 | `r2.dev` 기본 도메인은 프로덕션 비권장. CDN 캐싱에도 필요 |
| CORS 설정 | 클라이언트가 앱에서 직접 PUT 하므로 없으면 업로드가 전부 막힌다 |
| 환경변수 등록 | 계정 ID·버킷명·엔드포인트·키. 코드에 하드코딩 금지 |

공개 읽기로 결정되면 `Post.photoKeys` 를 클라이언트가 공개 베이스 URL 과 조합해 쓴다. 조회도
presigned 로 가면 계약에 조회용 URL 필드가 추가되어야 하므로 스펙 개정 사유가 된다 —
그 경우 `[AMEND]` 로 보고할 것.

## 7. 다국어

주제 태그는 enum 이므로 서버가 표시 문자열을 내려주지 않는다. 클라이언트가 매핑한다 (POL-023).

탐색 타일 제목은 다르다. 운영자가 시즌마다 쓰는 자유 문구라 매핑할 enum 이 없고, 따라서 서버가
문자열로 내려준다.

타일은 **언어별로 독립된 레코드**로 적재한다 (스펙 §8에서 확정). 타일 하나에 4개 언어 컬럼을 두는
구조가 아니다. 조회 시 `Accept-Language` 로 해당 언어의 레코드만 고른다.

컬럼 방식을 택하지 않은 이유는 그것이 "모든 타일은 모든 언어에 존재한다"를 구조로 강제하기
때문이다. 벚꽃 시즌 타일을 한국어·일본어에만 띄우고 영어권에는 다른 타일을 띄우는 운영이
불가능해진다. 레코드를 나누면 언어마다 타일 구성과 개수가 달라도 된다.

요청 언어의 타일이 하나도 없으면 빈 목록을 반환한다. 다른 언어의 타일로 대체하지 않는다 —
영어권 사용자에게 한국어 타일이 뜨는 편이 아무것도 안 뜨는 것보다 나쁘다.

## 8. 이벤트

이 스펙에서 도메인 이벤트를 발행하지 않는다. 알림이 비범위이고, 신고 자동 숨김도 같은 커맨드
안에서 동기적으로 처리하면 충분하기 때문이다. Kafka 도입 후 알림이 들어올 때 재검토할 사안이다.

## 9. 테스트

애플리케이션·도메인 계층 단위 테스트만. 어댑터와 인프라는 이 플랫폼의 스펙 범위 밖이다.

| 단위 | 검증 대상 |
|---|---|
| `Post` 도메인 모델 | R1~R4 불변식. 본문 길이 경계(1자·500자·501자·공백), 사진 5개·6개 |
| `Comment` 도메인 모델 | R13. 부모 참조 필드가 없다는 구조적 사실 포함 |
| `Block` 도메인 모델 | R15. 자기 차단 거부 |
| `CreatePostCommandHandler` | R1~R4, R19, R24. `SpotLookupPort`·`ObjectStoragePort` 스텁으로 spot 부재·키 미업로드. 타인 키·위조 키는 포트 없이 도메인 판정으로 검증한다 |
| `DeletePostCommandHandler` / `DeleteCommentCommandHandler` | R5. 소유자·비소유자 |
| `LikePostCommandHandler` / `UnlikePostCommandHandler` | R12. 양방향 멱등 |
| `BlockUserCommandHandler` | R15. 중복 차단, 자기 차단 |
| `ReportContentCommandHandler` | R16, R20, R22, R23. 중복 신고, 5명 도달 시 hidden 전환, 같은 사람 5번은 미도달, MEMBER 대상은 임계값 도달해도 무조치, 댓글 숨김 시 commentCount 감소 |
| `IssuePostImageUploadUrlCommandHandler` | R18, R25. 고정 시계로 만료 5분, 연속 발급 키 상이, 미지원 contentType 거부 |
| `SpotPostsQueryHandler` / `NearbyPostsQueryHandler` | R6~R8, R14, R20. 차단 양방향 제외, 숨김 제외와 작성자 예외, 반경 경계 |
| `PostByIdQueryHandler` | R21. 차단 작성자·숨김 글에 null, 작성자 본인에게는 반환, 미인증 조회 |
| `PostCommentsQueryHandler` | R13, R14 |

인증 실패(R17)는 리졸버 진입 이전의 인증 계층이 처리하며, 그 경로는 SPEC-0005 에서 이미
구현·검증되었다. 이 스펙에서 다시 테스트하지 않는다.

커서 페이지네이션의 실제 실행 계획은 데이터가 쌓인 뒤에야 판단할 수 있다 (서버 이슈 #43 이
좋아요 목록에 대해 같은 지적을 하고 있다). 단위 테스트로 잡을 수 있는 것은 커서 인코딩·디코딩과
`hasNextPage` 계산까지이며, 인덱스 효율은 여기서 검증하지 않는다.
