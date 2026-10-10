package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult

/** SNS 회원가입. id_token 검증 → 가입 → provider 토큰 확보 순서이며, 토큰 확보가 실패해도 가입은 성공한다. */
interface SnsRegistrationUseCase {
    fun registerBySns(command: RegisterMemberCommand): RegisterMemberResult
}
