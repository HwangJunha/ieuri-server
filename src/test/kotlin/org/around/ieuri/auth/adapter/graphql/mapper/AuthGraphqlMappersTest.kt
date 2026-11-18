package org.around.ieuri.auth.adapter.graphql.mapper

import org.around.ieuri.auth.application.member.provided.dto.AgreementStatusView
import org.around.ieuri.auth.application.member.provided.dto.MemberDetailView
import org.around.ieuri.auth.application.member.provided.dto.MemberSummaryView
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.application.token.provided.dto.TokenPairResult
import org.around.ieuri.auth.domain.member.MemberStatus
import org.around.ieuri.auth.domain.member.Provider
import org.around.ieuri.common.graphql.generated.types.AgreeToRequiredTermsInput
import org.around.ieuri.common.graphql.generated.types.AgreementInput
import org.around.ieuri.common.graphql.generated.types.AssignRoleInput
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
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.CursorPageInfo
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.around.ieuri.common.graphql.generated.types.MemberStatus as GqlMemberStatus
import org.around.ieuri.common.graphql.generated.types.Provider as GqlProvider

/** auth의 GraphQL 입력 → Command, read DTO → 응답 변환(POL-033). 필드마다 다른 값을 줘 뒤바뀌면 드러나게 한다. */
class AuthGraphqlMappersTest {

    private val agreements = listOf(AgreementInput(TermsCode.SERVICE, true), AgreementInput(TermsCode.MARKETING, false))

    // ── 입력 → Command ──

    @Test
    fun `SNS 가입 입력이 Command로 옮겨진다`() {
        val command = RegisterSnsMemberInput(
            provider = GqlProvider.KAKAO,
            idToken = "id-token-값",
            email = "typed@example.com",
            ageVerified = true,
            agreements = agreements,
            authorizationCode = "auth-code-값",
            nickname = "닉네임-값",
        ).toCommand()

        assertEquals(Provider.KAKAO, command.provider)
        assertEquals("id-token-값", command.idToken)
        assertEquals("typed@example.com", command.submittedEmail)
        assertEquals(true, command.ageVerified)
        assertEquals(listOf("SERVICE", "MARKETING"), command.agreements.map { it.termsCode })
        assertEquals(listOf(true, false), command.agreements.map { it.agreed })
        assertEquals("auth-code-값", command.authorizationCode)
        assertEquals("닉네임-값", command.nickname)
    }

    @Test
    fun `SNS 가입 입력의 선택 값이 없으면 null로 옮겨진다`() {
        val command = RegisterSnsMemberInput(
            provider = GqlProvider.APPLE,
            idToken = "id-token-값",
            email = "typed@example.com",
            ageVerified = false,
            agreements = emptyList(),
            authorizationCode = null,
            nickname = null,
        ).toCommand()

        assertEquals(Provider.APPLE, command.provider)
        assertEquals(false, command.ageVerified)
        assertEquals(emptyList(), command.agreements)
        assertNull(command.authorizationCode)
        assertNull(command.nickname)
    }

    @Test
    fun `provider는 네 값 모두 같은 이름으로 옮겨진다`() {
        GqlProvider.entries.forEach { provider ->
            val command = RegisterSnsMemberInput(provider = provider, idToken = "t", email = "a@example.com", ageVerified = true, agreements = emptyList()).toCommand()

            assertEquals(provider.name, command.provider.name)
        }
        assertEquals(Provider.entries.map { it.name }, GqlProvider.entries.map { it.name })
    }

    @Test
    fun `자체 가입 입력이 Command로 옮겨진다 - 이메일과 비밀번호가 섞이지 않는다`() {
        val command = RegisterEmailMemberInput(
            email = "self@example.com",
            password = "Passw0rd!-distinct",
            ageVerified = true,
            agreements = agreements,
        ).toCommand()

        assertEquals("self@example.com", command.email)
        assertEquals("Passw0rd!-distinct", command.password)
        assertEquals(true, command.ageVerified)
        assertEquals(listOf("SERVICE", "MARKETING"), command.agreements.map { it.termsCode })
        assertEquals(listOf(true, false), command.agreements.map { it.agreed })
    }

    /** 접속지는 Command에 담지 않는다 — 리졸버가 유스케이스에 따로 넘긴다. */
    @Test
    fun `SNS 로그인 입력이 Command로 옮겨진다 - 토큰과 인가 코드가 섞이지 않는다`() {
        val command = LoginSnsInput(
            provider = GqlProvider.GOOGLE,
            idToken = "id-token-값",
            authorizationCode = "auth-code-값",
        ).toCommand()

        assertEquals(Provider.GOOGLE, command.provider)
        assertEquals("id-token-값", command.idToken)
        assertEquals("auth-code-값", command.authorizationCode)
    }

    @Test
    fun `SNS 로그인 입력의 인가 코드가 없으면 null로 옮겨진다`() {
        val command = LoginSnsInput(provider = GqlProvider.APPLE, idToken = "id-token-값", authorizationCode = null).toCommand()

        assertEquals(Provider.APPLE, command.provider)
        assertEquals("id-token-값", command.idToken)
        assertNull(command.authorizationCode)
    }

    @Test
    fun `SNS 로그인의 provider는 네 값 모두 같은 이름으로 옮겨진다`() {
        GqlProvider.entries.forEach { provider ->
            assertEquals(provider.name, LoginSnsInput(provider = provider, idToken = "t").toCommand().provider.name)
        }
    }

    @Test
    fun `자체 로그인 입력이 Command로 옮겨진다 - 이메일과 비밀번호가 섞이지 않는다`() {
        val command = LoginEmailInput(email = "login@example.com", password = "비밀번호-값").toCommand()

        assertEquals("login@example.com", command.email)
        assertEquals("비밀번호-값", command.password)
    }

    @Test
    fun `재발급과 로그아웃 입력의 refresh 토큰이 Command로 옮겨진다`() {
        assertEquals("refresh-재발급-값", RefreshTokenInput(refreshToken = "refresh-재발급-값").toCommand().refreshToken)
        assertEquals("refresh-로그아웃-값", LogoutInput(refreshToken = "refresh-로그아웃-값").toCommand().refreshToken)
    }

    // ── 이메일 인증·변경 ──

    @Test
    fun `이메일 인증 입력이 Command로 옮겨진다 - 핸들은 UUID로 읽고 코드와 섞이지 않는다`() {
        val command = VerifyEmailInput(verificationId = "11111111-2222-3333-4444-555555555555", code = "481516").toCommand()

        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), command.verificationPublicId)
        assertEquals("481516", command.code)
    }

    @Test
    fun `인증 코드 재발송 입력의 핸들이 UUID로 옮겨진다`() {
        val command = ResendVerificationInput(verificationId = "22222222-3333-4444-5555-666666666666").toCommand()

        assertEquals(UUID.fromString("22222222-3333-4444-5555-666666666666"), command.verificationPublicId)
    }

    @Test
    fun `인증 주소 정정과 이메일 변경 입력의 새 주소가 다듬지 않은 채 Command로 옮겨진다`() {
        // 정규화는 도메인(Email)이 한다 — 변환은 값을 건드리지 않는다.
        assertEquals(" Retarget@Example.com ", RetargetEmailVerificationInput(newEmail = " Retarget@Example.com ").toCommand().newEmail)
        assertEquals(" Change@Example.com ", ChangeEmailInput(newEmail = " Change@Example.com ").toCommand().newEmail)
    }

    @Test
    fun `이메일 변경 확인 입력이 Command로 옮겨진다 - 핸들은 UUID로 읽고 코드와 섞이지 않는다`() {
        val command = ConfirmEmailChangeInput(verificationId = "33333333-4444-5555-6666-777777777777", code = "162342").toCommand()

        assertEquals(UUID.fromString("33333333-4444-5555-6666-777777777777"), command.verificationPublicId)
        assertEquals("162342", command.code)
    }

    @Test
    fun `이메일 변경 재발송 입력의 핸들이 UUID로 옮겨진다`() {
        val command = ResendEmailChangeInput(verificationId = "44444444-5555-6666-7777-888888888888").toCommand()

        assertEquals(UUID.fromString("44444444-5555-6666-7777-888888888888"), command.verificationPublicId)
    }

    @Test
    fun `UUID가 아닌 핸들은 네 입력 모두 예외다`() {
        assertFailsWith<IllegalArgumentException> { VerifyEmailInput(verificationId = "not-a-uuid", code = "481516").toCommand() }
        assertFailsWith<IllegalArgumentException> { ResendVerificationInput(verificationId = "not-a-uuid").toCommand() }
        assertFailsWith<IllegalArgumentException> { ConfirmEmailChangeInput(verificationId = "not-a-uuid", code = "481516").toCommand() }
        assertFailsWith<IllegalArgumentException> { ResendEmailChangeInput(verificationId = "not-a-uuid").toCommand() }
    }

    @Test
    fun `비밀번호 재설정 요청 입력의 이메일이 Command로 옮겨진다`() {
        assertEquals("forgot@example.com", RequestPasswordResetInput(email = "forgot@example.com").toCommand().email)
    }

    // ── 프로필 ──

    @Test
    fun `닉네임 변경 입력이 다듬지 않은 채 Command로 옮겨진다`() {
        assertEquals("  여행하는곰  ", ChangeNicknameInput(nickname = "  여행하는곰  ").toCommand().nickname)
    }

    @Test
    fun `프로필 이미지 업로드 URL 입력이 Command로 옮겨진다 - 크기는 Long으로 넓힌다`() {
        val command = CreateProfileImageUploadUrlInput(contentType = "image/webp", contentLength = 2_147_483_647).toCommand()

        assertEquals("image/webp", command.contentType)
        assertEquals(2_147_483_647L, command.contentLength)
    }

    @Test
    fun `프로필 이미지 업로드 확인 입력의 오브젝트 키가 Command로 옮겨진다`() {
        assertEquals("pending/profile/7/키-값", ConfirmProfileImageUploadInput(objectKey = "pending/profile/7/키-값").toCommand().objectKey)
    }

    // ── 약관 동의 ──

    @Test
    fun `약관 토글 입력이 Command로 옮겨진다 - 코드는 enum 이름이다`() {
        val on = UpdateAgreementInput(termsCode = TermsCode.MARKETING, agreed = true).toCommand()
        val off = UpdateAgreementInput(termsCode = TermsCode.LOCATION, agreed = false).toCommand()

        assertEquals("MARKETING", on.termsCode)
        assertEquals(true, on.agreed)
        assertEquals("LOCATION", off.termsCode)
        assertEquals(false, off.agreed)
    }

    @Test
    fun `약관 코드는 모든 값이 같은 이름으로 옮겨진다`() {
        TermsCode.entries.forEach { code ->
            assertEquals(code.name, UpdateAgreementInput(termsCode = code, agreed = true).toCommand().termsCode)
        }
    }

    @Test
    fun `필수 약관 동의 입력의 코드가 순서대로 이름으로 옮겨진다`() {
        val command = AgreeToRequiredTermsInput(termsCodes = listOf(TermsCode.PRIVACY, TermsCode.SERVICE, TermsCode.PRIVACY)).toCommand()
        val empty = AgreeToRequiredTermsInput(termsCodes = emptyList()).toCommand()

        // 중복 제거는 도메인이 한다 — 변환은 받은 대로 옮긴다.
        assertEquals(listOf("PRIVACY", "SERVICE", "PRIVACY"), command.termsCodes)
        assertEquals(emptyList(), empty.termsCodes)
    }

    // ── 정지·복구·역할(관리자) ──

    @Test
    fun `정지 입력이 Command로 옮겨진다 - 만료 시각은 ISO 문자열을 Instant로 읽는다`() {
        val command = SuspendMemberInput(memberId = 41L, reason = "사유-값", suspendedUntil = "2026-11-12T13:14:15Z").toCommand()

        assertEquals(41L, command.memberId)
        assertEquals("사유-값", command.reason)
        assertEquals(Instant.parse("2026-11-12T13:14:15Z"), command.suspendedUntil)
    }

    @Test
    fun `정지 입력의 만료 시각이 없으면 null이다 - 무기한 정지`() {
        val command = SuspendMemberInput(memberId = 42L, reason = "사유-값", suspendedUntil = null).toCommand()

        assertEquals(42L, command.memberId)
        assertNull(command.suspendedUntil)
    }

    @Test
    fun `ISO-8601이 아닌 만료 시각은 예외다`() {
        assertFailsWith<DateTimeParseException> { SuspendMemberInput(memberId = 43L, reason = "사유-값", suspendedUntil = "2026-11-12").toCommand() }
    }

    @Test
    fun `복구 입력의 회원 id가 Command로 옮겨진다`() {
        assertEquals(44L, ReactivateMemberInput(memberId = 44L).toCommand().memberId)
    }

    /** 수행자는 Command에 없다 — 리졸버가 인증 컨텍스트에서 읽어 따로 넘긴다. */
    @Test
    fun `역할 부여 입력의 memberId는 대상 회원으로 옮겨진다`() {
        val command = AssignRoleInput(memberId = 51L, roleCode = "ADMIN").toCommand()

        assertEquals(51L, command.targetMemberId)
        assertEquals("ADMIN", command.roleCode)
    }

    @Test
    fun `역할 회수 입력의 memberId는 대상 회원으로 옮겨진다`() {
        val command = RevokeRoleInput(memberId = 52L, roleCode = "SUPER_ADMIN").toCommand()

        assertEquals(52L, command.targetMemberId)
        assertEquals("SUPER_ADMIN", command.roleCode)
    }

    @Test
    fun `비밀번호 재설정 입력이 Command로 옮겨진다 - 이메일과 코드와 새 비밀번호가 섞이지 않는다`() {
        val command = ResetPasswordInput(email = "reset@example.com", code = "481516", newPassword = "NewPassw0rd!-distinct").toCommand()

        assertEquals("reset@example.com", command.email)
        assertEquals("481516", command.code)
        assertEquals("NewPassw0rd!-distinct", command.newPassword)
    }

    // ── 토큰 ──

    @Test
    fun `토큰 쌍이 성공 payload로 옮겨진다 - 두 토큰과 두 만료가 섞이지 않는다`() {
        val payload = TokenPairResult(
            accessToken = "access-값",
            refreshToken = "refresh-값",
            tokenType = "Bearer",
            accessTokenExpiresInSeconds = 900L,
            refreshTokenExpiresInSeconds = 1_209_600L,
        ).toPayload()

        assertEquals(true, payload.success)
        assertEquals(emptyList(), payload.userErrors)
        assertEquals("access-값", payload.accessToken)
        assertEquals("refresh-값", payload.refreshToken)
        assertEquals("Bearer", payload.tokenType)
        assertEquals(900, payload.accessTokenExpiresIn)
        assertEquals(1_209_600, payload.refreshTokenExpiresIn)
    }

    // ── 약관 동의 상태 ──

    @Test
    fun `약관 동의 상태가 응답으로 옮겨진다`() {
        val gql = AgreementStatusView(
            termsCode = "LOCATION",
            required = true,
            agreed = false,
            agreedAt = Instant.parse("2026-10-05T03:04:05Z"),
            version = "2.1",
        ).toGql()

        assertEquals(TermsCode.LOCATION, gql.termsCode)
        assertEquals(true, gql.required)
        assertEquals(false, gql.agreed)
        assertEquals("2026-10-05T03:04:05Z", gql.agreedAt)
        assertEquals("2.1", gql.version)
    }

    @Test
    fun `동의 시각이 없으면 null이고 필수와 동의 여부는 섞이지 않는다`() {
        val gql = AgreementStatusView(termsCode = "MARKETING", required = false, agreed = true, agreedAt = null, version = "1.0").toGql()

        assertEquals(TermsCode.MARKETING, gql.termsCode)
        assertEquals(false, gql.required)
        assertEquals(true, gql.agreed)
        assertNull(gql.agreedAt)
    }

    /** 건너뛰지 않고 실패한다 — 건너뛰면 필수 약관 게이트가 풀린다. */
    @Test
    fun `스키마 enum에 없는 약관 코드는 예외다`() {
        assertFailsWith<IllegalArgumentException> {
            AgreementStatusView(termsCode = "UNKNOWN", required = true, agreed = false, agreedAt = null, version = "1.0").toGql()
        }
    }

    // ── 회원 상세·목록 ──

    @Test
    fun `회원 상세가 응답으로 옮겨진다 - 시각 셋이 섞이지 않는다`() {
        val gql = MemberDetailView(
            memberId = 11L,
            email = "detail@example.com",
            nickname = "닉네임-값",
            profileImageUrl = "images/profile/11/a",
            status = MemberStatus.SUSPENDED,
            emailVerified = true,
            suspendedAt = Instant.parse("2026-10-01T01:00:00Z"),
            suspendedUntil = Instant.parse("2026-10-02T02:00:00Z"),
            suspensionReason = "사유-값",
            roles = listOf("ADMIN", "USER"),
            createdAt = Instant.parse("2026-09-03T03:00:00.123Z"),
        ).toGql()

        assertEquals(11L, gql.id)
        assertEquals("detail@example.com", gql.email)
        assertEquals("닉네임-값", gql.nickname)
        assertEquals("images/profile/11/a", gql.profileImageUrl)
        assertEquals(GqlMemberStatus.SUSPENDED, gql.status)
        assertEquals(true, gql.emailVerified)
        assertEquals("2026-10-01T01:00:00Z", gql.suspendedAt)
        assertEquals("2026-10-02T02:00:00Z", gql.suspendedUntil)
        assertEquals("사유-값", gql.suspensionReason)
        assertEquals(listOf("ADMIN", "USER"), gql.roles)
        assertEquals("2026-09-03T03:00:00.123Z", gql.createdAt)
    }

    @Test
    fun `회원 상세의 null 값은 null로 옮겨진다`() {
        val gql = MemberDetailView(
            memberId = 12L,
            email = "detail@example.com",
            nickname = "닉네임-값",
            profileImageUrl = null,
            status = MemberStatus.ACTIVE,
            emailVerified = false,
            suspendedAt = null,
            suspendedUntil = null,
            suspensionReason = null,
            roles = emptyList(),
            createdAt = Instant.parse("2026-09-03T03:00:00Z"),
        ).toGql()

        assertEquals(GqlMemberStatus.ACTIVE, gql.status)
        assertEquals(false, gql.emailVerified)
        assertNull(gql.profileImageUrl)
        assertNull(gql.suspendedAt)
        assertNull(gql.suspendedUntil)
        assertNull(gql.suspensionReason)
        assertEquals(emptyList(), gql.roles)
    }

    private fun summary(id: Long, nickname: String, status: MemberStatus, createdAt: String) = MemberSummaryView(
        memberId = id,
        nickname = nickname,
        status = status,
        roles = listOf("역할-$id"),
        createdAt = Instant.parse(createdAt),
    )

    @Test
    fun `회원 목록 항목이 응답으로 옮겨진다`() {
        val gql = summary(21L, "닉네임-값", MemberStatus.SUSPENDED, "2026-09-04T04:00:00Z").toGql()

        assertEquals(21L, gql.id)
        assertEquals("닉네임-값", gql.nickname)
        assertEquals(GqlMemberStatus.SUSPENDED, gql.status)
        assertEquals(listOf("역할-21"), gql.roles)
        assertEquals("2026-09-04T04:00:00Z", gql.createdAt)
    }

    @Test
    fun `회원 상태는 모든 값이 같은 이름으로 옮겨진다`() {
        MemberStatus.entries.forEach { status ->
            assertEquals(status.name, summary(1L, "닉네임", status, "2026-09-04T04:00:00Z").toGql().status.name)
        }
    }

    @Test
    fun `회원 페이지가 connection으로 옮겨진다 - 커서와 페이지 정보가 섞이지 않는다`() {
        val connection = CursorPage(
            edges = listOf(
                CursorEdge("커서-1", summary(31L, "첫째", MemberStatus.ACTIVE, "2026-09-05T05:00:00Z")),
                CursorEdge("커서-2", summary(32L, "둘째", MemberStatus.SUSPENDED, "2026-09-06T06:00:00Z")),
            ),
            pageInfo = CursorPageInfo(hasNextPage = true, hasPreviousPage = false, startCursor = "시작-커서", endCursor = "끝-커서"),
            totalCount = 57L,
        ).toGql()

        assertEquals(listOf("커서-1", "커서-2"), connection.edges.map { it.cursor })
        assertEquals(listOf(31L, 32L), connection.edges.map { it.node.id })
        assertEquals(listOf("첫째", "둘째"), connection.edges.map { it.node.nickname })
        assertEquals(listOf(GqlMemberStatus.ACTIVE, GqlMemberStatus.SUSPENDED), connection.edges.map { it.node.status })
        assertEquals(true, connection.pageInfo.hasNextPage)
        assertEquals(false, connection.pageInfo.hasPreviousPage)
        assertEquals("시작-커서", connection.pageInfo.startCursor)
        assertEquals("끝-커서", connection.pageInfo.endCursor)
        assertEquals(57, connection.totalCount)
    }

    @Test
    fun `빈 회원 페이지는 빈 edges와 null 커서로 옮겨진다`() {
        val connection = CursorPage<MemberSummaryView>(
            edges = emptyList(),
            pageInfo = CursorPageInfo(hasNextPage = false, hasPreviousPage = true, startCursor = null, endCursor = null),
            totalCount = null,
        ).toGql()

        assertEquals(emptyList(), connection.edges)
        assertEquals(false, connection.pageInfo.hasNextPage)
        assertEquals(true, connection.pageInfo.hasPreviousPage)
        assertNull(connection.pageInfo.startCursor)
        assertNull(connection.pageInfo.endCursor)
        assertNull(connection.totalCount)
    }

    // ── 역할 ──

    @Test
    fun `역할 정보가 응답으로 옮겨진다`() {
        val gql = RoleInfoView(code = "ADMIN", name = "관리자-이름", description = "설명-값").toGql()
        val bare = RoleInfoView(code = "USER", name = "사용자-이름", description = null).toGql()

        assertEquals("ADMIN", gql.code)
        assertEquals("관리자-이름", gql.name)
        assertEquals("설명-값", gql.description)
        assertEquals("USER", bare.code)
        assertNull(bare.description)
    }
}
