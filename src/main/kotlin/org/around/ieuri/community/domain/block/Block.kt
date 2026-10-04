package org.around.ieuri.community.domain.block

/**
 * 사용자 차단 한 건. 단방향으로 저장하고, 조회 시 "내가 차단했거나 나를 차단한" 양쪽을 모두 제외한다.
 * - 중복 차단은 오류가 아니라 멱등 no-op이며 저장소가 판정한다.
 */
data class Block(val blockerId: Long, val blockedId: Long) {
    init {
        require(blockerId > 0 && blockedId > 0) { "회원 id는 양수여야 합니다." }
        if (blockerId == blockedId) throw CannotBlockSelfException()
    }
}
