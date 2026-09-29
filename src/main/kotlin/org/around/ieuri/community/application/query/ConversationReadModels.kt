package org.around.ieuri.community.application.query

import java.time.Instant

// DM 읽기(CQRS) read model(SPEC-0017). 리졸버가 GraphQL 응답 타입으로 매핑한다.

/** 대화 목록 항목. 빈 대화면 [lastMessage]가 null이다. */
data class ConversationView(
    val id: Long,
    val counterpartId: Long,
    val lastMessage: MessageView?,
    val unreadCount: Int,
    val updatedAt: Instant,
)

/**
 * 메시지에 붙는 인용 스냅샷(SPEC-0026 R14).
 * - 살아있는 참조가 아니다 — [questionId]·[answerId]로 다시 조회하면 없을 수 있다.
 */
data class ConversationSpotQaOriginView(
    val catalogId: Long,
    val spotTitle: String,
    val questionId: Long,
    val questionBodySnapshot: String,
    val answerId: Long,
    val answerBodySnapshot: String,
)

/**
 * 메시지 한 건. 본문은 조회 어댑터가 복호화한 평문이다.
 * - [body]는 사진만 있는 메시지에서 빈 문자열이다. 본문도 사진도 없는 과거 메시지도 있다(SPEC-0021).
 */
data class MessageView(
    val id: Long,
    val body: String,
    val senderId: Long,
    val ownedByViewer: Boolean,
    /** 이 값을 만들 때 기준이 된 조회자. 구독 경로에는 SecurityContext가 없어 필드 리졸버가 여기서 읽는다. */
    val viewerId: Long,
    val createdAt: Instant,
    /** 첨부 사진의 오브젝트 키. 클라이언트가 CDN 베이스와 조합해 쓴다. */
    val photoKey: String? = null,
    /** 인용한 장소 질문/답변의 스냅샷. 인용이 없으면 null. 발췌는 복호화된 평문이다(POL-050). */
    val spotQaOrigin: ConversationSpotQaOriginView? = null,
)

/** 타이핑 이벤트(R10). 영속화되지 않는 전달 시점의 스냅샷이다. */
data class TypingEventView(
    val conversationId: Long,
    val senderId: Long,
    val emittedAt: Instant,
)
