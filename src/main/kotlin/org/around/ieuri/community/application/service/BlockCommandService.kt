package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.command.BlockUserCommand
import org.around.ieuri.community.application.exception.CannotBlockSelfException
import org.around.ieuri.community.application.exception.MemberNotFoundException
import org.around.ieuri.community.application.port.`in`.command.BlockCommandUseCase
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.persistence.BlockRepositoryPort
import org.around.ieuri.community.domain.aggregate.Block
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 차단·해제. 중복 차단과 미차단 해제는 오류가 아니라 멱등 no-op이다.
 * - 저장은 단방향 한 건이며 양방향 해석은 조회 시점에 한다.
 */
@Service
class BlockCommandService(
    private val blockRepositoryPort: BlockRepositoryPort,
    private val memberLookupPort: MemberLookupPort,
) : BlockCommandUseCase {

    @Transactional
    override fun block(command: BlockUserCommand, memberId: Long) {
        // 자기 차단은 존재 확인보다 먼저 거부한다.
        if (command.targetMemberId == memberId) throw CannotBlockSelfException()
        if (memberLookupPort.findAuthor(command.targetMemberId) == null) throw MemberNotFoundException()

        blockRepositoryPort.save(
            Block.create(blockerId = memberId, blockedId = command.targetMemberId),
        )
    }

    @Transactional
    override fun unblock(command: BlockUserCommand, memberId: Long) {
        if (memberLookupPort.findAuthor(command.targetMemberId) == null) throw MemberNotFoundException()
        blockRepositoryPort.delete(blockerId = memberId, blockedId = command.targetMemberId)
    }
}
