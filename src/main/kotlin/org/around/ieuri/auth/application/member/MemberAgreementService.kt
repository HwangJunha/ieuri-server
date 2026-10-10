package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberAgreementUseCase
import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.ActiveTerms
import org.around.ieuri.auth.domain.member.Agreement
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.event.TermsAgreementChanged
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 약관 동의 변경(SPEC-0011 R10·R12). 현재 상태는 동의 테이블이, 이력은 감사 기록이 갖는다.
 * - 철회해도 다른 오퍼레이션을 거절하지 않는다. 이용 제한은 클라이언트가 `required=true, agreed=false`를 보고 건다(R11).
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberAgreementService(
    private val memberRepository: MemberRepository,
    private val eventPublisher: MemberEventPublisherPort,
    private val clock: Clock,
) : MemberAgreementUseCase {

    override fun updateAgreement(command: UpdateAgreementCommand): AgreementStatusView {
        val now = clock.instant()
        val active = ActiveTerms.of(memberRepository.findEffectiveTerms(now))
        val agreement = load(command.memberId).decideAgreement(command.termsCode, command.agreed, active, now)
        record(command.memberId, listOf(agreement), active, command.agreed)
        return statusesOf(command.memberId, active).first { it.termsCode == command.termsCode }
    }

    /** 동의만 받는다. 이미 동의한 코드를 다시 내도 오류가 아니다. */
    override fun agreeToRequiredTerms(command: AgreeToRequiredTermsCommand): List<AgreementStatusView> {
        val now = clock.instant()
        val active = ActiveTerms.of(memberRepository.findEffectiveTerms(now))
        val agreements = load(command.memberId).agreeToRequiredTerms(command.termsCodes, active, now)
        record(command.memberId, agreements, active, agreed = true)
        return statusesOf(command.memberId, active)
    }

    private fun record(memberId: Long, agreements: List<Agreement>, active: ActiveTerms, agreed: Boolean) {
        if (agreements.isEmpty()) return
        memberRepository.saveAgreements(memberId, agreements)
        eventPublisher.publish(TermsAgreementChanged(memberId, agreements.mapNotNull { active.find(it.termsCode) }, agreed))
    }

    private fun statusesOf(memberId: Long, active: ActiveTerms): List<AgreementStatusView> = active.statusOf(memberRepository.findAgreements(memberId)).map(AgreementStatusView::of)

    private fun load(memberId: Long): Member = memberRepository.findById(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")
}
