package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.graphql.generated.types.ReactToSpotInput
import org.around.ieuri.common.graphql.generated.types.ReactToSpotPayload
import org.around.ieuri.common.graphql.generated.types.UserError

/**
 * 좋아요/싫어요 기록 GraphQL 뮤테이션 리졸버(인바운드 어댑터, 쓰기). 언어는 LocaleContext(Accept-Language)에서,
 * memberId는 SecurityContext에서 온다. 비로그인은 requireMemberId()가 던지는 UnauthorizedException이
 * 최상위 errors[]로 전파된다(SPEC-0005 R15) — 클라이언트의 자동 토큰 갱신이 최상위 errors[]에서만
 * 트리거되므로 인증 실패는 payload에 담지 않는다.
 * 그 외 예상 실패(SPOT_NOT_FOUND/INVALID_INPUT)는 withReactionUserErrors로 userErrors로 변환한다(BaseResponse 규약).
 */
@DgsComponent
class ReactToSpotResolver(
    private val reactToSpotUseCase: ReactToSpotUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    fun reactToSpot(@InputArgument input: ReactToSpotInput): ReactToSpotPayload = withReactionUserErrors(
        onError = { failure(it) },
    ) {
        // 비로그인 → UnauthorizedException. ReactionUserException이 아니므로 withReactionUserErrors가
        // 붙잡지 않고 최상위 errors[]로 전파된다(auth 도메인 리졸버와 동일한 경로).
        val memberId = currentMemberProvider.requireMemberId()

        reactToSpotUseCase.react(
            command = ReactToSpotCommand(
                spotId = input.spotId,
                // GraphQL enum → 도메인 enum. 이름 체계가 어긋나면 valueOf가 즉시 실패한다.
                reaction = ReactionType.valueOf(input.reaction.name),
            ),
            memberId = memberId,
        )
        ReactToSpotPayload(success = true, userErrors = emptyList())
    }

    private fun failure(userErrors: List<UserError>): ReactToSpotPayload =
        ReactToSpotPayload(success = false, userErrors = userErrors)
}
