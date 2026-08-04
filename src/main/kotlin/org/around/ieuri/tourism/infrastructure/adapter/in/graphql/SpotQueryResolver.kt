package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.application.query.LikedSpotsQuery
import org.around.ieuri.tourism.application.query.SpotDetailView
import org.around.ieuri.tourism.application.query.SpotIntroView
import org.around.ieuri.tourism.application.query.SpotImageView
import org.around.ieuri.tourism.application.query.SpotPageView
import org.around.ieuri.tourism.application.query.SpotView
import org.around.ieuri.tourism.application.query.SpotsQuery
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.locale.LocaleContext
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
 * 관광지 조회 GraphQL 쿼리 리졸버(인바운드 어댑터, CQRS 읽기). 언어는 요청 스코프 LocaleContext에서 읽어
 * 유스케이스에 넘긴다(GraphQL 인자 아님, Accept-Language 기반). read DTO를 GraphQL 응답 타입으로 매핑한다.
 * 쿼리 도메인 오류는 TourismQueryExceptionHandler가 최상위 errors[]로 분류한다(payload 컨벤션 없음).
 */
@DgsComponent
class SpotQueryResolver(
    private val spotQueryUseCase: SpotQueryUseCase,
    private val localeContext: LocaleContext,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsQuery
    fun spots(
        @InputArgument latitude: Double,
        @InputArgument longitude: Double,
        @InputArgument radiusMeters: Int?,
        @InputArgument category: GqlSpotCategory?,
        @InputArgument page: Int?,
        @InputArgument size: Int?,
    ): GqlSpotPage {
        val result = spotQueryUseCase.spots(
            SpotsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                category = category?.let { SpotCategory.valueOf(it.name) },
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
            ),
            locale = localeContext.locale,
            // 비로그인 허용 쿼리 — memberId가 있으면 이미 반응한 항목을 제외한다(requireMemberId 아님).
            memberId = currentMemberProvider.currentMemberId(),
        )
        return result.toGql()
    }

    @DgsQuery
    fun availableSpotCategories(): List<GqlSpotCategory> =
        spotQueryUseCase.availableCategories(localeContext.locale)
            .map { GqlSpotCategory.valueOf(it.name) }

    /**
     * 상세 본문(detailCommon2 1콜). gallery·intro·infoItems·likeCount는 **요청된 경우에만** 아래 @DgsData
     * 필드 리졸버가 각자 조회한다 — `{ id title overview }`만 물으면 upstream 콜은 1회다.
     *
     * 생성 타입(GqlSpotDetail)이 아니라 read DTO를 반환한다: 생성 타입은 non-null 필드를 생성자 필수 인자로
     * 요구해서 지연 필드를 비워 둘 수 없다. 지연 필드의 소스로는 read DTO가 맞다(각 필드 리졸버가 여기서 id를 읽는다).
     */
    @DgsQuery
    fun spotDetail(@InputArgument id: Long): SpotDetailView =
        spotQueryUseCase.spotDetail(spotId = id, locale = localeContext.locale)

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
        spotQueryUseCase.spotGallery(dfe.spotId(), localeContext.locale)
            .map { it.toGql() }

    @DgsData(parentType = "SpotDetail", field = "intro")
    fun intro(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument category: GqlSpotCategory,
    ): GqlSpotIntro? =
        spotQueryUseCase.spotIntro(
            spotId = dfe.spotId(),
            category = SpotCategory.valueOf(category.name),
            locale = localeContext.locale,
        )?.toGql()

    /** 이용안내(nullable — SPEC-0006 R10). 실패 전파 규약은 [gallery]와 동일하다. */
    @DgsData(parentType = "SpotDetail", field = "infoItems")
    fun infoItems(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument category: GqlSpotCategory,
    ): List<GqlSpotInfoItem>? =
        spotQueryUseCase.spotInfoItems(
            spotId = dfe.spotId(),
            category = SpotCategory.valueOf(category.name),
            locale = localeContext.locale,
        ).map { GqlSpotInfoItem(name = it.name, text = it.text) }

    @DgsData(parentType = "SpotDetail", field = "likeCount")
    fun likeCount(dfe: DgsDataFetchingEnvironment): Int =
        // GraphQL likeCount는 Int!. 반응 수가 Int 범위를 넘을 일은 없다.
        spotQueryUseCase.spotLikeCount(dfe.spotId()).toInt()

    /**
     * 좋아요 목록(SPEC-0008 R-15~R-17).
     *
     * **인증 필수다**(AC-53). `requireMemberId()`가 던지는 예외는 최상위 `errors[]`의
     * UNAUTHENTICATED로 나간다 — 빈 목록으로 대체하지 않는다(AC-54). 빈 목록은 "좋아요가 없음"이라는
     * 다른 의미이고, 둘을 섞으면 클라이언트가 로그인 유도 시점을 판단할 수 없다.
     *
     * `spots`가 `currentMemberId()`(비로그인 허용)를 쓰는 것과 대비된다 — 그쪽은 비로그인도 유효한
     * 조회지만 "내 좋아요"는 주체가 없으면 성립하지 않는다.
     *
     * **생성 타입을 반환해야 한다**(`members` 선례와 동일). Spring for GraphQL의
     * `ConnectionFieldTypeVisitor`가 `edges`·`pageInfo`를 가진 필드를 Relay 커넥션으로 자동 인식해
     * 반환값을 감싸려 하기 때문이다. 이때 통과 조건은 둘 중 하나다 —
     * 등록된 `ConnectionAdapter`가 지원하는 타입이거나, **반환 객체의 클래스 이름이 `Connection`으로
     * 끝나거나**(`ConnectionDataFetcher.adaptDataContainer`). 이 저장소엔 어댑터가 없으므로 후자에
     * 기대며, `GqlLikedSpotConnection`은 이름이 맞아 그대로 통과한다.
     *
     * read DTO(`CursorPage`)를 그대로 돌려주면 두 조건에 모두 걸리지 않아
     * `No ConnectionAdapter for: ...CursorPage`로 실패한다. `spotDetail`이 read DTO를 반환할 수 있는
     * 것과 갈리는 지점이다 — 그쪽은 커넥션 형태가 아니라 이 방문자를 타지 않는다.
     */
    @DgsQuery
    fun likedSpots(
        @InputArgument category: GqlSpotCategory?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlLikedSpotConnection {
        val memberId = currentMemberProvider.requireMemberId()
        val page = spotQueryUseCase.likedSpots(
            LikedSpotsQuery(
                category = category?.let { SpotCategory.valueOf(it.name) },
                first = first ?: DEFAULT_LIKED_PAGE_SIZE,
                after = after,
            ),
            locale = localeContext.locale,
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
        category = GqlSpotCategory.valueOf(category.name),
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
        category = GqlSpotCategory.valueOf(category.name),
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
     * 부모 `spotDetail`이 돌려준 read DTO에서 `spot.id`를 꺼낸다.
     *
     * `!!` 대신 명시적으로 실패시킨다 — 부모 리졸버가 반환한 값이라 실제로 null이 될 수 없지만,
     * 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다. 단언 연산자는 그 사실을
     * 숨기는 반면 이 쪽은 왜 안전한지를 남긴다.
     */
    private fun DgsDataFetchingEnvironment.spotId(): Long =
        getSource<SpotDetailView>()?.id
            ?: error("SpotDetail 필드 리졸버는 부모가 반환한 SpotDetailView를 소스로 받아야 한다.")
}
