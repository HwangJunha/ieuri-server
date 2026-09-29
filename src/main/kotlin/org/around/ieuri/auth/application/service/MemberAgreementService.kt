package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.command.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.command.UpdateAgreementCommand
import org.around.ieuri.auth.application.exception.TermsCodeNotRequiredException
import org.around.ieuri.auth.application.exception.TermsNotFoundException
import org.around.ieuri.auth.application.port.`in`.command.MemberAgreementUseCase
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
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
 * - 상태는 `member_terms_agreement`(약관당 한 행), 이력은 감사 로그가 갖는다.
 * - 버전이 올라가면 이전 동의는 이월되지 않는다.
 */
@Service
class MemberAgreementService(
    private val termsRepository: TermsRepositoryPort,
    private val agreementRepository: MemberAgreementRepositoryPort,
    private val auditLog: AuditLogPort,
    private val clock: Clock,
) : MemberAgreementUseCase {

    @Transactional(readOnly = true)
    override fun agreements(memberId: Long): List<AgreementStatusView> = statusesOf(memberId, termsRepository.findActiveTerms(clock.instant()))

    /**
     * 약관 동의를 켜고 끈다. 필수 약관도 철회할 수 있다.
     * - 철회해도 다른 오퍼레이션을 거절하지 않는다. 이용 제한은 클라이언트가 `required=true, agreed=false`를 보고 건다(R11).
     */
    @Transactional
    override fun updateAgreement(command: UpdateAgreementCommand): AgreementStatusView {
        val activeTerms = termsRepository.findActiveTerms(clock.instant())
        val terms = activeTerms.firstOrNull { it.code == command.termsCode }
            ?: throw TermsNotFoundException("시행 중인 약관이 아닙니다.")

        record(command.memberId, listOf(terms), command.agreed)

        return statusesOf(command.memberId, activeTerms).first { it.termsCode == command.termsCode }
    }

    /** 미동의 필수 약관에 동의(R12). 동의만 받으며, 이미 동의한 코드를 다시 제출해도 오류가 아니다. */
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

        record(command.memberId, targets, agreed = true)

        return statusesOf(command.memberId, activeTerms)
    }

    /** 동의/철회를 감사 로그(이력)와 동의 테이블(현재 상태) 두 곳에 같은 시각으로 기록한다. */
    private fun record(memberId: Long, terms: List<TermsView>, agreed: Boolean) {
        if (terms.isEmpty()) return
        val now = clock.instant()

        auditLog.record(
            memberId = memberId,
            eventType = if (agreed) "TERMS_AGREED" else "TERMS_WITHDRAWN",
            // 약관 본문이 아니라 식별자만 담는다 — 개인정보를 로그에 남기지 않는다(POL-013).
            payload = mapOf(
                "terms" to terms.map { mapOf("code" to it.code, "version" to it.version, "required" to it.required) },
                "agreed" to agreed,
            ),
        )

        agreementRepository.upsert(
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

    /** 시행 중인 약관마다 회원의 판단을 얹는다. 버전이 다른 이력은 미동의이며 그때 agreedAt은 null이다. */
    private fun statusesOf(memberId: Long, activeTerms: List<TermsView>): List<AgreementStatusView> {
        val history = agreementRepository.findCurrentAgreements(memberId)
        return activeTerms.map { terms ->
            val record = history[TermsKey(terms.code, terms.version)]
            val agreed = record?.agreed == true
            AgreementStatusView(
                termsCode = terms.code,
                required = terms.required,
                agreed = agreed,
                agreedAt = if (agreed) record.agreedAt else null,
                version = terms.version,
            )
        }
    }
}
