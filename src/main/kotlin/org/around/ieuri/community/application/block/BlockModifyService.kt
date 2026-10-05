package org.around.ieuri.community.application.block

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.block.provided.BlockCommandUseCase
import org.around.ieuri.community.application.block.required.BlockRepository
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.withdrawal.MemberContentEraser
import org.around.ieuri.community.domain.block.Block
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 차단·해제. 회원 존재 확인(auth)은 첫 쿼리 전에 한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class BlockModifyService(
    private val blockRepository: BlockRepository,
    private val memberLookupPort: MemberLookupPort,
) : BlockCommandUseCase,
    MemberContentEraser {

    override fun block(targetMemberId: Long, memberId: Long) {
        requirePositive(targetMemberId)
        // 자기 차단은 존재 확인보다 먼저 거부한다.
        val block = Block(blockerId = memberId, blockedId = targetMemberId)
        requireMemberExists(targetMemberId)

        blockRepository.add(block)
    }

    override fun unblock(targetMemberId: Long, memberId: Long) {
        requirePositive(targetMemberId)
        requireMemberExists(targetMemberId)

        blockRepository.remove(blockerId = memberId, blockedId = targetMemberId)
    }

    private fun requirePositive(targetMemberId: Long) = require(targetMemberId > 0) { "targetMemberId는 양수여야 합니다." }

    private fun requireMemberExists(memberId: Long) {
        if (memberLookupPort.findAuthor(memberId) == null) throw MemberNotFoundException()
    }

    /** 탈퇴 정리: 회원이 걸었거나 회원을 향한 차단 삭제. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberContent(memberId: Long, deletedAt: Instant): Int = blockRepository.removeInvolving(memberId)
}
