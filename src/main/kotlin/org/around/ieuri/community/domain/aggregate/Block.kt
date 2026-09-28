package org.around.ieuri.community.domain.aggregate

import java.time.Instant

/**
 * 사용자 차단. 애그리게이트 루트.
 * - 단방향 한 건으로 저장하고, 조회 시 "내가 차단했거나 나를 차단한" 양쪽을 모두 제외한다.
 */
class Block private constructor(
    val id: Long?,
    val blockerId: Long,
    val blockedId: Long,
    val createdAt: Instant?,
) {
    companion object {
        /** 자기 자신은 차단할 수 없다. 중복 차단은 오류가 아니라 멱등 no-op이며 저장소가 판정한다. */
        fun create(blockerId: Long, blockedId: Long): Block {
            require(blockerId > 0) { "blockerId는 양수여야 합니다." }
            require(blockedId > 0) { "blockedId는 양수여야 합니다." }
            require(blockerId != blockedId) { "자기 자신은 차단할 수 없습니다." }
            return Block(id = null, blockerId = blockerId, blockedId = blockedId, createdAt = null)
        }

        fun restore(id: Long, blockerId: Long, blockedId: Long, createdAt: Instant): Block = Block(id = id, blockerId = blockerId, blockedId = blockedId, createdAt = createdAt)
    }
}
