package org.around.ieuri.community.application.query

import java.time.Instant

/**
 * DM 읽기(CQRS) 전용 read model(SPEC-0017). QueryDSL이 직접 프로젝션한 결과이며
 * 도메인/JPA 어느 쪽도 아니다. 리졸버가 이 DTO를 GraphQL 응답 타입으로 매핑한다.
 */

/**
 * 대화 목록 항목. [lastMessage]는 **빈 대화가 있을 수 있어 nullable이다** —
 * 대화 시작과 첫 전송이 별개 연산이기 때문이다.
 */
data class ConversationView(
    val id: Long,
    val counterpartId: Long,
    val lastMessage: MessageView?,
    val unreadCount: Int,
    val updatedAt: Instant,
)

/**
 * 인용 스냅샷(SPEC-0026 R14). **살아있는 참조가 아니다** — [questionId]·[answerId]로 다시
 * 조회하면 없을 수 있으며, 그래도 이 값들은 남는다.
 *
 * **메시지에 붙는다** — 한 대화에서 여러 질문을 인용할 수 있기 때문이다.
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
 * 메시지 한 건. 본문은 조회 어댑터가 복호화한 평문이다(R16).
 *
 * [body]는 **사진만 있는 메시지에서 빈 문자열이다.** 본문도 사진도 없는 메시지도 존재한다 —
 * 피드 인용만 있던 과거 메시지이며 삭제하지 않고 남긴다(SPEC-0021 R4).
 */
data class MessageView(
    val id: Long,
    val body: String,
    val senderId: Long,
    val ownedByViewer: Boolean,
    /**
     * 이 값을 만들 때 기준이 된 조회자. **구독 경로에서 필수다** — 이벤트마다 도는 실행에는
     * SecurityContext(ThreadLocal)가 없어서, 필드 리졸버가 조회자를 여기서 받아야 한다.
     */
    val viewerId: Long,
    val createdAt: Instant,
    /** 첨부 사진의 오브젝트 키. 클라이언트가 CDN 베이스와 조합해 쓴다. */
    val photoKey: String? = null,
    /**
     * 이 메시지가 인용한 장소 질문/답변의 스냅샷(SPEC-0026 R14). 인용이 없으면 null.
     *
     * 발췌는 조회 어댑터가 복호화한 평문이다 — 대화의 일부라 본문과 같이 암호화되어
     * 저장된다(POL-050).
     */
    val spotQaOrigin: ConversationSpotQaOriginView? = null,
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
