package org.around.ieuri.auth.application.member.provided

import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeResult
import org.around.ieuri.auth.application.member.provided.dto.EmailChangeRequested
import org.around.ieuri.auth.application.member.provided.dto.EmailCredentialView
import org.around.ieuri.auth.application.member.provided.dto.LifecycleResult
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberResult
import org.around.ieuri.auth.application.member.provided.dto.RegisterSnsIdentityCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordResult
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationResult
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleChangeResult
import org.around.ieuri.auth.application.member.provided.dto.SnsRegistrationResult
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailResult
import org.around.ieuri.auth.application.member.provided.dto.WithdrawalTargetView
import org.around.ieuri.auth.domain.member.Provider

/**
 * 회원 쓰기 유스케이스. 호출 하나가 트랜잭션 하나다. 인가는 리졸버가 판정한다.
 * - Command에는 클라이언트가 지정한 것만 담는다. 회원 id·접속지처럼 서버가 요청에서 알아낸 값은 인자로 받는다.
 * - 외부 호출이 끼는 흐름은 따로 있다: [SnsRegistrationUseCase]·[MemberLoginUseCase]·[MemberWithdrawalUseCase]·[ProfileImageUseCase].
 */
interface MemberCommandUseCase {

    // ── 가입 ──

    /** 검증을 마친 SNS 신원으로 가입한다. 신원을 그대로 믿으므로 리졸버가 부르지 않는다 — [SnsRegistrationUseCase]만 쓴다. */
    fun registerBySnsIdentity(command: RegisterSnsIdentityCommand): SnsRegistrationResult

    /** 자체(EMAIL) 가입. 미인증으로 시작하며 인증 코드를 발급한다. */
    fun registerByEmail(command: RegisterEmailMemberCommand): RegisterMemberResult

    // ── 가입 이메일 인증 ──

    fun verifyEmail(command: VerifyEmailCommand): VerifyEmailResult

    fun resendVerification(command: ResendVerificationCommand): ResendVerificationResult

    /** 인증 대기 주소 정정(SPEC-0024 R7). 인증을 마친 회원은 쓸 수 없고, 쿨다운은 [resendVerification]과 함께 센다. */
    fun retargetEmailVerification(command: RetargetEmailVerificationCommand, memberId: Long): RetargetEmailVerificationResult

    // ── 비밀번호 재설정 ──

    /** 이메일로 재설정 코드를 보낸다. */
    fun requestPasswordReset(command: RequestPasswordResetCommand)

    /** 코드가 맞으면 새 비밀번호로 바꾸고 로그인 세션을 모두 끊는다. */
    fun resetPassword(command: ResetPasswordCommand): ResetPasswordResult

    // ── 이메일 변경(SPEC-0011 R7·R8) ──

    /** 새 주소로 코드를 보낸다. 통과 전까지 회원의 이메일은 기존 값이다. [ipAddress]는 접속기록용이며 알 수 없으면 null이다. */
    fun changeEmail(command: ChangeEmailCommand, memberId: Long, ipAddress: String?): EmailChangeRequested

    /** 통과하면 이메일이 바뀌고, 자체(EMAIL) 회원은 로그인 식별자도 함께 바뀐다(R8). */
    fun confirmEmailChange(command: ConfirmEmailChangeCommand, memberId: Long): ConfirmEmailChangeResult

    fun resendEmailChange(command: ResendEmailChangeCommand, memberId: Long): EmailChangeRequested

    // ── 정지·복구(관리자)와 탈퇴의 DB 단계 ──

    fun suspend(command: SuspendMemberCommand): LifecycleResult

    fun reactivate(command: ReactivateMemberCommand): LifecycleResult

    /** 탈퇴할 회원의 provider 정보. 없는 회원이면 MEMBER_NOT_FOUND다. 복호화한 토큰을 내보내므로 리졸버가 부르지 않는다. */
    fun withdrawalTarget(memberId: Long): WithdrawalTargetView

    /** 회원과 소유 데이터를 지운다. provider 연결 해제가 실패했으면 [unlinkFailureReason]을 함께 남긴다(탈퇴는 그대로 진행한다). */
    fun deleteMember(memberId: Long, provider: Provider, unlinkFailureReason: String?)

    // ── 프로필(SPEC-0011 R5·R6) ──

    /** 닉네임 변경. 반영된 값(공백 제거 후)을 돌려준다. */
    fun changeNickname(command: ChangeNicknameCommand, memberId: Long): String

    /** 확인이 끝난 임시 키를 회원의 프로필 이미지로 삼고, 이전 키를 돌려준다(없었으면 null). */
    fun changeProfileImage(memberId: Long, pendingObjectKey: String): String?

    /** 프로필 이미지를 떼고 이전 키를 돌려준다. 이미 없어도 성공한다. */
    fun removeProfileImage(memberId: Long): String?

    // ── 약관 동의(SPEC-0011 R10·R12) ──

    /** 약관 토글(R10). 반영 후 그 약관의 상태를 돌려준다. */
    fun updateAgreement(command: UpdateAgreementCommand, memberId: Long): AgreementStatusView

    /** 미동의 필수 약관에 동의(R12). 반영 후 전체 상태를 돌려준다. */
    fun agreeToRequiredTerms(command: AgreeToRequiredTermsCommand, memberId: Long): List<AgreementStatusView>

    // ── 역할(관리자) ──

    /** [actorMemberId]는 수행한 관리자다(감사 기록에 남는다). */
    fun assignRole(command: AssignRoleCommand, actorMemberId: Long): RoleChangeResult

    /** 수행자가 대상 본인이고 마지막 관리 역할이면 거부한다(자기 강등 방지). */
    fun revokeRole(command: RevokeRoleCommand, actorMemberId: Long): RoleChangeResult

    // ── 로그인의 DB 단계. 비밀번호 해시를 내보내므로 리졸버가 부르지 않는다 — [MemberLoginUseCase]만 쓴다 ──

    /** SNS 신원의 회원을 로그인시킨다. 미가입은 MEMBER_NOT_FOUND, 유효한 정지는 ACCOUNT_INACTIVE다. 기간제 정지가 만료됐으면 복구하고 허용한다. */
    fun loginSnsMember(provider: Provider, providerUserId: String): Long

    /** [loginIdentifier]는 정규화한 이메일의 보호 변환값이다. 없는 계정이면 null. */
    fun findEmailCredential(loginIdentifier: String): EmailCredentialView?

    /** 비밀번호 대조를 마친 회원을 로그인시킨다. 정지 판정은 [loginSnsMember]와 같다. */
    fun loginMember(memberId: Long)

    /** 탈퇴 때 연결을 끊는 데 쓸 provider 토큰을 저장한다. 회원이 없으면 아무것도 하지 않는다. */
    fun saveProviderRefreshToken(memberId: Long, token: String)
}
