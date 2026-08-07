package org.around.ieuri.auth.infrastructure.adapter.out.persistence

import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.port.out.persistence.MemberProfileRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.NicknameConflictException
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.repository.MemberProfileJpaRepository
import org.springframework.dao.DataIntegrityViolationException
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

    @Transactional(readOnly = true)
    override fun existsByNicknameExcludingMember(nickname: String, memberId: Long): Boolean =
        profileRepository.existsByNicknameAndMemberIdNot(nickname, memberId)

    /**
     * 유니크 제약 위반을 애플리케이션 어휘로 옮긴다 — 애플리케이션이 JPA/JDBC 예외 타입을
     * 알 이유가 없다. 경합으로 사전 확인을 함께 통과한 두 요청 중 하나가 여기로 온다.
     */
    @Transactional
    override fun updateNickname(memberId: Long, nickname: String) {
        val entity = profileRepository.findByMemberId(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        entity.nickname = nickname
        try {
            // 제약 위반을 이 메서드 안에서 잡으려면 flush를 앞당겨야 한다 —
            // 커밋 시점까지 미루면 예외가 이 try 밖에서 터진다.
            profileRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            throw NicknameConflictException("이미 사용 중인 닉네임입니다.")
        }
    }

    @Transactional
    override fun updateProfileImage(memberId: Long, objectKey: String?) {
        val entity = profileRepository.findByMemberId(memberId)
            ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        entity.profileImageUrl = objectKey
    }

    @Transactional(readOnly = true)
    override fun findProfileImageKey(memberId: Long): String? =
        profileRepository.findByMemberId(memberId)?.profileImageUrl
}
