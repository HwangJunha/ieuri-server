package org.around.ieuri.auth.adapter.graphql.resolver
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.adapter.graphql.mapper.toCommand
import org.around.ieuri.auth.adapter.graphql.mapper.toGql
import org.around.ieuri.auth.adapter.graphql.support.withAuthUserErrors
import org.around.ieuri.auth.application.member.provided.MemberCommandUseCase
import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsInput
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsPayload
import org.around.ieuri.common.graphql.generated.types.AgreementStatus
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementInput
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementPayload
import org.around.ieuri.common.security.CurrentMemberProvider

/** 약관 동의 조회·변경 리졸버(SPEC-0011 R9~R12). 서버는 상태만 내려주고 오퍼레이션을 막지 않는다(R11). */
@DgsComponent
class MemberAgreementResolver(
    private val memberCommandUseCase: MemberCommandUseCase,
    private val memberQueryUseCase: MemberQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsQuery
    fun agreements(): List<AgreementStatus> {
        val memberId = currentMemberProvider.requireMemberId()
        return memberQueryUseCase.agreements(memberId).map { it.toGql() }
    }

    @DgsMutation
    fun updateAgreement(@InputArgument input: UpdateAgreementInput): UpdateAgreementPayload = withAuthUserErrors(
        onError = { UpdateAgreementPayload(success = false, userErrors = it, agreement = null) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val status = memberCommandUseCase.updateAgreement(input.toCommand(), memberId)
        UpdateAgreementPayload(success = true, userErrors = emptyList(), agreement = status.toGql())
    }

    /** 실패 시 `agreements`는 계약상 non-null이라 빈 목록으로 채운다. */
    @DgsMutation
    fun agreeToRequiredTerms(
        @InputArgument input: AgreeToRequiredTermsInput,
    ): AgreeToRequiredTermsPayload = withAuthUserErrors(
        onError = { AgreeToRequiredTermsPayload(success = false, userErrors = it, agreements = emptyList()) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val statuses = memberCommandUseCase.agreeToRequiredTerms(input.toCommand(), memberId)
        AgreeToRequiredTermsPayload(
            success = true,
            userErrors = emptyList(),
            agreements = statuses.map { it.toGql() },
        )
    }
}
