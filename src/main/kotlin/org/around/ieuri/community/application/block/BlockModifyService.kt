package org.around.ieuri.community.application.block

import org.around.ieuri.common.persistence.ExposedConfig
import org.around.ieuri.community.application.block.provided.BlockCommandUseCase
import org.around.ieuri.community.application.block.required.BlockRepository
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.domain.block.Block
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionOperations

/** 차단·해제. 회원 존재 확인(auth)은 트랜잭션 밖에서 한다. */
@Service
class BlockModifyService(
    private val blockRepository: BlockRepository,
    private val memberLookupPort: MemberLookupPort,
    @Qualifier(ExposedConfig.EXPOSED_TX_OPERATIONS) private val transaction: TransactionOperations,
) : BlockCommandUseCase {

    override fun block(targetMemberId: Long, memberId: Long) {
        requirePositive(targetMemberId)
        // 자기 차단은 존재 확인보다 먼저 거부한다.
        val block = Block(blockerId = memberId, blockedId = targetMemberId)
        requireMemberExists(targetMemberId)

        transaction.executeWithoutResult { blockRepository.add(block) }
    }

    override fun unblock(targetMemberId: Long, memberId: Long) {
        requirePositive(targetMemberId)
        requireMemberExists(targetMemberId)

        transaction.executeWithoutResult { blockRepository.remove(blockerId = memberId, blockedId = targetMemberId) }
    }

    private fun requirePositive(targetMemberId: Long) = require(targetMemberId > 0) { "targetMemberId는 양수여야 합니다." }

    private fun requireMemberExists(memberId: Long) {
        if (memberLookupPort.findAuthor(memberId) == null) throw MemberNotFoundException()
    }
}
