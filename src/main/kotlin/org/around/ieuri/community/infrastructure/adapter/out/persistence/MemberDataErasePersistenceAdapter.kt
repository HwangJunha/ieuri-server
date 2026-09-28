package org.around.ieuri.community.infrastructure.adapter.out.persistence

import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.BlockJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostHideJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.PostLikeJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 좋아요·숨김·차단 물리 삭제(SPEC-0011 R17). 콘텐츠·DM은 [MemberContentSoftDeleteAdapter].
 * - 벌크 삭제는 영속성 컨텍스트를 우회한다(DB의 ON DELETE CASCADE는 동작한다).
 */
@Component
class MemberDataErasePersistenceAdapter(
    private val likeRepository: PostLikeJpaRepository,
    private val hideRepository: PostHideJpaRepository,
    private val blockRepository: BlockJpaRepository,
) : MemberDataErasePort {

    @Transactional
    override fun deleteLikesByMember(memberId: Long): Long = likeRepository.deleteByMemberId(memberId)

    @Transactional
    override fun deleteHidesByMember(memberId: Long): Long = hideRepository.deleteByMemberId(memberId)

    @Transactional
    override fun deleteBlocksInvolving(memberId: Long): Long = blockRepository.deleteByBlockerIdOrBlockedId(memberId, memberId)
}
