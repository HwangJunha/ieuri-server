package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.appKey
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.tourism.adapter.graphql.dataloader.CategoryDisplayDataLoader
import org.around.ieuri.tourism.adapter.graphql.mapper.toCategorySelection
import org.around.ieuri.tourism.adapter.graphql.mapper.toGql
import org.around.ieuri.tourism.adapter.graphql.mapper.toGqlCodeOnly
import org.around.ieuri.tourism.adapter.graphql.mapper.toGqlLikedSpotConnection
import org.around.ieuri.tourism.adapter.graphql.mapper.toGqlSpotPage
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.catalog.provided.SpotCatalogQueryUseCase
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotsQuery
import org.around.ieuri.tourism.application.catalog.provided.dto.SpotsQuery
import org.around.ieuri.tourism.application.category.provided.CategoryQueryUseCase
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.application.spot.provided.SpotDetailQueryUseCase
import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailView
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.dataloader.DataLoader
import org.around.ieuri.common.graphql.generated.types.LikedSpotConnection as GqlLikedSpotConnection
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory
import org.around.ieuri.common.graphql.generated.types.SpotImage as GqlSpotImage
import org.around.ieuri.common.graphql.generated.types.SpotInfoItem as GqlSpotInfoItem
import org.around.ieuri.common.graphql.generated.types.SpotIntro as GqlSpotIntro
import org.around.ieuri.common.graphql.generated.types.SpotPage as GqlSpotPage

/**
 * 관광지 조회 GraphQL 리졸버(CQRS 읽기). 언어는 Accept-Language 헤더에서 읽는다(GraphQL 인자 아님).
 * - 쿼리 도메인 오류는 TourismQueryExceptionHandler가 최상위 errors[]로 분류한다.
 */
@DgsComponent
class SpotQueryResolver(
    private val catalogQueryUseCase: SpotCatalogQueryUseCase,
    private val detailQueryUseCase: SpotDetailQueryUseCase,
    private val categoryQueryUseCase: CategoryQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
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
        // 만료 토큰은 비로그인으로 강등하지 않고 오류다(R1).
        val memberId = dfe.viewerMemberId()
        val result = catalogQueryUseCase.spots(
            SpotsQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters ?: DEFAULT_RADIUS_METERS,
                categories = toCategorySelection(categories),
                page = page ?: DEFAULT_PAGE,
                size = size ?: DEFAULT_SIZE,
            ),
            locale = dfe.domainLocale(),
            // 비로그인 허용 — memberId가 있으면 이미 반응한 항목을 제외한다.
            memberId = memberId,
        )
        return result.toGqlSpotPage()
    }

    /**
     * 칩 목록. 배열 순서는 보장하지 않는다(클라이언트가 정한다, V59).
     * - 읽어 온 라벨·아이콘을 로더에 미리 채운다 — 안 채우면 필드 리졸버가 한 번 더 조회한다.
     */
    @DgsQuery
    fun availableSpotCategories(dfe: DgsDataFetchingEnvironment): List<GqlSpotCategory> {
        val configured = categoryQueryUseCase.availableCategories(dfe.appKey(), dfe.domainLocale())
        val loader: DataLoader<String, CategoryDisplay> =
            dfe.getDataLoader(CategoryDisplayDataLoader::class.java)
        configured.forEach {
            loader.prime(it.category.code, it)
        }
        return configured.map { it.toGql() }
    }

    /**
     * 상세 본문(upstream 1콜). gallery·intro·infoItems·likeCount는 선택된 경우에만 필드 리졸버가 조회한다.
     * - 생성 타입이 아니라 read DTO를 반환한다 — 필드 리졸버가 여기서 id를 읽는다.
     */
    @DgsQuery
    fun spotDetail(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument id: Long,
    ): SpotDetailView = detailQueryUseCase.spotDetail(catalogId = id, locale = dfe.domainLocale())

    /**
     * 분류. 읽기 모델의 분류는 값 클래스라 실행 중에 문자열로 풀린다 — 생성 타입으로 옮겨야 `code`·`label`이 풀린다.
     * - `label`·`iconUrl`은 `CategoryDisplayResolver`가 언어에 맞춰 채운다.
     */
    @DgsData(parentType = "SpotDetail", field = "category")
    fun category(dfe: DgsDataFetchingEnvironment): GqlSpotCategory = requireNotNull(dfe.getSource<SpotDetailView>()).category.toGqlCodeOnly()

    /**
     * 갤러리(SPEC-0008 R-5). nullable — 조회 실패는 이 필드만 null로 두고 `errors[]`에 싣는다(AC-17).
     * - 예외를 삼키지 않는다. 이미지가 없는 정상 경우만 빈 배열이다(AC-19).
     */
    @DgsData(parentType = "SpotDetail", field = "gallery")
    fun gallery(dfe: DgsDataFetchingEnvironment): List<GqlSpotImage>? = detailQueryUseCase.spotGallery(dfe.catalogId(), dfe.domainLocale())
        .map { it.toGql() }

    @DgsData(parentType = "SpotDetail", field = "intro")
    fun intro(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument categoryCode: String,
    ): GqlSpotIntro? = detailQueryUseCase.spotIntro(
        catalogId = dfe.catalogId(),
        category = SpotCategory(categoryCode),
        locale = dfe.domainLocale(),
    )?.toGql()

    /** 이용안내(nullable — SPEC-0006 R10). 실패 전파 규약은 [gallery]와 동일하다. */
    @DgsData(parentType = "SpotDetail", field = "infoItems")
    fun infoItems(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument categoryCode: String,
    ): List<GqlSpotInfoItem>? = detailQueryUseCase.spotInfoItems(
        catalogId = dfe.catalogId(),
        category = SpotCategory(categoryCode),
        locale = dfe.domainLocale(),
    ).map { it.toGql() }

    @DgsData(parentType = "SpotDetail", field = "likeCount")
    fun likeCount(dfe: DgsDataFetchingEnvironment): Int = // GraphQL likeCount는 Int!. 반응 수가 Int 범위를 넘을 일은 없다.
        catalogQueryUseCase.spotLikeCount(dfe.catalogId(), dfe.domainLocale()).toInt()

    /**
     * 호출자가 이 장소에 남긴 반응. 비로그인이면 null이다.
     * - 작성 진입점 표시용이며 서버 판정을 대체하지 않는다(SPEC-0026 AC-3.6, R5).
     */
    @DgsData(parentType = "SpotDetail", field = "viewerReaction")
    fun viewerReaction(dfe: DgsDataFetchingEnvironment): String? {
        // 만료 토큰이면 null이 아니라 오류다(R1).
        val memberId = dfe.viewerMemberId() ?: return null
        return catalogQueryUseCase.spotViewerReaction(dfe.catalogId(), memberId, dfe.domainLocale())?.name
    }

    /** 뷰어 신원. 비로그인은 null, 만료 토큰은 오류다(SPEC-0032 R1). */
    private fun DgsDataFetchingEnvironment.viewerMemberId(): Long? = currentMemberProvider.optionalMemberId(tokenPresentation())

    /**
     * 인증된 회원이 좋아요한 spot 커넥션. 미인증이면 UNAUTHENTICATED 오류다.
     * - `CursorPage`를 그대로 반환하면 안 된다 — 이름이 `Connection`으로 끝나지 않으면 `No ConnectionAdapter`로 실패한다.
     */
    @DgsQuery
    fun likedSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlLikedSpotConnection {
        val memberId = currentMemberProvider.requireMemberId()
        return catalogQueryUseCase.likedSpots(
            LikedSpotsQuery(
                categories = toCategorySelection(categories),
                first = first ?: DEFAULT_LIKED_PAGE_SIZE,
                after = after,
            ),
            locale = dfe.domainLocale(),
            memberId = memberId,
        ).toGqlLikedSpotConnection()
    }

    /**
     * 좋아요 목록 내 검색(SPEC-0033). 응답·정렬·커서 규약은 [likedSpots]와 같다.
     * - 검색 노드를 보므로 방금 누른 좋아요가 잠시 잡히지 않을 수 있다.
     * - 빈 검색어는 오류다.
     */
    @DgsQuery
    fun searchLikedSpots(
        dfe: DgsDataFetchingEnvironment,
        @InputArgument query: String,
        @InputArgument categories: List<String>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
    ): GqlLikedSpotConnection {
        val memberId = currentMemberProvider.requireMemberId()
        return catalogQueryUseCase.searchLikedSpots(
            LikedSpotsQuery(
                categories = toCategorySelection(categories),
                first = first ?: DEFAULT_LIKED_PAGE_SIZE,
                after = after,
            ),
            // 트림·길이 검증은 서비스가 한다(SPEC-0033 R6).
            text = query,
            locale = dfe.domainLocale(),
            memberId = memberId,
        ).toGqlLikedSpotConnection()
    }

    private companion object {
        const val DEFAULT_RADIUS_METERS = 20_000
        const val DEFAULT_PAGE = 1
        const val DEFAULT_SIZE = 100

        /** 좋아요 목록 기본 페이지 크기. */
        const val DEFAULT_LIKED_PAGE_SIZE = 20
    }

    /** 부모 `spotDetail`의 read DTO에서 카탈로그 id를 꺼낸다. 없으면 명시적으로 실패한다. */
    private fun DgsDataFetchingEnvironment.catalogId(): Long = getSource<SpotDetailView>()?.id
        ?: error("SpotDetail 필드 리졸버는 부모가 반환한 SpotDetailView를 소스로 받아야 한다.")
}
