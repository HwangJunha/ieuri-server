package org.around.ieuri.tourism.application.exception

import org.around.ieuri.auth.application.exception.EmailNotVerifiedException
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
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.toReactionUserErrorOrNull
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 예외 taxonomy 불변식(SPEC-0005 AC-42) — **인증 실패는 userErrors[]로 변환되지 않는다.**
 *
 * 클라이언트의 자동 토큰 갱신은 최상위 errors[]를 보는 링크 계층에서 일어나므로, 인증 실패가 payload의
 * userErrors[]에 담기면 갱신이 아예 트리거되지 않는다(k-trip-spot#22 실기기 재현).
 *
 * 이 규약을 강제할 스키마 수단이 없다(userErrors의 code는 SDL상 String!). 따라서 다음 뮤테이션이 같은
 * 실수를 반복하는 것을 막는 **유일한 자동 장치가 이 테스트**다 — 회귀 방지 장치이지 특정 변경의 확인
 * 절차가 아니다. 새 예외가 인증 실패를 userErrors에 담으면 여기서 깨져야 한다.
 *
 * 검증 대상 toReactionUserErrorOrNull/toUserErrorOrNull은 Throwable → UserError?의 순수 함수로,
 * 프레임워크도 컨텍스트도 요구하지 않는다.
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
        EmailNotVerifiedException("m"),
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

    /**
     * 인증 실패 예외 자체가 두 변환기 어디에도 걸리지 않아야 한다(null → 호출부가 재던져 최상위 errors[]로).
     * reactToSpot이 requireMemberId()로 던지는 것이 바로 이 예외다.
     */
    @Test
    fun `UnauthorizedException은 어느 경로에서도 UserError로 변환되지 않는다`() {
        val e = UnauthorizedException("인증이 필요합니다.")
        assertNull(toReactionUserErrorOrNull(e), "인증 실패가 reactToSpot의 userErrors로 변환됐다")
        assertNull(toUserErrorOrNull(e), "인증 실패가 auth 뮤테이션의 userErrors로 변환됐다")
    }
}
