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
     * 반경 안의 관광지를 **거리 오름차순**으로 한 페이지 조회한다(SPEC-0004 R-8·R-9).
     * TourAPI를 호출하지 않는다 — 적재된 spot 테이블만 본다(AC-20·AC-21).
     *
     * 거리 계산·정렬을 DB가 수행하는 것이 SPEC-0002와 달라진 점이다. 당시에는 upstream이 반경 안의
     * 결과만 줘서 application이 Haversine으로 거리를 채우고 정렬했지만, 전량 적재 후에는 "반경 안"을
     * DB가 인덱스로 풀어야 한다(AC-24). [SpotSearchResult.distanceMeters]는 그 계산 결과다.
     */
    fun searchNearby(query: NearbySpotSearch): List<SpotSearchResult>

    /** [query] 조건에 맞는 전체 건수(페이지 무관). SpotPage.totalCount 산출용. */
    fun countNearby(query: NearbySpotSearch): Int

    /**
     * 이 회원이 (현재 언어에서) 이미 반응한 `spot.id` 전체.
     *
     * SPEC-0002에서는 upstream이 준 후보로 범위를 좁혔지만(IN), 이제는 제외를 **DB 조회 단계에**
     * 넘겨야 해서(그래야 페이지가 size보다 작아지지 않는다) 후보를 미리 알 수 없다.
     * 반응 기록은 회원당 많아야 수천 건 규모라 전량 조회가 현실적이다.
     */
    fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long>

    /**
     * 이 `spot.id`에 대한 LIKE 반응 수(SPEC-0003 §5). DISLIKE는 세지 않는다. **언어 조건이 없다** —
     * spotId가 이미 언어별이므로 언어를 다시 거르면 중복이다(V20). 반응이 없으면 0.
     * 상세 조회 시 캐시와 무관하게 매번 실시간 집계한다(likeCount는 캐시 비대상).
     */
    fun countLikes(spotId: Long): Long

    /**
     * `spot.id`에 대응하는 upstream 좌표. **상세 조회가 캐시·DB를 모두 놓쳤을 때만 필요하다** —
     * TourAPI는 우리 PK를 모르므로 `contentId`로 물어야 하기 때문이다.
     *
     * 반환된 [SpotIdentity.language]는 **언어 교차 id를 거부하는 근거**이기도 하다. 요청 언어와
     * 다르면 존재하지 않는 것으로 취급한다 — 조용히 다른 언어 콘텐츠를 돌려주면 클라이언트의
     * 언어 전환 버그가 드러나지 않는다.
     *
     * 없으면 null(= 존재하지 않는 spot).
     */
    fun findIdentity(spotId: Long): SpotIdentity?

    /**
     * `spot` 행이 이미 보유한 상세 표시 값(SPEC-0008 R-2). **상세 본문이 부재할 때 응답을 구성하는
     * 재료다** — title·category·thumbnailImageUrl·copyright가 여기서 오므로 본문이 없어도 non-null
     * 필드를 모두 채울 수 있고(AC-5·AC-6), 부재 행의 NOT NULL 컬럼도 이 값으로 적재한다.
     *
     * 없으면 null(= 존재하지 않는 spot).
     */
    fun findSpotBasics(spotId: Long): SpotBasics?

    /**
     * 회원이 좋아요(LIKE)한 spot 한 페이지(SPEC-0008 R-15·R-16). DISLIKE는 제외한다(AC-45).
     *
     * **요청 언어로 한정한다**(AC-49). `spot_reaction`은 V20 이후 language 컬럼이 없으므로 `spot`을
     * 조인해 판별한다 — 종료 축제 조회가 같은 이유로 `spot`을 조인하는 것과 같은 패턴이다.
     */
    fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView>

    /**
     * 좋아요 총 개수. **`totalCount` 필드가 선택됐을 때만 호출된다** — 별도 COUNT(*)라 비용이 따로
     * 들고, 커서 페이징에 필수도 아니다(소진 판정은 hasNextPage). 목록과 같은 조건을 써야 한다.
     */
    fun countLikedSpots(memberId: Long, language: DomainLocale): Long
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
)

/**
 * 좋아요 목록 조회 입력. [language]로 한정하고 [memberId] 본인 것만 조회한다(AC-48).
 * [after]는 불투명 커서이며 정렬 키(likedAt, spotId)에서 파생된다.
 */
data class LikedSpotsSearch(
    val memberId: Long,
    val language: DomainLocale,
    val first: Int,
    val after: String?,
)

/**
 * 반경 조회 입력. [excludedContentIds]는 로그인 회원이 이미 반응한 항목으로, **DB 쿼리 단계에서**
 * 제외한다 — 조회 후 애플리케이션에서 걸러내면 페이지가 요청한 size보다 작아지기 때문이다
 * (SPEC-0002의 실시간 조회에서는 upstream 페이지를 그대로 서빙해 이 문제가 없었다).
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

/**
 * `spot.id`가 가리키는 행의 upstream 식별 정보. **상세 조회의 TourAPI 폴백과 언어 검증에만 쓴다.**
 *
 * 이 타입이 존재하는 이유는 외부 식별자(`spot.id`)와 upstream 식별자(`contentId`)가 분리됐기
 * 때문이다. 분리 이전에는 둘이 같아서 변환이 필요 없었다.
 */
data class SpotIdentity(
    val contentId: String,
    val language: DomainLocale,
)
