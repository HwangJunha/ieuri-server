package org.around.ieuri.community.domain.aggregate

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
) {
    /** 정렬 키(최근 활동순). 메시지가 없으면 생성 시각이다. */
    var lastMessageAt: Instant = lastMessageAt
        private set

    /** 메시지가 오갈 때마다 목록의 맨 위로 올린다. */
    fun touch(at: Instant) {
        lastMessageAt = at
    }

    fun hasParticipant(memberId: Long): Boolean = memberId == memberLow || memberId == memberHigh

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
        ): Conversation {
            require(memberId > 0 && counterpartId > 0) { "회원 id는 양수여야 합니다." }
            require(memberId != counterpartId) { "자기 자신과는 대화할 수 없습니다." }
            return Conversation(
                id = null,
                memberLow = minOf(memberId, counterpartId),
                memberHigh = maxOf(memberId, counterpartId),
                lastMessageAt = now,
                createdAt = null,
            )
        }

        /** 저장소에서 복원. 불변식은 생성 시점에 이미 통과했으므로 다시 검사하지 않는다. */
        fun restore(
            id: Long,
            memberLow: Long,
            memberHigh: Long,
            lastMessageAt: Instant,
            createdAt: Instant,
        ): Conversation = Conversation(
            id = id,
            memberLow = memberLow,
            memberHigh = memberHigh,
            lastMessageAt = lastMessageAt,
            createdAt = createdAt,
        )
    }
}
