package org.around.ieuri.auth.application.member.provided
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordResult
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailResult

/** 회원가입 유스케이스: 가입·이메일 검증·재발송·인증 주소 재입력·비밀번호 재설정. */
interface MemberRegistrationUseCase {
    /** SNS 회원가입. 가입은 이메일 검증 완료를 기다리지 않고 성공 처리된다. */
    fun registerBySns(command: RegisterMemberCommand): RegisterMemberResult

    /** 자체(EMAIL) 회원가입. 이메일 인증 전 미검증 상태로 시작하며 검증 코드를 발급한다. */
    fun registerByEmail(command: RegisterEmailMemberCommand): RegisterMemberResult

    /** 이메일 검증 코드 확인. */
    fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult

    /** 이메일 검증 코드 재발송. */
    fun resendVerification(command: ResendVerificationCommand): ResendVerificationResult

    /** 인증 대기 주소 정정(SPEC-0024 R7). 인증을 마친 회원은 쓸 수 없고, 쿨다운은 [resendVerification]과 공유한다. */
    fun retargetEmailVerification(command: RetargetEmailVerificationCommand): RetargetEmailVerificationResult

    /** 비밀번호 재설정 요청. 이메일로 재설정 코드를 발송한다. */
    fun requestPasswordReset(command: RequestPasswordResetCommand)

    /** 비밀번호 재설정 실행. 코드 검증 성공 시 새 비밀번호로 교체하고 refresh 토큰을 폐기한다. */
    fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult
}
