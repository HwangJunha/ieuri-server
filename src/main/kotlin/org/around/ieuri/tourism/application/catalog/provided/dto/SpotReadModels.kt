package org.around.ieuri.tourism.application.catalog.provided.dto

import org.around.ieuri.tourism.domain.catalog.CategorySelection
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.CopyrightType
import java.time.Instant
import java.time.LocalDate

/** spots 쿼리 입력. 오프셋 페이지네이션이며 [page]는 1부터 센다. 언어는 Accept-Language에서 온다. */
data class SpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val page: Int,
    val size: Int,
)

/** spots 응답. [items]는 거리 오름차순 정렬된 한 페이지다. */
data class SpotPageView(
    val items: List<SpotView>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int?,
)

/** spots 응답의 카드 1장. */
data class SpotView(
    val id: Long,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val distanceMeters: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val attributionText: String,
    val mapX: Double,
    val mapY: Double,
)

/** 좋아요 목록 조회 입력(SPEC-0008). 언어는 Accept-Language, 회원은 인증 컨텍스트에서 온다. */
data class LikedSpotsQuery(
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(`spots`와 같은 규약). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)

/**
 * 좋아요 목록 항목 1건. 상세가 부재한 spot도 목록에 나타난다.
 * - [likedAt]은 노드가 아니라 엣지의 값이다 — 리졸버가 엣지로 옮긴다.
 */
data class LikedSpotView(
    val id: Long,
    val title: String,
    val thumbnailImageUrl: String,
    val address: String?,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val attributionText: String,
    val likedAt: Instant,
    /** 소속 카탈로그. 커서의 정렬 키이며 응답에는 나가지 않는다. */
    val catalogId: Long,
)

/** spot 검색 입력(SPEC-0013). 오프셋 페이지네이션이며 [page]는 1부터 센다. 언어는 Accept-Language에서 온다. */
data class SearchSpotsQuery(
    val query: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val page: Int,
    val size: Int,
)

/** 검색 응답. 검증 실패도 이 형태로 돌아온다(POL-015) — [userErrors]가 있으면 [items]는 비고 [totalCount]는 0이다. */
data class SpotSearchResultView(
    val userErrors: List<SearchUserError>,
    val items: List<SpotView>,
    val page: Int,
    val hasNext: Boolean,
    val totalCount: Int,
) {
    val success: Boolean get() = userErrors.isEmpty()
}

/** 검색 입력 검증 실패 1건. 클라이언트는 메시지가 아니라 [code]로 분기한다. */
data class SearchUserError(
    val code: String,
    val message: String,
    val field: String?,
)

/** 자동완성 항목 1건. 제목 접두 매칭 결과다. */
data class SpotSuggestionView(
    val id: Long,
    val title: String,
    val category: SpotCategory,
)

/** 진행 중 축제 조회 입력. '오늘'은 서버 시계에서 온다. */
data class OngoingFestivalsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    val first: Int,
    val after: String?,
)

/** 진행 중 축제 1건. 날짜에는 시각 성분이 없다. */
data class FestivalView(
    val id: Long,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val distanceMeters: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val attributionText: String,
    val mapX: Double,
    val mapY: Double,
    val eventStartDate: LocalDate,
    val eventEndDate: LocalDate,
)

/** 인기 spot 조회 입력. 좋아요 최소 기준은 정책이 정한다. */
data class PopularSpotsQuery(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int,
    /** 카테고리 선택. 비어 있으면 전체이며, 값이 여럿이면 OR로 결합된다(SPEC-0020 R5). */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)

/** 인기 spot 1건. [likeCount]는 엣지의 값이다 — 리졸버가 엣지로 옮긴다. */
data class PopularSpotView(
    val spot: SpotView,
    val likeCount: Long,
)
