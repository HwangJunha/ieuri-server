package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.member.provided.MemberAgreementUseCase
import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsInput
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsPayload
import org.around.ieuri.common.graphql.generated.types.AgreementStatus
import org.around.ieuri.common.graphql.generated.types.TermsCode
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementInput
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementPayload
import org.around.ieuri.common.security.CurrentMemberProvider

/** 약관 동의 조회·변경 리졸버(SPEC-0011 R9~R12). 서버는 상태만 내려주고 오퍼레이션을 막지 않는다(R11). */
@DgsComponent
class MemberAgreementResolver(
    private val memberAgreementUseCase: MemberAgreementUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsQuery
    fun agreements(): List<AgreementStatus> {
        val memberId = currentMemberProvider.requireMemberId()
        return memberAgreementUseCase.agreements(memberId).map { it.toGql() }
    }

    @DgsMutation
    fun updateAgreement(@InputArgument input: UpdateAgreementInput): UpdateAgreementPayload = withUserErrors(
        onError = { UpdateAgreementPayload(success = false, userErrors = it, agreement = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val status = memberAgreementUseCase.updateAgreement(
            UpdateAgreementCommand(
                memberId = memberId,
                termsCode = input.termsCode.name,
                agreed = input.agreed,
            ),
        )
        UpdateAgreementPayload(success = true, userErrors = emptyList(), agreement = status.toGql())
    }

    /** 실패 시 `agreements`는 계약상 non-null이라 빈 목록으로 채운다. */
    @DgsMutation
    fun agreeToRequiredTerms(
        @InputArgument input: AgreeToRequiredTermsInput,
    ): AgreeToRequiredTermsPayload = withUserErrors(
        onError = { AgreeToRequiredTermsPayload(success = false, userErrors = it, agreements = emptyList()) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val statuses = memberAgreementUseCase.agreeToRequiredTerms(
            AgreeToRequiredTermsCommand(
                memberId = memberId,
                termsCodes = input.termsCodes.map { it.name },
            ),
        )
        AgreeToRequiredTermsPayload(
            success = true,
            userErrors = emptyList(),
            agreements = statuses.map { it.toGql() },
        )
    }

    /**
     * read DTO → 응답 타입.
     * - 시행 중인 약관 코드가 스키마 enum에 없으면 예외가 난다. 건너뛰면 필수 약관 게이트가 풀린다.
     */
    private fun AgreementStatusView.toGql() = AgreementStatus(
        termsCode = TermsCode.valueOf(termsCode),
        required = required,
        agreed = agreed,
        agreedAt = agreedAt?.toString(),
        version = version,
    )
}
