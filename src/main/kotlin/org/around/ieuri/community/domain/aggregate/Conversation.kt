package org.around.ieuri.community.domain.aggregate

import java.time.Instant

/**
 * 1:1 대화. 애그리게이트 루트.
 * - 참여자는 `memberLow < memberHigh`로 정렬된 쌍이다. (A,B)와 (B,A)는 같은 대화다(AC7-2).
 * - 메시지가 없는 빈 대화가 존재할 수 있다.
 */
class Conversation private constructor(
    val id: Long?,
    val memberLow: Long,
    val memberHigh: Long,
    lastMessageAt: Instant,
    val createdAt: Instant?,
    lastReadMessageIdLow: Long?,
    lastReadMessageIdHigh: Long?,
) {
    /** 정렬 키(최근 활동순). 메시지가 없으면 생성 시각이다. */
    var lastMessageAt: Instant = lastMessageAt
        private set

    /** 각 참여자가 마지막으로 읽은 메시지 id. null은 "아직 아무것도 읽지 않았다"이다. */
    var lastReadMessageIdLow: Long? = lastReadMessageIdLow
        private set

    var lastReadMessageIdHigh: Long? = lastReadMessageIdHigh
        private set

    /** 메시지가 오갈 때마다 목록의 맨 위로 올린다. */
    fun touch(at: Instant) {
        lastMessageAt = at
    }

    fun hasParticipant(memberId: Long): Boolean = memberId == memberLow || memberId == memberHigh

    /** [memberId]가 마지막으로 읽은 메시지 id. 아직 읽지 않았으면 null이다. */
    fun lastReadMessageIdOf(memberId: Long): Long? {
        require(hasParticipant(memberId)) { "대화의 참여자가 아닙니다." }
        return if (memberId == memberLow) lastReadMessageIdLow else lastReadMessageIdHigh
    }

    /** [memberId]의 읽음 지점을 [messageId]까지 올린다. 더 작은 id로는 되돌아가지 않는다. */
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

    /** [memberId]의 상대방. 참여자가 아닌 id면 예외 — 호출 전에 [hasParticipant]로 판정한다. */
    fun counterpartOf(memberId: Long): Long {
        require(hasParticipant(memberId)) { "대화의 참여자가 아닙니다." }
        return if (memberId == memberLow) memberHigh else memberLow
    }

    companion object {
        /**
         * 두 회원의 새 대화. 인자 순서와 무관하게 정렬된 쌍으로 만든다.
         * - 자기 자신과의 대화는 거부한다(AC7-4).
         */
        fun create(
            memberId: Long,
            counterpartId: Long,
            now: Instant,
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
        ): Conversation = Conversation(
            id = id,
            memberLow = memberLow,
            memberHigh = memberHigh,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
            lastReadMessageIdLow = lastReadMessageIdLow,
            lastReadMessageIdHigh = lastReadMessageIdHigh,
        )
    }
}
