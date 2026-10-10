package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberProfileUseCase
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.Nickname
import org.around.ieuri.auth.domain.member.ProfileImageObjectKey
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 프로필의 DB 단계(SPEC-0011 R5·R6). 프로필 행이 없는 회원은 없는 회원과 같이 답한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberProfileService(
    private val memberRepository: MemberRepository,
) : MemberProfileUseCase {

    /** 형식만 보고 중복은 보지 않는다(SPEC-0032 R31). 형식 오류가 회원 부재보다 먼저다. */
    override fun changeNickname(command: ChangeNicknameCommand): String {
        val nickname = Nickname(command.nickname)
        load(command.memberId)
        if (!memberRepository.saveNickname(command.memberId, nickname)) throw notFound()
        return nickname.value
    }

    override fun attachProfileImage(memberId: Long, pendingObjectKey: String): String? {
        val member = load(memberId)
        val previousKey = memberRepository.findProfileImageKey(memberId)
        val confirmed = member.attachProfileImage(ProfileImageObjectKey.pendingOf(pendingObjectKey, memberId))
        if (!memberRepository.saveProfileImage(memberId, confirmed.value)) throw notFound()
        return previousKey
    }

    override fun detachProfileImage(memberId: Long): String? {
        val previousKey = memberRepository.findProfileImageKey(memberId)
        if (!memberRepository.saveProfileImage(memberId, objectKey = null)) throw notFound()
        return previousKey
    }

    private fun load(memberId: Long): Member = memberRepository.findById(memberId) ?: throw notFound()

    private fun notFound() = MemberNotFoundException("회원을 찾을 수 없습니다.")
}
