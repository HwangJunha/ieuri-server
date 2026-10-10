package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.SnsRegistrationResult
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailResult

/** 가입과 가입 이메일 인증. 호출 하나가 트랜잭션 하나다. */
interface MemberRegistrationUseCase {
    /** 검증을 마친 SNS 신원으로 가입한다. 신원을 그대로 믿으므로 리졸버가 부르지 않는다 — [SnsRegistrationUseCase]만 쓴다. */
    fun registerBySnsIdentity(command: RegisterSnsIdentityCommand): SnsRegistrationResult

    /** 자체(EMAIL) 가입. 미인증으로 시작하며 인증 코드를 발급한다. */
    fun registerByEmail(command: RegisterEmailMemberCommand): RegisterMemberResult

    fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult

    fun resendVerification(command: ResendVerificationCommand): ResendVerificationResult

    /** 인증 대기 주소 정정(SPEC-0024 R7). 인증을 마친 회원은 쓸 수 없고, 쿨다운은 [resendVerification]과 함께 센다. */
    fun retargetEmailVerification(command: RetargetEmailVerificationCommand): RetargetEmailVerificationResult
}
