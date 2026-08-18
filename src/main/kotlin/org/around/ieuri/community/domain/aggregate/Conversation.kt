package org.around.ieuri.community.domain.aggregate

import org.around.ieuri.community.domain.vo.SpotQaOrigin
import java.time.Instant

/**
 * 1:1 대화. 애그리게이트 루트. 그룹 대화는 비범위다.
 *
 * **참여자를 정렬된 쌍으로 들고 있다** — `memberLow < memberHigh`가 불변식이며 (A,B)와
 * (B,A)가 같은 대화가 된다. 그래야 "이미 있는 대화"를 저장소의 유일 제약 하나로 판정할
 * 수 있고(AC7-2), 두 방향을 각각 조회하다 경합에서 중복 행이 생기는 일이 없다.
 *
 * 대화 시작과 첫 메시지 전송은 별개 연산이라 **빈 대화가 존재할 수 있다.**
 */
class Conversation private constructor(
    val id: Long?,
    val memberLow: Long,
    val memberHigh: Long,
    lastMessageAt: Instant,
    val createdAt: Instant?,
    lastReadMessageIdLow: Long?,
    lastReadMessageIdHigh: Long?,
    /**
     * 이 대화를 시작시킨 질문/답변의 스냅샷(SPEC-0026 R13). 일반 DM이면 null.
     *
     * **생성 시점에 고정되고 이후 바뀌지 않는다** — 기존 대화에 출처를 소급 적용하지
     * 않기 때문이며(AC-13.3), 그래서 val이다.
     */
    val spotQaOrigin: SpotQaOrigin?,
) {
    /** 정렬 키(최근 활동순). 메시지가 없으면 생성 시각이다. */
    var lastMessageAt: Instant = lastMessageAt
        private set

    /**
     * 각 참여자가 마지막으로 읽은 메시지 id. **null은 "아직 아무것도 읽지 않았다"이며 0이
     * 아니다** — 0으로 두면 "id 0까지 읽었다"와 구분되지 않는다.
     *
     * 시각이 아니라 id를 쓰는 이유는 경계를 하나로 정하기 위해서다. 같은 밀리초에 도착한
     * 메시지가 여럿이면 시각으로는 어디까지 읽은 것인지 정할 수 없다.
     */
    var lastReadMessageIdLow: Long? = lastReadMessageIdLow
        private set

    var lastReadMessageIdHigh: Long? = lastReadMessageIdHigh
        private set

    /** 메시지가 오갈 때마다 목록의 맨 위로 올린다. */
    fun touch(at: Instant) {
        lastMessageAt = at
    }

    fun hasParticipant(memberId: Long): Boolean = memberId == memberLow || memberId == memberHigh

    /**
     * 저장 직후 부여된 id를 얹은 사본. 나머지 상태는 그대로다.
     *
     * 저장한 엔티티를 되읽어 도메인으로 옮기는 대신 이것을 쓰는 이유는 출처 발췌 때문이다 —
     * 엔티티에는 암호문만 있어, 되읽으면 방금 손에 쥔 평문을 다시 복호화하게 된다.
     *
     * **[createdAt]은 비어 있다.** DB가 채우는 값이라 새로 만든 직후에는 알 수 없다 —
     * 이미 있던 대화를 돌려받는 경로와 이 점이 다르므로, 시각이 필요하면 다시 읽어야 한다.
     */
    fun withId(assignedId: Long): Conversation = Conversation(
        id = assignedId,
        memberLow = memberLow,
        memberHigh = memberHigh,
        lastMessageAt = lastMessageAt,
        createdAt = createdAt,
        lastReadMessageIdLow = lastReadMessageIdLow,
        lastReadMessageIdHigh = lastReadMessageIdHigh,
        spotQaOrigin = spotQaOrigin,
    )

    /** [memberId]가 마지막으로 읽은 메시지 id. 아직 읽지 않았으면 null이다. */
    fun lastReadMessageIdOf(memberId: Long): Long? {
        require(hasParticipant(memberId)) { "대화의 참여자가 아닙니다." }
        return if (memberId == memberLow) lastReadMessageIdLow else lastReadMessageIdHigh
    }

    /**
     * [memberId]의 읽음 지점을 [messageId]까지 올린다.
     *
     * **뒤로 가지 않는다.** 클라이언트가 과거 메시지를 다시 열거나 요청이 순서를 바꿔
     * 도착해도 이미 읽은 것이 안 읽음으로 되돌아가면 안 된다 — 사용자에게는 뱃지가
     * 되살아나는 것으로 보인다.
     */
    fun markRead(memberId: Long, messageId: Long) {
        require(hasParticipant(memberId)) { "대화의 참여자가 아닙니다." }
        val current = lastReadMessageIdOf(memberId)
        if (current != null && current >= messageId) return

        if (memberId == memberLow) {
            lastReadMessageIdLow = messageId
        } else {
            lastReadMessageIdHigh = messageId
        }
    }

    /**
     * [memberId]의 상대방. **참여자가 아닌 id로 부르면 안 된다** — 호출 전에
     * [hasParticipant]로 판정한다.
     */
    fun counterpartOf(memberId: Long): Long {
        require(hasParticipant(memberId)) { "대화의 참여자가 아닙니다." }
        return if (memberId == memberLow) memberHigh else memberLow
    }

    companion object {
        /**
         * 두 회원의 새 대화. 인자 순서와 무관하게 같은 쌍이면 같은 모양이 된다 —
         * 여기서 정렬하는 것이 유일 제약이 성립하는 근거다.
         *
         * 자기 자신과의 대화는 만들지 않는다(AC7-4).
         */
        fun create(
            memberId: Long,
            counterpartId: Long,
            now: Instant,
            spotQaOrigin: SpotQaOrigin? = null,
        ): Conversation {
            require(memberId > 0 && counterpartId > 0) { "회원 id는 양수여야 합니다." }
            require(memberId != counterpartId) { "자기 자신과는 대화할 수 없습니다." }
            return Conversation(
                id = null,
                memberLow = minOf(memberId, counterpartId),
                memberHigh = maxOf(memberId, counterpartId),
                lastMessageAt = now,
                createdAt = null,
                lastReadMessageIdLow = null,
                lastReadMessageIdHigh = null,
                spotQaOrigin = spotQaOrigin,
            )
        }

        /** 저장소에서 복원. 불변식은 생성 시점에 이미 통과했으므로 다시 검사하지 않는다. */
        fun restore(
            id: Long,
            memberLow: Long,
            memberHigh: Long,
            lastMessageAt: Instant,
            createdAt: Instant,
            lastReadMessageIdLow: Long? = null,
            lastReadMessageIdHigh: Long? = null,
            spotQaOrigin: SpotQaOrigin? = null,
        ): Conversation = Conversation(
            id = id,
            memberLow = memberLow,
            memberHigh = memberHigh,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
            lastReadMessageIdLow = lastReadMessageIdLow,
            lastReadMessageIdHigh = lastReadMessageIdHigh,
            spotQaOrigin = spotQaOrigin,
        )
    }
}
