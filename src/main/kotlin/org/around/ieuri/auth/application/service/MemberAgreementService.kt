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
 *
 * **이력과 상태를 나눠 둔다.** `member_terms_agreement`는 현재 상태(약관당 한 행)이고,
 * 동의·철회 **이력은 감사 로그**가 갖는다 — 상태 테이블은 회원 삭제 시 함께 지워지지만
 * 감사 로그는 FK가 없어 남기 때문이다(철회·탈퇴가 증빙이 가장 필요한 사건이다).
 *
 * 상태 판정의 핵심은 **버전이 올라가면 이전 동의가 이월되지 않는다**는 것이다. 시행 중인
 * 버전에 대한 이력이 없으면 미동의이며, 필수 약관이면 클라이언트가 게이트를 건다.
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
     * 약관 동의를 켜고 끈다. **필수 약관도 철회할 수 있다** — 동의 철회권은 동의와 대등한
     * 수단으로 보장되어야 하며, 탈퇴로만 물릴 수 있게 하면 철회와 계정 삭제가 강제로 묶인다.
     *
     * 철회해도 서버가 다른 오퍼레이션을 거절하지는 않는다. 필수 약관을 철회하면
     * `agreements`의 그 항목이 `required=true, agreed=false`가 되고, 이용 제한은
     * 클라이언트가 그 값을 보고 건다(R11과 같은 규약).
     */
    @Transactional
    override fun updateAgreement(command: UpdateAgreementCommand): AgreementStatusView {
        val activeTerms = termsRepository.findActiveTerms(clock.instant())
        val terms = activeTerms.firstOrNull { it.code == command.termsCode }
            ?: throw TermsNotFoundException("시행 중인 약관이 아닙니다.")

        record(command.memberId, listOf(terms), command.agreed)

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

        record(command.memberId, targets, agreed = true)

        return statusesOf(command.memberId, activeTerms)
    }

    /**
     * 동의/철회를 기록한다. 전부 같은 시각을 쓴다 — 한 번의 사용자 행위이기 때문이다.
     *
     * **두 곳에 쓴다.** 감사 로그가 이력(append-only, 탈퇴 후에도 보존)이고, 동의 테이블은
     * 현재 상태(약관당 한 행)다. 감사 로그로 상태를 계산하려 들면 JSONB 스캔이 되므로
     * 상태 테이블을 따로 둔다.
     */
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

    /**
     * 시행 중인 약관 각각에 대해 회원의 최근 판단을 얹는다.
     *
     * **이력을 시행 중인 버전으로 조회한다** — 코드만 맞고 버전이 다른 이력은 이월되지 않아
     * 미동의로 떨어진다. 미동의면 `agreedAt`은 null이다.
     */
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
