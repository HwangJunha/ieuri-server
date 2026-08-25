package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import org.around.ieuri.tourism.application.port.out.client.LocationAuditPort
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.common.graphql.appKey
import org.around.ieuri.common.graphql.rejectStaleToken
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.graphql.generated.types.CopyrightType as GqlCopyrightType
import org.around.ieuri.common.graphql.generated.types.LikedSpot as GqlLikedSpot
import org.around.ieuri.common.graphql.generated.types.LikedSpotConnection as GqlLikedSpotConnection
import org.around.ieuri.common.graphql.generated.types.LikedSpotEdge as GqlLikedSpotEdge
import org.around.ieuri.common.graphql.generated.types.PageInfo as GqlPageInfo
import org.around.ieuri.common.graphql.generated.types.Spot as GqlSpot
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.graphql.generated.types.SpotDetail as GqlSpotDetail
import org.around.ieuri.common.graphql.generated.types.SpotImage as GqlSpotImage
import org.around.ieuri.common.graphql.generated.types.SpotInfoItem as GqlSpotInfoItem
import org.around.ieuri.common.graphql.generated.types.SpotIntro as GqlSpotIntro
import org.around.ieuri.common.graphql.generated.types.SpotPage as GqlSpotPage

/**
 * 관광지 조회 GraphQL 쿼리 리졸버(인바운드 어댑터, CQRS 읽기). 언어는 Accept-Language 헤더를
 * 실행 컨텍스트에서 읽어 유스케이스에 넘긴다(GraphQL 인자 아님). read DTO를 GraphQL 응답 타입으로 매핑한다.
 * 쿼리 도메인 오류는 TourismQueryExceptionHandler가 최상위 errors[]로 분류한다(payload 컨벤션 없음).
 */
@DgsComponent
class SpotQueryResolver(
    private val spotQueryUseCase: SpotQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
    private val locationAuditPort: LocationAuditPort,
) {
    @DgsQuery
    fun spots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument categories: List<String>?,
        @InputArgument page: Int?,
        @InputArgument size: Int?,
    ): GqlSpotPage {
        // 만료 토큰을 비로그인으로 강등하지 않는다(R1) — 이미 반응한 항목이 조용히 되살아난다.
        dfe.rejectStaleToken()
        // 좌표를 받는 연산이므로 조회 사실을 남긴다(R19). **좌표는 넘기지 않는다.**
        locationAuditPort.recordAccess(currentMemberProvider.currentMemberId(), "spots")
        val result = spotQueryUseCase.spots(
            SpotsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                categories = toCategorySelection(categories),
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
            ),
            locale = dfe.domainLocale(),
            // 비로그인 허용 쿼리 — memberId가 있으면 이미 반응한 항목을 제외한다(requireMemberId 아님).
            memberId = currentMemberProvider.currentMemberId(),
        )
        return result.toGql()
    }

    /**
     * **순서를 정하지 않는다**(V59) — 서버는 무엇을 보일지만 정하고 배열 순서는 클라이언트가 정한다.
     *
     * 구성 조회가 라벨·아이콘을 이미 담아 오므로 요청 메모에 심어 둔다 — 그러지 않으면
     * label·iconUrl 필드 리졸버가 칩마다 같은 것을 다시 묻는다(칩 8개에 조회 8번).
     */
    @DgsQuery
    fun availableSpotCategories(dfe: DgsDataFetchingEnvironment): List<GqlSpotCategory> {
        val configured = spotQueryUseCase.availableCategories(dfe.appKey(), dfe.domainLocale())
        CategoryDisplayResolver.seed(
            dfe,
            configured.map { CategoryDisplay(it.category, it.label, it.iconUrl) },
        )
        return configured.map {
            GqlSpotCategory(code = it.category.code, label = it.label, iconUrl = it.iconUrl)
        }
    }

    /**
     * 상세 본문(detailCommon2 1콜). gallery·intro·infoItems·likeCount는 **요청된 경우에만** 아래 @DgsData
     * 필드 리졸버가 각자 조회한다 — `{ id title overview }`만 물으면 upstream 콜은 1회다.
     *
     * 생성 타입(GqlSpotDetail)이 아니라 read DTO를 반환한다: 생성 타입은 non-null 필드를 생성자 필수 인자로
     * 요구해서 지연 필드를 비워 둘 수 없다. 지연 필드의 소스로는 read DTO가 맞다(각 필드 리졸버가 여기서 id를 읽는다).
     */
    @DgsQuery
    fun spotDetail(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument id: Long,
    ): SpotDetailView =
        spotQueryUseCase.spotDetail(catalogId = id, locale = dfe.domainLocale())

    /**
     * 갤러리(SPEC-0008 R-5). **nullable이다** — 이 필드의 조회가 실패하면 DGS가 실패를 이 필드에
     * 가두고 null로 채운 뒤 최상위 `errors[]`에 path와 함께 싣는다. 본문은 이미 부모 리졸버가
     * 반환했으므로 그대로 살아남는다(AC-17).
     *
     * 여기서 예외를 삼켜 null을 돌려주지 않는다 — 그러면 `errors[]` 항목이 사라져 클라이언트가
     * "실패"와 "이미지 없음"을 구분할 근거를 잃는다. 실패는 그대로 던지고, 이미지가 없는 정상
     * 경우만 빈 배열이 된다(AC-19).
     */
    @DgsData(parentType = "SpotDetail", field = "gallery")
    fun gallery(dfe: DgsDataFetchingEnvironment): List<GqlSpotImage>? =
        spotQueryUseCase.spotGallery(dfe.catalogId(), dfe.domainLocale())
            .map { it.toGql() }

    @DgsData(parentType = "SpotDetail", field = "intro")
    fun intro(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument categoryCode: String,
    ): GqlSpotIntro? =
        spotQueryUseCase.spotIntro(
            catalogId = dfe.catalogId(),
            category = SpotCategory(categoryCode),
            locale = dfe.domainLocale(),
        )?.toGql()

    /** 이용안내(nullable — SPEC-0006 R10). 실패 전파 규약은 [gallery]와 동일하다. */
    @DgsData(parentType = "SpotDetail", field = "infoItems")
    fun infoItems(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument categoryCode: String,
    ): List<GqlSpotInfoItem>? =
        spotQueryUseCase.spotInfoItems(
            catalogId = dfe.catalogId(),
            category = SpotCategory(categoryCode),
            locale = dfe.domainLocale(),
        ).map { GqlSpotInfoItem(name = it.name, text = it.text) }

    @DgsData(parentType = "SpotDetail", field = "likeCount")
    fun likeCount(dfe: DgsDataFetchingEnvironment): Int =
        // GraphQL likeCount는 Int!. 반응 수가 Int 범위를 넘을 일은 없다.
        spotQueryUseCase.spotLikeCount(dfe.catalogId(), dfe.domainLocale()).toInt()

    /**
     * 호출자가 이 장소에 남긴 최신 반응. **비로그인이면 null이다** — 공개 조회라
     * `requireMemberId`가 아니라 `currentMemberId`를 쓴다(`spots`의 선례).
     *
     * 클라이언트가 질문/답변 작성 진입점을 그릴 때 참고하는 값이며(SPEC-0026 AC-3.6),
     * **서버 판정을 대체하지 않는다** — 실제 자격은 뮤테이션 시점에 다시 본다(R5).
     */
    @DgsData(parentType = "SpotDetail", field = "viewerReaction")
    fun viewerReaction(dfe: DgsDataFetchingEnvironment): String? {
        // 만료 토큰이면 null(=반응 없음)이 아니라 오류다 — 그래야 클라이언트가 갱신한다(R1).
        dfe.rejectStaleToken()
        val memberId = currentMemberProvider.currentMemberId() ?: return null
        return spotQueryUseCase.spotViewerReaction(dfe.catalogId(), memberId, dfe.domainLocale())
    }

    /**
     * 인증된 회원이 좋아요한 spot 커넥션. 미인증이면 빈 목록이 아니라 UNAUTHENTICATED 오류다.
     *
     * **read DTO(`CursorPage`)를 그대로 반환하면 안 된다.** Spring for GraphQL의
     * `ConnectionFieldTypeVisitor`가 `edges`·`pageInfo`를 가진 필드를 Relay 커넥션으로 인식해 감싸려
     * 하는데, 이 저장소엔 `ConnectionAdapter`가 없어 **클래스 이름이 `Connection`으로 끝나는** 타입만
     * 통과한다. `CursorPage`를 돌려주면 `No ConnectionAdapter for: ...CursorPage`로 실패한다.
     */
    @DgsQuery
    fun likedSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlLikedSpotConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = spotQueryUseCase.likedSpots(
            LikedSpotsQuery(
                categories = toCategorySelection(categories),
                first = first ?: DEFAULT_LIKED_PAGE_SIZE,
                after = after,
            ),
            locale = dfe.domainLocale(),
            memberId = memberId,
        )
        return GqlLikedSpotConnection(
            edges = page.edges.map {
                GqlLikedSpotEdge(
                    cursor = it.cursor,
                    node = it.node.toGql(),
                    // likedAt은 노드가 아니라 엣지의 값이다 — 회원과 spot의 *관계* 속성이라
                    // 같은 spot이라도 회원마다 다르다.
                    likedAt = it.node.likedAt.toString(),
                )
            },
            pageInfo = GqlPageInfo(
                hasNextPage = page.pageInfo.hasNextPage,
                hasPreviousPage = page.pageInfo.hasPreviousPage,
                startCursor = page.pageInfo.startCursor,
                endCursor = page.pageInfo.endCursor,
            ),
        )
    }

    private fun LikedSpotView.toGql(): GqlLikedSpot = GqlLikedSpot(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        address = address,
        category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
        copyright = GqlCopyrightType.valueOf(copyright.name),
        attributionText = attributionText,
    )

    private fun SpotPageView.toGql(): GqlSpotPage = GqlSpotPage(
        items = items.map { it.toGql() },
        page = page,
        hasNext = hasNext,
        totalCount = totalCount,
    )

    private fun SpotView.toGql(): GqlSpot = GqlSpot(
        id = id,
        title = title,
        address = address,
        thumbnailImageUrl = thumbnailImageUrl,
        distanceMeters = distanceMeters,
        category = GqlSpotCategory(code = category.code, label = category.code, iconUrl = null),
        copyright = GqlCopyrightType.valueOf(copyright.name),
        attributionText = attributionText,
        mapX = mapX,
        mapY = mapY,
    )

    private fun SpotIntroView.toGql(): GqlSpotIntro = GqlSpotIntro(
        businessHours = businessHours,
        closedDays = closedDays,
        parking = parking,
        inquiryPhone = inquiryPhone,
        fee = fee,
        petAllowed = petAllowed,
        creditCardAccepted = creditCardAccepted,
    )

    private fun SpotImageView.toGql(): GqlSpotImage = GqlSpotImage(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        copyright = GqlCopyrightType.valueOf(copyright.name),
        attributionText = attributionText,
    )

    private companion object {
        const val DEFAULT_RADIUS_METERS = 20_000
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 100

        /** 좋아요 목록 기본 페이지 크기. `members`의 선례를 따랐다(SPEC-0008 [TBD-2]). */
        const val DEFAULT_LIKED_PAGE_SIZE = 20
    }

    /**
     * 부모 `spotDetail`이 돌려준 read DTO에서 카탈로그 id를 꺼낸다(`SpotDetailView.id`는 카탈로그 id다).
     *
     * `!!` 대신 명시적으로 실패시킨다 — 부모 리졸버가 반환한 값이라 실제로 null이 될 수 없지만,
     * 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다. 단언 연산자는 그 사실을
     * 숨기는 반면 이 쪽은 왜 안전한지를 남긴다.
     */
    private fun DgsDataFetchingEnvironment.catalogId(): Long =
        getSource<SpotDetailView>()?.id
            ?: error("SpotDetail 필드 리졸버는 부모가 반환한 SpotDetailView를 소스로 받아야 한다.")
}
