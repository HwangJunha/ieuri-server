package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.adapter.graphql.mapper.toCommand
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.support.RecordVisitsResponse
import org.around.ieuri.tourism.adapter.graphql.support.RemoveVisitResponse
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.footprint.provided.FootprintCommandUseCase
import org.around.ieuri.common.graphql.generated.types.CreateMapShareLinkPayload as GqlCreateMapShareLinkPayload
import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput as GqlRecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput as GqlRemoveVisitInput
import org.around.ieuri.common.graphql.generated.types.RevokeMapShareLinkInput as GqlRevokeMapShareLinkInput
import org.around.ieuri.common.graphql.generated.types.RevokeMapShareLinkPayload as GqlRevokeMapShareLinkPayload
import org.around.ieuri.common.graphql.generated.types.UserError as GqlUserError

/**
 * 전국 지도 쓰기 리졸버(SPEC-0030 R3·R4·R12·R14). 네 뮤테이션 모두 인증 필수다.
 * - 대상 회원을 인자로 받지 않는다 — 인증 컨텍스트가 정한다.
 * - 인증 실패는 payload가 아니라 최상위 `errors[]`로 나간다(SPEC-0005 R15).
 */
@DgsComponent
class NationwideMapMutationResolver(
    private val footprintCommandUseCase: FootprintCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsMutation
    fun recordVisits(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument input: GqlRecordVisitsInput,
    ): RecordVisitsResponse {
        val memberId = currentMemberProvider.requireMemberId()
        val result = footprintCommandUseCase.recordVisits(input.toCommand(), memberId, dfe.domainLocale())

        // 없는 코드가 섞여 있어도 나머지는 기록된다.
        // 오류는 하나로 묶고 어떤 코드였는지 밝힌다(공개 행정코드라 개인정보가 아니다).
        return RecordVisitsResponse(
            success = result.unknownDistrictCodes.isEmpty(),
            userErrors = if (result.unknownDistrictCodes.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    GqlUserError(
                        code = "DISTRICT_NOT_FOUND",
                        message = "존재하지 않는 지역 코드입니다: ${result.unknownDistrictCodes.joinToString()}",
                        field = "districtCodes",
                    ),
                )
            },
            updatedCells = result.updatedCells,
        )
    }

    @DgsMutation
    fun removeVisit(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument input: GqlRemoveVisitInput,
    ): RemoveVisitResponse {
        val memberId = currentMemberProvider.requireMemberId()
        val result = footprintCommandUseCase.removeVisit(input.toCommand(), memberId, dfe.domainLocale())

        return RemoveVisitResponse(
            success = result.updatedCell != null,
            userErrors = if (result.updatedCell == null) {
                listOf(
                    GqlUserError(
                        code = "MAP_CELL_NOT_FOUND",
                        message = "존재하지 않는 지역입니다.",
                        field = "cellCode",
                    ),
                )
            } else {
                emptyList()
            },
            updatedCell = result.updatedCell,
        )
    }

    @DgsMutation
    fun createMapShareLink(): GqlCreateMapShareLinkPayload {
        val memberId = currentMemberProvider.requireMemberId()
        val link = footprintCommandUseCase.createShareLink(memberId)

        return GqlCreateMapShareLinkPayload(
            success = true,
            userErrors = emptyList(),
            shareLink = link.toGql(),
        )
    }

    @DgsMutation
    fun revokeMapShareLink(
        @InputArgument input: GqlRevokeMapShareLinkInput,
    ): GqlRevokeMapShareLinkPayload {
        val memberId = currentMemberProvider.requireMemberId()
        val revoked = footprintCommandUseCase.revokeShareLink(memberId, input.shareUrl)

        // 남의 토큰과 이미 폐기된 토큰을 구분하지 않는다 — 구분하면 남의 링크 유효성을 떠볼 수 있다.
        return GqlRevokeMapShareLinkPayload(
            success = revoked,
            userErrors = if (revoked) {
                emptyList()
            } else {
                listOf(
                    GqlUserError(
                        code = "SHARE_LINK_NOT_FOUND",
                        message = "더 이상 볼 수 없는 링크입니다.",
                        field = "shareUrl",
                    ),
                )
            },
        )
    }
}
