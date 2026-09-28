package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberProfileJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 프로필 쓰기 포트 구현(SPEC-0011 R5·R6).
 *
 * 변경은 managed 엔티티를 로드해 필드를 바꾸고 **dirty-check로 UPDATE를 흘린다** —
 * detached 엔티티를 새로 만들어 save()하면 dirty-check가 무의미해진다.
 */
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
