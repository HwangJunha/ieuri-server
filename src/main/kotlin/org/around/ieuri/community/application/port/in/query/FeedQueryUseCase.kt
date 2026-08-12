package org.around.ieuri.community.application.port.`in`.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.ConversationView
import org.around.ieuri.community.application.query.FeedItemView
import org.around.ieuri.community.application.query.MessageView
import org.around.ieuri.community.application.query.TypingEventView

/**
 * 피드 조회 유스케이스(SPEC-0017 R4). 모든 조회가 인증을 전제한다.
 */
interface FeedQueryUseCase {
    /** 피드 한 페이지. 최신순 고정이며 차단 관계 작성자의 항목은 질의 단계에서 빠진다. */
    fun feed(first: Int?, after: String?, memberId: Long): CursorPage<FeedItemView>

    /** 차단·삭제·숨김에 걸리면 오류가 아니라 null이다(기존 `post(id:)`와 같은 관례). */
    fun feedItem(feedItemId: Long, memberId: Long): FeedItemView?

    /**
     * 아래 둘은 피드의 **지연 필드**다 — 클라이언트가 고른 경우에만 DataLoader가 한 번에 읽는다.
     * 결과에 키가 없으면 그 피드에 값이 없다는 뜻이다.
     */
    fun feedItemPhotoKeys(feedItemIds: List<Long>): Map<Long, List<String>>

    fun likedFeedItemIds(feedItemIds: List<Long>, memberId: Long): Set<Long>
}

/**
 * 대화 조회 유스케이스(R8·R10).
 *
 * **참여자가 아니면 최상위 errors[]의 FORBIDDEN이다**(AC8-3·AC10-3) — 조회는 볼 자격 자체가
 * 없어 부분 성공이 성립하지 않는다. 뮤테이션의 userErrors와 갈리는 지점이다.
 */
interface ConversationQueryUseCase {
    fun conversations(first: Int?, after: String?, memberId: Long): CursorPage<ConversationView>

    fun conversationMessages(
        conversationId: Long,
        first: Int?,
        after: String?,
        memberId: Long,
    ): CursorPage<MessageView>

    /**
     * 타이핑 구독(R10). **구독 시작 시점에 참여자 검사를 한다** — 연결이 살아 있다는 것이
     * 특정 대화를 볼 자격의 증명은 아니다(POL-048). 참여자가 아니면 여기서 예외가 난다.
     *
     * 스트림 타입을 노출하지 않고 [ConversationSubscription]을 돌려주는 이유는 application이
     * 리액티브 라이브러리를 알지 않게 하기 위함이다 — 구독 표현은 인프라의 선택이다.
     */
    fun typingIndicator(conversationId: Long, memberId: Long): ConversationSubscription<TypingEventView>

    /**
     * 새 메시지 구독(R9). 참여자 검사는 타이핑과 같다.
     *
     * **이 구독은 "새 메시지가 있다"는 신호이지 전달 보장 수단이 아니다** — 진실은 DB에
     * 있고, 끊긴 동안의 메시지는 재연결 후 [conversationMessages]로 메꾼다. 메시지를 구독
     * 이벤트로만 전달하면 브로커가 끊긴 순간의 메시지가 영원히 사라진 것처럼 보인다.
     *
     * 이벤트마다 저장된 메시지를 읽어 내려주므로 **본문은 항상 DB의 값**이다.
     */
    fun newMessage(conversationId: Long, memberId: Long): ConversationSubscription<MessageView>
}

/**
 * 구독 한 건. [onEvent]로 넘긴 콜백이 이벤트마다 불리고, [cancel]로 끊는다.
 *
 * 타이핑은 **유실 허용이 계약이고**(AC14-2), 새 메시지는 유실되면 안 되므로 재연결 시
 * 조회로 메꾼다 — 어느 쪽이든 이 인터페이스는 "이 파드에 붙은 구독자 하나"만 표현한다.
 */
interface ConversationSubscription<T> {
    fun onEvent(listener: (T) -> Unit)

    fun cancel()
}
