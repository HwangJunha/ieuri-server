package org.around.ieuri.auth.application.port.`in`.command

import org.around.ieuri.auth.application.command.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.command.UpdateAgreementCommand
import org.around.ieuri.auth.application.query.AgreementStatusView

/**
 * 약관 동의 유스케이스(SPEC-0011 R9~R12). 변경은 반영 후 상태를 돌려준다.
 * - 서버는 상태만 노출하고, 미동의 회원의 오퍼레이션 호출도 거절하지 않는다(R11). 게이트는 클라이언트 몫이다.
 */
interface MemberAgreementUseCase {
    /** 시행 중인 약관 전체에 대한 회원의 동의 상태. 필수·선택을 모두 담는다. */
    fun agreements(memberId: Long): List<AgreementStatusView>

    /** 약관 토글(R10). 반영 후 그 약관의 상태를 돌려준다. */
    fun updateAgreement(command: UpdateAgreementCommand): AgreementStatusView

    /** 미동의 필수 약관에 동의(R12). 반영 후 전체 상태를 돌려준다. */
    fun agreeToRequiredTerms(command: AgreeToRequiredTermsCommand): List<AgreementStatusView>
}
