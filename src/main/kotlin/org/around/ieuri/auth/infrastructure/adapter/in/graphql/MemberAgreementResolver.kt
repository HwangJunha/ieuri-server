package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.auth.application.command.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.command.UpdateAgreementCommand
import org.around.ieuri.auth.application.port.`in`.command.MemberAgreementUseCase
import org.around.ieuri.auth.application.query.AgreementStatusView
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsInput
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsPayload
import org.around.ieuri.common.graphql.generated.types.AgreementStatus
import org.around.ieuri.common.graphql.generated.types.TermsCode
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementInput
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementPayload
import org.around.ieuri.common.security.CurrentMemberProvider

/**
 * 약관 동의 조회·변경 리졸버(SPEC-0011 R9~R12).
 *
 * **서버는 상태만 내려주고 화면 진입을 막지 않는다** — 게이트는 클라이언트가 이 응답을
 * 근거로 건다. 미동의 회원의 다른 오퍼레이션 호출도 거절하지 않는다(R11 단서).
 */
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

    /** 실패해도 `agreements`는 빈 목록이 아니라 계약상 non-null이라 빈 목록으로 채운다. */
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
     * read DTO → 응답 타입. **매핑은 리졸버가 소유한다** — application이 DGS 생성 타입을
     * 알면 계층 방향이 뒤집힌다.
     *
     * `termsCode`는 스키마가 enum이라 DB의 코드 문자열을 그대로 넣을 수 없다. 시행 중인
     * 약관에 enum에 없는 코드가 들어오면 여기서 터지는데, 조용히 건너뛰는 것보다 낫다 —
     * 필수 약관 하나가 사라지면 클라이언트 게이트가 통째로 풀린다.
     */
    private fun AgreementStatusView.toGql() = AgreementStatus(
        termsCode = TermsCode.valueOf(termsCode),
        required = required,
        agreed = agreed,
        agreedAt = agreedAt?.toString(),
        version = version,
    )
}
