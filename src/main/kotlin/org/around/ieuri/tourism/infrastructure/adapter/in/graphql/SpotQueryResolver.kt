package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.exception.SpotDetailNotFoundException
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
     * 갤러리(nullable — SPEC-0006 R9). **예외를 잡지 않는다.** 조회가 실패하면 예외가 그대로 올라가고,
     * 필드가 nullable이므로 GraphQL 실행기가 이 필드만 null로 채운 뒤 `path=["spotDetail","gallery"]`인
     * errors[] 항목을 만든다 — 본문과 형제 필드는 살아남는다. 여기서 예외를 삼켜 빈 배열로 바꾸면
     * 클라이언트가 실패를 알 수 없고 errors[] 항목도 생기지 않는다.
     *
     * 반환 타입이 nullable인 것은 스키마와의 형 일치를 위해서다. 이 함수 자체가 null을 돌려주는 경로는
     * 없다 — null은 실행기가 실패 시에 만든다.
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
