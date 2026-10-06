package org.around.ieuri.tourism.application.shared.provided.exception

import org.around.ieuri.auth.application.exception.InactiveMemberException
import org.around.ieuri.auth.application.exception.InvalidCredentialsException
import org.around.ieuri.auth.application.exception.MemberAlreadyRegisteredException
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.RoleNotFoundException
import org.around.ieuri.auth.application.exception.SelfDemotionException
import org.around.ieuri.auth.application.exception.SnsVerificationFailedException
import org.around.ieuri.auth.application.exception.UserFacingException
import org.around.ieuri.auth.application.exception.VerificationNotFoundException
import org.around.ieuri.auth.application.exception.VerificationRateLimitedException
import org.around.ieuri.auth.infrastructure.adapter.`in`.graphql.toUserErrorOrNull
import org.around.ieuri.common.security.UnauthorizedException
import org.around.ieuri.tourism.adapter.graphql.support.toReactionUserErrorOrNull
import org.around.ieuri.tourism.application.reaction.provided.exception.SpotNotFoundException
import org.around.ieuri.tourism.domain.shared.ReactionUserException
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 예외 taxonomy 불변식(SPEC-0005 AC-42) — 인증 실패는 userErrors[]로 변환되지 않는다.
 * - 클라이언트의 자동 토큰 갱신은 최상위 errors[]에서만 트리거된다. 이 규약을 강제하는 장치는 이 테스트뿐이다.
 */
class UserErrorTaxonomyTest {

    /** userErrors[]에 실리면 안 되는 세션 상태 code. */
    private val sessionStateCodes = setOf("UNAUTHENTICATED", "UNAUTHORIZED", "TOKEN_EXPIRED")

    /** tourism의 userErrors 대상 예외 전체(ReactionUserException 하위). */
    private val reactionUserExceptions: List<ReactionUserException> = listOf(
        SpotNotFoundException(),
    )

    /** auth의 userErrors 대상 예외 전체(UserFacingException 하위). */
    private val userFacingExceptions: List<UserFacingException> = listOf(
        MemberAlreadyRegisteredException("m"),
        VerificationNotFoundException("m"),
        VerificationRateLimitedException("m"),
        MemberNotFoundException("m"),
        InactiveMemberException("m"),
        SnsVerificationFailedException("m"),
        RoleNotFoundException("m"),
        SelfDemotionException("m"),
        InvalidCredentialsException("m"),
    )

    @Test
    fun `reactToSpot의 userErrors에는 인증 실패 code가 없다`() {
        reactionUserExceptions.forEach { e ->
            val code = toReactionUserErrorOrNull(e)?.code
            assertTrue(
                code !in sessionStateCodes,
                "${e::class.simpleName}이 userErrors로 세션 상태 code($code)를 노출한다 — 인증 실패는 최상위 errors[]로 가야 한다",
            )
        }
    }

    @Test
    fun `auth 뮤테이션의 userErrors에는 인증 실패 code가 없다`() {
        userFacingExceptions.forEach { e ->
            val code = toUserErrorOrNull(e)?.code
            assertTrue(
                code !in sessionStateCodes,
                "${e::class.simpleName}이 userErrors로 세션 상태 code($code)를 노출한다 — 인증 실패는 최상위 errors[]로 가야 한다",
            )
        }
    }

    /** 인증 실패 예외는 두 변환기 어디에도 걸리지 않아야 한다 — null이면 호출부가 최상위 errors[]로 재던진다. */
    @Test
    fun `UnauthorizedException은 어느 경로에서도 UserError로 변환되지 않는다`() {
        val e = UnauthorizedException("인증이 필요합니다.")
        assertNull(toReactionUserErrorOrNull(e), "인증 실패가 reactToSpot의 userErrors로 변환됐다")
        assertNull(toUserErrorOrNull(e), "인증 실패가 auth 뮤테이션의 userErrors로 변환됐다")
    }
}
