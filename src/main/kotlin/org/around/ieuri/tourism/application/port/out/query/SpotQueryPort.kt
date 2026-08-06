package org.around.ieuri.tourism.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.query.LikedSpotView
import org.around.ieuri.tourism.domain.vo.Coordinates
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory

/**
 * 관광지 관련 읽기 아웃바운드 포트(CQRS 읽기). 애그리거트를 우회해 필요한 값만 프로젝션한다.
 * spot 조회에 필요한 DB 읽기를 한 포트로 모은다(반경 조회, 반응 기록 기반 집계·필터).
 */
interface SpotQueryPort {
    /**
     * 반경 안의 관광지를 **거리 오름차순**으로 한 페이지 조회한다. TourAPI를 호출하지 않고 적재된
     * spot 테이블만 본다. [SpotSearchResult.distanceMeters]는 DB가 계산한 구면 거리다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int

    /** 이 회원이 현재 언어에서 이미 반응한 `spot.id` 전체. */
    fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long>

    /**
     * 이 `spot.id`에 대한 LIKE 반응 수. DISLIKE는 세지 않으며, 반응이 없으면 0.
     * **언어 조건이 없다** — spotId가 이미 언어별이라 언어를 다시 거르면 중복이다.
     */
    fun countLikes(spotId: Long): Long

    /**
     * `spot.id`에 대응하는 upstream 식별 정보. 없으면 null(= 존재하지 않는 spot).
     * 반환된 [SpotIdentity.language]는 언어 교차 id를 거부하는 근거이기도 하다.
     */
    fun findIdentity(spotId: Long): SpotIdentity?

    /**
     * `spot` 행이 이미 보유한 상세 표시 값. 상세 본문이 부재할 때 응답을 구성하고 부재 행의 NOT NULL
     * 컬럼을 채우는 재료다. 없으면 null(= 존재하지 않는 spot).
     */
    fun findSpotBasics(spotId: Long): SpotBasics?

    /** 여러 spot의 기본 정보를 **IN 절 한 번**으로. 없는 id는 결과에 키가 없다. */
    fun findSpotBasicsIn(spotIds: List<Long>): Map<Long, SpotBasics>

    /** 회원이 좋아요(LIKE)한 spot 한 페이지. DISLIKE는 제외하고 요청 언어로 한정한다. */
    fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView>

    /** 반경 안의 spot id. [limit]을 넘으면 잘린다 — 호출자가 그것을 감안해야 한다. */
    fun findSpotIdsWithin(
        longitude: Double,
        latitude: Double,
        radiusMeters: Int,
        limit: Int,
        categoryCode: String? = null,
    ): List<Long>
}

/**
 * `spot` 행이 보유한 상세 표시 값(read DTO). 본문 부재 시 응답·적재의 재료가 된다.
 * attributionText는 copyright에서 도출되므로 담지 않는다.
 */
data class SpotBasics(
    val title: String,
    val address: String?,
    val thumbnailImageUrl: String,
    val category: SpotCategory,
    val copyright: CopyrightType,
    /** 경도. 다른 도메인이 이 좌표를 복사해 자기 반경 조회에 쓴다. */
    val longitude: Double,
    /** 위도. */
    val latitude: Double,
)

/**
 * 좋아요 목록 조회 입력. [language]로 한정하고 [memberId] 본인 것만 조회한다.
 * [after]는 불투명 커서이며 정렬 키(likedAt, spotId)에서 파생된다.
 */
data class LikedSpotsSearch(
    val memberId: Long,
    val language: DomainLocale,
    /** 카테고리 필터. null이면 전체. 값이 있으면 `spot.category`로 거른다. */
    val category: SpotCategory?,
    val first: Int,
    val after: String?,
)

/**
 * 반경 조회 입력. [excludedSpotIds]는 로그인 회원이 이미 반응한 항목으로, **DB 쿼리 단계에서**
 * 제외해야 한다 — 조회 후 애플리케이션에서 걸러내면 페이지가 요청한 size보다 작아진다.
 */
data class NearbySpotSearch(
    val language: DomainLocale,
    val center: Coordinates,
    val radiusMeters: Int,
    val category: SpotCategory?,
    val excludedSpotIds: Set<Long>,
    val limit: Int,
    val offset: Int,
)

/**
 * 반경 조회 결과 1건(read DTO). 리졸버가 GraphQL Spot 타입으로 매핑한다(application은 DGS 무의존).
 * [distanceMeters]는 DB가 ST_Distance로 계산한 요청 좌표로부터의 구면 거리(미터)다.
 * attributionText는 copyright에서 파생되므로 담지 않는다 — 서비스가 도출한다.
 */
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

/** `spot.id`가 가리키는 행의 upstream 식별 정보. 상세 조회의 TourAPI 폴백과 언어 검증에만 쓴다. */
data class SpotIdentity(
    val contentId: String,
    val language: DomainLocale,
)
