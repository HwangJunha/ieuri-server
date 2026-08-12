package org.around.ieuri.community.application.query

import java.time.Instant

/**
 * 피드·DM 읽기(CQRS) 전용 read model(SPEC-0017). QueryDSL이 직접 프로젝션한 결과이며
 * 도메인/JPA 어느 쪽도 아니다. 리졸버가 이 DTO를 GraphQL 응답 타입으로 매핑한다.
 */

/**
 * 피드 한 건. **community_feed_item 한 행이 가진 것만 담는다.**
 *
 * 사진·spot·작성자·좋아요 여부는 각자 다른 곳에 있어 **여기 없다** — 클라이언트가 그 필드를
 * 고른 경우에만 DataLoader가 한 번에 읽는다(PostView와 같은 이유).
 *
 * [spotId]만 예외로 담는다 — 같은 행의 컬럼이라 추가 조회가 없고, spot 상세 조회의 입력이다.
 */
data class FeedItemView(
    val id: Long,
    val body: String,
    val spotId: Long,
    val authorId: Long,
    val likeCount: Int,
    val ownedByViewer: Boolean,
    val createdAt: Instant,
)

/**
 * 대화 목록 항목. [lastMessage]는 **빈 대화가 있을 수 있어 nullable이다** —
 * 대화 시작과 첫 전송이 별개 연산이기 때문이다.
 *
 * **시작된 피드를 담지 않는다** — 방은 하나인데 피드는 여러 개라 방 단위 맥락은 성립하지
 * 않는다. 인용은 [MessageView.feedItemId]로 메시지마다 붙는다.
 */
data class ConversationView(
    val id: Long,
    val counterpartId: Long,
    val lastMessage: MessageView?,
    val unreadCount: Int,
    val updatedAt: Instant,
)

/**
 * 메시지 한 건. 본문은 조회 어댑터가 복호화한 평문이다(R16).
 *
 * [body]는 **첨부만 있는 메시지에서 빈 문자열이다** — 사진이나 피드 인용만 보낼 수 있다.
 */
data class MessageView(
    val id: Long,
    val body: String,
    val senderId: Long,
    val ownedByViewer: Boolean,
    val createdAt: Instant,
    /** 인용한 피드. 피드가 지워졌으면 null이며 메시지는 남는다. */
    val feedItemId: Long? = null,
    /** 첨부 사진의 오브젝트 키. 클라이언트가 CDN 베이스와 조합해 쓴다. */
    val photoKey: String? = null,
)

/**
 * 타이핑 이벤트(R10). **영속화되지 않으므로 이후 쿼리로 다시 조회할 방법이 없다** —
 * 전달 시점의 스냅샷이다.
 */
data class TypingEventView(
    val conversationId: Long,
    val senderId: Long,
    val emittedAt: Instant,
)
