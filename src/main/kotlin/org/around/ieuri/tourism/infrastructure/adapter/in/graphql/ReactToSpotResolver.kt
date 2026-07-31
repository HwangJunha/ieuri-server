package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.exception.UnauthenticatedReactionException
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.locale.LocaleContext
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.graphql.generated.types.ReactToSpotInput
import org.around.ieuri.common.graphql.generated.types.ReactToSpotPayload
import org.around.ieuri.common.graphql.generated.types.UserError

/**
 * 좋아요/싫어요 기록 GraphQL 뮤테이션 리졸버(인바운드 어댑터, 쓰기). 언어는 LocaleContext(Accept-Language)에서,
 * memberId는 SecurityContext에서 온다. 비로그인은 UNAUTHENTICATED userError로 응답한다(AC-14, 최상위 error 아님).
 * 예상 실패는 withReactionUserErrors로 payload의 userErrors로 변환한다(BaseResponse 규약).
 */
@DgsComponent
class ReactToSpotResolver(
    private val reactToSpotUseCase: ReactToSpotUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val localeContext: LocaleContext,
) {
    @DgsMutation
    fun reactToSpot(@InputArgument input: ReactToSpotInput): ReactToSpotPayload = withReactionUserErrors(
        onError = { failure(it) },
    ) {
        // 비로그인 → userErrors(UNAUTHENTICATED). requireMemberId()는 최상위 error를 던지므로 쓰지 않는다.
        val memberId = currentMemberProvider.currentMemberId()
            ?: throw UnauthenticatedReactionException()

        reactToSpotUseCase.react(
            command = ReactToSpotCommand(
                spotId = input.spotId,
                // GraphQL enum → 도메인 enum. 이름 체계가 어긋나면 valueOf가 즉시 실패한다.
                reaction = ReactionType.valueOf(input.reaction.name),
            ),
            memberId = memberId,
            locale = localeContext.locale,
        )
        ReactToSpotPayload(success = true, userErrors = emptyList())
    }

    private fun failure(userErrors: List<UserError>): ReactToSpotPayload =
        ReactToSpotPayload(success = false, userErrors = userErrors)
}
