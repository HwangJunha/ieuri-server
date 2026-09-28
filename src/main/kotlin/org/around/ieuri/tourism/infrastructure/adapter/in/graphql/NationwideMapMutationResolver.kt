package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.application.port.`in`.command.NationwideMapCommandUseCase
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.common.graphql.generated.types.CreateMapShareLinkPayload as GqlCreateMapShareLinkPayload
import org.around.ieuri.common.graphql.generated.types.MapShareLink as GqlMapShareLink
import org.around.ieuri.common.graphql.generated.types.RecordVisitsInput as GqlRecordVisitsInput
import org.around.ieuri.common.graphql.generated.types.RemoveVisitInput as GqlRemoveVisitInput
import org.around.ieuri.common.graphql.generated.types.RevokeMapShareLinkInput as GqlRevokeMapShareLinkInput
import org.around.ieuri.common.graphql.generated.types.RevokeMapShareLinkPayload as GqlRevokeMapShareLinkPayload
import org.around.ieuri.common.graphql.generated.types.UserError as GqlUserError

/**
 * 전국 지도 쓰기 리졸버(SPEC-0030 R3·R4·R12·R14).
 *
 * **네 뮤테이션 모두 인증 필수다.** 방문 기록과 공유 링크는 회원 본인의 것이며, 대상 회원을
 * 인자로 받지 않는다 — 누구의 기록인지는 인증 컨텍스트가 정한다.
 *
 * 인증 실패(`UnauthorizedException`)는 payload가 아니라 최상위 `errors[]`로 나간다 —
 * 리졸버가 잡지 않고 그대로 전파시킨다(SPEC-0005 R15).
 */
@DgsComponent
class NationwideMapMutationResolver(
    private val commandUseCase: NationwideMapCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    @DgsMutation
    fun recordVisits(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument input: GqlRecordVisitsInput,
    ): RecordVisitsResponse {
        val memberId = currentMemberProvider.requireMemberId()
        val result = commandUseCase.recordVisits(input.toCommand(memberId), dfe.domainLocale())

        // 없는 코드가 섞여 있어도 나머지는 기록된다 — 전체를 되돌리면 사진 동기화가
        // 코드 하나 때문에 통째로 실패한다.
        //
        // **오류는 코드마다가 아니라 하나로 묶고 어떤 코드였는지 밝힌다.** 같은 문구를 N개
        // 보내면 클라이언트는 몇 개가 틀렸는지만 알 뿐 무엇이 틀렸는지 모른다. 시군구 코드는
        // 공개된 행정표준코드라 메시지에 담아도 개인정보가 아니다.
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
        val result = commandUseCase.removeVisit(input.toCommand(memberId), dfe.domainLocale())

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
        val link = commandUseCase.createShareLink(memberId)

        return GqlCreateMapShareLinkPayload(
            success = true,
            userErrors = emptyList(),
            shareLink = GqlMapShareLink(shareUrl = link.shareUrl, createdAt = link.createdAt.toString()),
        )
    }

    @DgsMutation
    fun revokeMapShareLink(
        @InputArgument input: GqlRevokeMapShareLinkInput,
    ): GqlRevokeMapShareLinkPayload {
        val memberId = currentMemberProvider.requireMemberId()
        val revoked = commandUseCase.revokeShareLink(memberId, input.shareUrl)

        // 남의 토큰을 넣었을 때와 이미 폐기됐을 때를 구분하지 않는다 — 구분하면 토큰을
        // 넣어 보며 남의 링크가 유효한지 가려낼 수 있다.
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
