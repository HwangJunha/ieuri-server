package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand

/**
 * 약관 동의 변경(SPEC-0011 R10·R12). 반영 후 상태를 돌려준다.
 * - 미동의 회원의 다른 오퍼레이션도 거절하지 않는다(R11). 게이트는 클라이언트 몫이다.
 */
interface MemberAgreementUseCase {
    /** 약관 토글(R10). 반영 후 그 약관의 상태를 돌려준다. */
    fun updateAgreement(command: UpdateAgreementCommand): AgreementStatusView

    /** 미동의 필수 약관에 동의(R12). 반영 후 전체 상태를 돌려준다. */
    fun agreeToRequiredTerms(command: AgreeToRequiredTermsCommand): List<AgreementStatusView>
}
