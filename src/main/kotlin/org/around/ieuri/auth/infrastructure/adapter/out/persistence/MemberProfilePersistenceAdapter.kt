package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberProfileJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 프로필 쓰기 포트 구현(SPEC-0011 R5·R6). managed 엔티티를 고쳐 dirty-check로 UPDATE한다. */
@Component
class MemberProfilePersistenceAdapter(
    private val profileRepository: MemberProfileJpaRepository,
) : MemberProfileRepositoryPort {

    @Transactional
    override fun updateNickname(memberId: Long, nickname: String) {
        val entity = profileRepository.findByMemberId(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        entity.nickname = nickname
    }

    @Transactional
    override fun updateProfileImage(memberId: Long, objectKey: String?) {
        val entity = profileRepository.findByMemberId(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        entity.profileImageUrl = objectKey
    }

    @Transactional(readOnly = true)
    override fun findProfileImageKey(memberId: Long): String? = profileRepository.findByMemberId(memberId)?.profileImageUrl
}
