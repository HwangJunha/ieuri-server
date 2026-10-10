package org.around.ieuri.auth.adapter.graphql.mapper

import org.around.ieuri.auth.application.member.provided.dto.AgreementInput
import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.RegisterEmailMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.RegisterMemberCommand
import org.around.ieuri.auth.application.member.provided.dto.ResetPasswordCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.graphql.generated.types.AgreementStatus
import org.around.ieuri.common.graphql.generated.types.AuthTokenPayload
import org.around.ieuri.common.graphql.generated.types.LoginEmailInput
import org.around.ieuri.common.graphql.generated.types.LoginSnsInput
import org.around.ieuri.common.graphql.generated.types.RegisterEmailMemberInput
import org.around.ieuri.common.graphql.generated.types.RegisterSnsMemberInput
import org.around.ieuri.common.graphql.generated.types.ResetPasswordInput
import org.around.ieuri.common.graphql.generated.types.TermsCode
import org.around.ieuri.common.pagination.CursorPage
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

/** SNS 로그인 입력 → Command. 접속지는 리졸버가 요청에서 읽어 넘긴다. */
fun LoginSnsInput.toCommand(ipAddress: String?): LoginCommand = LoginCommand(
    provider = Provider.valueOf(provider.name),
    idToken = idToken,
    authorizationCode = authorizationCode,
    ipAddress = ipAddress,
)

/** 자체(EMAIL) 로그인 입력 → Command. */
fun LoginEmailInput.toCommand(ipAddress: String?): LoginEmailCommand = LoginEmailCommand(
    email = email,
    password = password,
    ipAddress = ipAddress,
)

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
