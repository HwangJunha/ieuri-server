package org.around.ieuri.community.domain.aggregate

import java.time.Instant

/**
 * 사용자 차단. 애그리게이트 루트.
 *
 * **단방향 레코드로 저장하고 조회 시점에 양방향으로 해석한다.** A가 B를 차단하면 한 건만
 * 쓰고, 피드 필터가 "내가 차단했거나 나를 차단한" 양쪽을 모두 제외한다.
 *
 * 양방향 두 건을 쓰지 않는 이유는 해제 시 정합성이다 — 두 건이면 A가 해제할 때 B가 건
 * 차단까지 지워질 위험이 있고, "누가 먼저 차단했는가"도 잃는다.
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
