package org.around.ieuri.auth.adapter.graphql.mapper

import org.around.ieuri.auth.application.member.provided.dto.AgreeToRequiredTermsCommand
import org.around.ieuri.auth.application.member.provided.dto.AgreementInput
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.ChangeEmailCommand
import org.around.ieuri.auth.application.member.provided.dto.ChangeNicknameCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ConfirmProfileImageUploadCommand
import org.around.ieuri.auth.application.member.provided.dto.IssueProfileImageUploadUrlCommand
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.ReactivateMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RequestPasswordResetCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendEmailChangeCommand
import org.around.ieuri.auth.application.member.provided.dto.ResendVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.RetargetEmailVerificationCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.application.member.provided.dto.SuspendMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.UpdateAgreementCommand
import org.around.ieuri.auth.application.member.provided.dto.VerifyEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsInput
import org.around.ieuri.common.graphql.generated.types.AgreementStatus
import org.around.ieuri.common.graphql.generated.types.AssignRoleInput
import org.around.ieuri.common.graphql.generated.types.AuthTokenPayload
import org.around.ieuri.common.graphql.generated.types.ChangeEmailInput
import org.around.ieuri.common.graphql.generated.types.ChangeNicknameInput
import org.around.ieuri.common.graphql.generated.types.ConfirmEmailChangeInput
import org.around.ieuri.common.graphql.generated.types.ConfirmProfileImageUploadInput
import org.around.ieuri.common.graphql.generated.types.CreateProfileImageUploadUrlInput
import org.around.ieuri.common.graphql.generated.types.LoginEmailInput
import org.around.ieuri.common.graphql.generated.types.LoginSnsInput
import org.around.ieuri.common.graphql.generated.types.LogoutInput
import org.around.ieuri.common.graphql.generated.types.ReactivateMemberInput
import org.around.ieuri.common.graphql.generated.types.RefreshTokenInput
import org.around.ieuri.common.graphql.generated.types.RegisterEmailMemberInput
import org.around.ieuri.common.graphql.generated.types.RegisterSnsMemberInput
import org.around.ieuri.common.graphql.generated.types.RequestPasswordResetInput
import org.around.ieuri.common.graphql.generated.types.ResendEmailChangeInput
import org.around.ieuri.common.graphql.generated.types.ResendVerificationInput
import org.around.ieuri.common.graphql.generated.types.ResetPasswordInput
import org.around.ieuri.common.graphql.generated.types.RetargetEmailVerificationInput
import org.around.ieuri.common.graphql.generated.types.RevokeRoleInput
import org.around.ieuri.common.graphql.generated.types.SuspendMemberInput
import org.around.ieuri.common.graphql.generated.types.TermsCode
import org.around.ieuri.common.graphql.generated.types.UpdateAgreementInput
import org.around.ieuri.common.graphql.generated.types.VerifyEmailInput
import org.around.ieuri.common.pagination.CursorPage
import java.time.Instant
import java.util.UUID
import org.around.ieuri.common.graphql.generated.types.MemberConnection as GqlMemberConnection
import org.around.ieuri.common.graphql.generated.types.MemberDetail as GqlMemberDetail
import org.around.ieuri.common.graphql.generated.types.MemberEdge as GqlMemberEdge
import org.around.ieuri.common.graphql.generated.types.MemberStatus as GqlMemberStatus
import org.around.ieuri.common.graphql.generated.types.MemberSummary as GqlMemberSummary
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.RoleInfo as GqlRoleInfo

/* auth의 GraphQL 입력 → Command, read DTO → 응답 타입 변환. */

/** SNS 가입 입력 → Command. enum은 이름이 같아 이름으로 옮기고, 닉네임은 형식을 검사하지 않는다(R30). */
fun RegisterSnsMemberInput.toCommand(): RegisterMemberCommand = RegisterMemberCommand(
    provider = Provider.valueOf(provider.name),
    idToken = idToken,
    submittedEmail = email,
    ageVerified = ageVerified,
    agreements = agreements.map { AgreementInput(it.termsCode.name, it.agreed) },
    authorizationCode = authorizationCode,
    nickname = nickname,
)

/** 자체(EMAIL) 가입 입력 → Command. */
fun RegisterEmailMemberInput.toCommand(): RegisterEmailMemberCommand = RegisterEmailMemberCommand(
    email = email,
    password = password,
    ageVerified = ageVerified,
    agreements = agreements.map { AgreementInput(it.termsCode.name, it.agreed) },
)

/** SNS 로그인 입력 → Command. */
fun LoginSnsInput.toCommand(): LoginCommand = LoginCommand(
    provider = Provider.valueOf(provider.name),
    idToken = idToken,
    authorizationCode = authorizationCode,
)

/** 자체(EMAIL) 로그인 입력 → Command. */
fun LoginEmailInput.toCommand(): LoginEmailCommand = LoginEmailCommand(email = email, password = password)

fun RefreshTokenInput.toCommand(): RefreshTokenCommand = RefreshTokenCommand(refreshToken = refreshToken)

fun LogoutInput.toCommand(): LogoutCommand = LogoutCommand(refreshToken = refreshToken)

fun VerifyEmailInput.toCommand(): VerifyEmailCommand = VerifyEmailCommand(
    verificationPublicId = UUID.fromString(verificationId),
    code = code,
)

fun ResendVerificationInput.toCommand(): ResendVerificationCommand = ResendVerificationCommand(verificationPublicId = UUID.fromString(verificationId))

fun RetargetEmailVerificationInput.toCommand(): RetargetEmailVerificationCommand = RetargetEmailVerificationCommand(newEmail = newEmail)

fun RequestPasswordResetInput.toCommand(): RequestPasswordResetCommand = RequestPasswordResetCommand(email = email)

fun ChangeEmailInput.toCommand(): ChangeEmailCommand = ChangeEmailCommand(newEmail = newEmail)

fun ConfirmEmailChangeInput.toCommand(): ConfirmEmailChangeCommand = ConfirmEmailChangeCommand(
    verificationPublicId = UUID.fromString(verificationId),
    code = code,
)

fun ResendEmailChangeInput.toCommand(): ResendEmailChangeCommand = ResendEmailChangeCommand(verificationPublicId = UUID.fromString(verificationId))

fun ChangeNicknameInput.toCommand(): ChangeNicknameCommand = ChangeNicknameCommand(nickname = nickname)

fun CreateProfileImageUploadUrlInput.toCommand(): IssueProfileImageUploadUrlCommand = IssueProfileImageUploadUrlCommand(
    contentType = contentType,
    contentLength = contentLength.toLong(),
)

fun ConfirmProfileImageUploadInput.toCommand(): ConfirmProfileImageUploadCommand = ConfirmProfileImageUploadCommand(objectKey = objectKey)

/** 약관 코드 enum은 이름으로 옮긴다(Command는 문자열로 받는다). */
fun UpdateAgreementInput.toCommand(): UpdateAgreementCommand = UpdateAgreementCommand(termsCode = termsCode.name, agreed = agreed)

fun AgreeToRequiredTermsInput.toCommand(): AgreeToRequiredTermsCommand = AgreeToRequiredTermsCommand(termsCodes = termsCodes.map { it.name })

/** 정지 입력 → Command. 만료 시각은 ISO-8601 문자열이며 없으면 무기한이다. */
fun SuspendMemberInput.toCommand(): SuspendMemberCommand = SuspendMemberCommand(
    memberId = memberId,
    reason = reason,
    suspendedUntil = suspendedUntil?.let { Instant.parse(it) },
)

fun ReactivateMemberInput.toCommand(): ReactivateMemberCommand = ReactivateMemberCommand(memberId = memberId)

/** 역할 부여 입력 → Command. 입력의 `memberId`는 역할을 받는 대상이다. */
fun AssignRoleInput.toCommand(): AssignRoleCommand = AssignRoleCommand(targetMemberId = memberId, roleCode = roleCode)

/** 역할 회수 입력 → Command. 입력의 `memberId`는 역할을 잃는 대상이다. */
fun RevokeRoleInput.toCommand(): RevokeRoleCommand = RevokeRoleCommand(targetMemberId = memberId, roleCode = roleCode)

/** 비밀번호 재설정 입력 → Command. */
fun ResetPasswordInput.toCommand(): ResetPasswordCommand = ResetPasswordCommand(
    email = email,
    code = code,
    newPassword = newPassword,
)

/** 발급된 토큰 쌍 → 성공 payload. */
fun TokenPairResult.toPayload(): AuthTokenPayload = AuthTokenPayload(
    success = true,
    userErrors = emptyList(),
    accessToken = accessToken,
    refreshToken = refreshToken,
    tokenType = tokenType,
    accessTokenExpiresIn = accessTokenExpiresInSeconds.toInt(),
    refreshTokenExpiresIn = refreshTokenExpiresInSeconds.toInt(),
)

/** 시행 중인 약관 코드가 스키마 enum에 없으면 예외가 난다. 건너뛰면 필수 약관 게이트가 풀린다. */
fun AgreementStatusView.toGql(): AgreementStatus = AgreementStatus(
    termsCode = TermsCode.valueOf(termsCode),
    required = required,
    agreed = agreed,
    agreedAt = agreedAt?.toString(),
    version = version,
)

fun MemberDetailView.toGql(): GqlMemberDetail = GqlMemberDetail(
    id = memberId,
    email = email,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
    status = GqlMemberStatus.valueOf(status.name),
    emailVerified = emailVerified,
    suspendedAt = suspendedAt?.toString(),
    suspendedUntil = suspendedUntil?.toString(),
    suspensionReason = suspensionReason,
    roles = roles,
    createdAt = createdAt.toString(),
)

fun CursorPage<MemberSummaryView>.toGql(): GqlMemberConnection = GqlMemberConnection(
    edges = edges.map { GqlMemberEdge(cursor = it.cursor, node = it.node.toGql()) },
    pageInfo = GqlPageInfo(
        hasNextPage = pageInfo.hasNextPage,
        hasPreviousPage = pageInfo.hasPreviousPage,
        startCursor = pageInfo.startCursor,
        endCursor = pageInfo.endCursor,
    ),
    totalCount = totalCount?.toInt(),
)

fun MemberSummaryView.toGql(): GqlMemberSummary = GqlMemberSummary(
    id = memberId,
    nickname = nickname,
    status = GqlMemberStatus.valueOf(status.name),
    roles = roles,
    createdAt = createdAt.toString(),
)

fun RoleInfoView.toGql(): GqlRoleInfo = GqlRoleInfo(code = code, name = name, description = description)
