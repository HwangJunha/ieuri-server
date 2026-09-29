package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.generated.types.ReactToSpotInput
import org.around.ieuri.common.graphql.generated.types.ReactToSpotPayload
import org.around.ieuri.common.graphql.generated.types.UserError
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.command.ReactToSpotCommand
import org.around.ieuri.tourism.application.port.`in`.command.ReactToSpotUseCase
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.infrastructure.locale.domainLocale

/**
 * 좋아요/싫어요 기록 뮤테이션 리졸버. 언어는 Accept-Language 헤더에서, memberId는 SecurityContext에서 온다.
 * - 비로그인은 payload가 아니라 최상위 errors[]로 나간다(SPEC-0005 R15) — 클라이언트 토큰 갱신이 거기서만 트리거된다.
 * - 그 외 예상 실패(SPOT_NOT_FOUND/INVALID_INPUT)는 userErrors로 변환한다.
 */
@DgsComponent
class ReactToSpotResolver(
    private val reactToSpotUseCase: ReactToSpotUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    fun reactToSpot(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument input: ReactToSpotInput,
    ): ReactToSpotPayload = withReactionUserErrors(
        onError = { failure(it) },
    ) {
        // 비로그인 → UnauthorizedException → 최상위 errors[].
        val memberId = currentMemberProvider.requireMemberId()

        reactToSpotUseCase.react(
            command = ReactToSpotCommand(
                catalogId = input.catalogId,
                // GraphQL enum → 도메인 enum. 이름이 어긋나면 valueOf가 즉시 실패한다.
                reaction = ReactionType.valueOf(input.reaction.name),
            ),
            memberId = memberId,
            // 반응은 활성 언어별로 저장된다(SPEC-0029 R-13).
            language = dfe.domainLocale(),
        )
        ReactToSpotPayload(success = true, userErrors = emptyList())
    }

    private fun failure(userErrors: List<UserError>): ReactToSpotPayload = ReactToSpotPayload(success = false, userErrors = userErrors)
}
