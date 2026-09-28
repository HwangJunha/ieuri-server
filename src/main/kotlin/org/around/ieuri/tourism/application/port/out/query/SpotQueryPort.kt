package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.CategorySelection
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.ReactionType
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 읽기 아웃바운드 포트. 반경 조회는 [SpotSpatialQueryPort]에 있다.
 */
interface SpotQueryPort {
    /** 이 회원이 [language]에서 이미 반응한 카탈로그 id 전체. 다른 언어의 반응은 포함하지 않는다(SPEC-0029 R-13). */
    fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long>

    /** [findReactedSpotIds]와 같은 집합을 반응 종류별로. 반응이 없는 종류는 키가 없다. */
    fun findReactedSpotIdsByReaction(memberId: Long, language: DomainLocale): Map<ReactionType, Set<Long>>

    /** 이 카탈로그의 [language] LIKE 수. DISLIKE·다른 언어는 세지 않으며, 없으면 0. */
    fun countLikes(catalogId: Long, language: DomainLocale): Long

    /** 이 회원이 [language]에서 이 카탈로그에 남긴 반응. 그 언어로 반응한 적 없으면 null. */
    fun findReaction(catalogId: Long, memberId: Long, language: DomainLocale): ReactionType?

    /**
     * 카탈로그 id → upstream 식별 정보. [language] 원본만 보며 폴백하지 않는다(SPEC-0029 R-10).
     * - null은 카탈로그가 없거나 그 언어에 원본이 없다는 뜻이다. 둘을 가르려면 [existsCatalog]를 본다.
     */
    fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity?

    /** 카탈로그 존재 여부(언어 무관). `SPOT_NOT_FOUND`와 `SPOT_NOT_FOUND_IN_LOCALE`을 가르는 데 쓴다. */
    fun existsCatalog(catalogId: Long): Boolean

    /** 카탈로그의 [language] 상세 표시 값. 폴백하지 않으며, 원본이나 카탈로그가 없으면 null. */
    fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics?

    /** 여러 카탈로그의 기본 정보. [language]에서 해소되지 않는 id는 결과에 키가 없다. */
    fun findSpotBasicsIn(catalogIds: List<Long>, language: DomainLocale): Map<Long, SpotBasics>

    /** 회원이 [LikedSpotsSearch.language]에서 좋아요한 spot 한 페이지를 그 언어의 제목으로. DISLIKE는 제외. */
    fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView>
}

/** `spot` 행이 보유한 상세 표시 값(read DTO). */
data class SpotBasics(
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val copyright: CopyrightType,
    /** 경도. */
    val longitude: Double,
    /** 위도. */
    val latitude: Double,
)

/** 좋아요 목록 조회 입력. [after]는 (likedAt, spotId)에서 파생된 불투명 커서다. */
data class LikedSpotsSearch(
    val memberId: Long,
    val language: DomainLocale,
    /** 비어 있으면 전체. */
    val categories: CategorySelection,
    val first: Int,
    val after: String?,
)

/**
 * 반경 조회 입력.
 * - [excludedSpotIds]는 DB 쿼리에서 제외해야 한다. 조회 후 거르면 페이지가 size보다 작아진다.
 */
data class NearbySpotSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    /** 비어 있으면 전체, 여럿이면 OR. */
    val categories: CategorySelection,
    val excludedSpotIds: Set<Long>,
    val limit: Int,
    val offset: Int,
)

/** 반경 조회 결과 1건(read DTO). [distanceMeters]는 요청 좌표로부터의 구면 거리(미터). */
data class SpotSearchResult(
    val spotId: Long,
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val mapX: Double,
    val mapY: Double,
    val category: SpotCategory,
    val copyright: CopyrightType,
    val distanceMeters: Double,
)

/** `spot.id`가 가리키는 행의 upstream 식별 정보와 카탈로그 좌표. */
data class SpotIdentity(
    /**
     * 원본 `spot.id`. 상세 캐시·`spot_detail` 계열의 키다.
     * - 카탈로그 id를 대신 쓰면 안 된다. 두 id 공간이 겹쳐 다른 장소의 본문이 적재될 수 있다.
     */
    val spotId: Long,
    val contentId: String,
    /** 경도. 원본 `spot` 행이 아니라 `spot_catalog`의 값이다. */
    val mapX: Double,
    /** 위도. 출처는 [mapX]와 같다. */
    val mapY: Double,
)
