package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.command.UpdateAgreementCommand
import org.around.ieuri.auth.application.exception.TermsCodeNotRequiredException
import org.around.ieuri.auth.application.exception.TermsCodeNotTogglableException
import org.around.ieuri.auth.application.exception.TermsNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.MemberAgreementUseCase
import org.around.ieuri.auth.application.port.out.persistence.MemberAgreementRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsKey
import org.around.ieuri.auth.application.port.out.persistence.TermsRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.TermsView
import org.around.ieuri.auth.application.query.AgreementStatusView
import org.around.ieuri.auth.domain.vo.Agreement
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 약관 동의 조회·변경(SPEC-0011 R9~R12).
 *
 * **동의 이력은 append-only다** — 철회도 `agreed = false` 행을 추가하는 것이지 기존 행을
 * 고치거나 지우지 않는다. 법적 증빙이라 덮어쓰지 않는다.
 *
 * 상태 판정의 핵심은 **버전이 올라가면 이전 동의가 이월되지 않는다**는 것이다. 시행 중인
 * 버전에 대한 이력이 없으면 미동의이며, 필수 약관이면 클라이언트가 게이트를 건다.
 */
@Service
class MemberAgreementService(
    private val termsRepository: TermsRepositoryPort,
    private val agreementRepository: MemberAgreementRepositoryPort,
    private val clock: Clock,
) : MemberAgreementUseCase {

    @Transactional(readOnly = true)
    override fun agreements(memberId: Long): List<AgreementStatusView> =
        statusesOf(memberId, termsRepository.findActiveTerms(clock.instant()))

    /**
     * 선택 약관만 토글한다. 필수 약관은 거절 — 철회 경로를 만들지 않는 것이 스펙의 결정이며,
     * 필수 약관 철회는 사실상 서비스 이용 중단이라 탈퇴로만 가능하다.
     */
    @Transactional
    override fun updateAgreement(command: UpdateAgreementCommand): AgreementStatusView {
        val activeTerms = termsRepository.findActiveTerms(clock.instant())
        val terms = activeTerms.firstOrNull { it.code == command.termsCode }
            ?: throw TermsNotFoundException("시행 중인 약관이 아닙니다.")
        if (terms.required) {
            throw TermsCodeNotTogglableException("필수 약관은 개별로 끌 수 없습니다.")
        }

        append(command.memberId, listOf(terms), command.agreed)

        return statusesOf(command.memberId, activeTerms).first { it.termsCode == command.termsCode }
    }

    /**
     * 미동의 필수 약관에 동의(R12). **동의만 받는다** — 이 경로로 철회는 불가능하다.
     *
     * 이미 동의한 코드를 다시 제출해도 오류가 아니다. append-only라 같은 판단이 한 줄 더
     * 쌓일 뿐이고, 클라이언트가 어느 것이 미동의인지 다시 계산하게 만들 이유가 없다.
     */
    @Transactional
    override fun agreeToRequiredTerms(command: AgreeToRequiredTermsCommand): List<AgreementStatusView> {
        val activeTerms = termsRepository.findActiveTerms(clock.instant())
        val byCode = activeTerms.associateBy { it.code }

        val targets = command.termsCodes.distinct().map { code ->
            val terms = byCode[code] ?: throw TermsNotFoundException("시행 중인 약관이 아닙니다.")
            if (!terms.required) {
                throw TermsCodeNotRequiredException("필수 약관이 아닙니다.")
            }
            terms
        }

        append(command.memberId, targets, agreed = true)

        return statusesOf(command.memberId, activeTerms)
    }

    /** 동의/비동의 행을 추가한다. 전부 같은 시각을 쓴다 — 한 번의 사용자 행위이기 때문이다. */
    private fun append(memberId: Long, terms: List<TermsView>, agreed: Boolean) {
        if (terms.isEmpty()) return
        val now = clock.instant()
        agreementRepository.append(
            memberId,
            terms.map {
                Agreement(
                    termsId = it.id,
                    termsCode = it.code,
                    termsVersion = it.version,
                    agreed = agreed,
                    agreedAt = now,
                )
            },
        )
    }

    /**
     * 시행 중인 약관 각각에 대해 회원의 최근 판단을 얹는다.
     *
     * **이력을 시행 중인 버전으로 조회한다** — 코드만 맞고 버전이 다른 이력은 이월되지 않아
     * 미동의로 떨어진다. 미동의면 `agreedAt`은 null이다.
     */
    private fun statusesOf(memberId: Long, activeTerms: List<TermsView>): List<AgreementStatusView> {
        val history = agreementRepository.findLatestAgreements(memberId)
        return activeTerms.map { terms ->
            val record = history[TermsKey(terms.code, terms.version)]
            val agreed = record?.agreed == true
            AgreementStatusView(
                termsCode = terms.code,
                required = terms.required,
                agreed = agreed,
                agreedAt = if (agreed) record?.agreedAt else null,
                version = terms.version,
            )
        }
    }
}
