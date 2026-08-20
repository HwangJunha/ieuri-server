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

    /**
     * 이 회원이 **[language]에서** 이미 반응한 카탈로그 id 전체. 반경 조회의 제외 집합이다.
     *
     * **언어별로 갈린다**(SPEC-0029 R-13) — 영어로 반응한 장소가 일본어 발견 큐에서까지
     * 사라지면 그 언어에서는 한 번도 보지 못한 장소를 잃는다.
     */
    fun findReactedSpotIds(memberId: Long, language: DomainLocale): Set<Long>

    /**
     * 이 카탈로그에 대한 **[language]의** LIKE 반응 수. DISLIKE는 세지 않으며, 반응이 없으면 0.
     * 다른 언어의 좋아요는 합산되지 않는다(SPEC-0029 AC-13.5).
     */
    fun countLikes(catalogId: Long, language: DomainLocale): Long

    /**
     * 이 회원이 **[language]에서** 이 카탈로그에 남긴 최신 반응. 반응한 적 없으면 null.
     *
     * `reactToSpot`은 덮어쓰기만 가능하고 취소 수단이 없어 (회원, 카탈로그, 언어)당 행이
     * 하나뿐이다 — 그 행이 곧 최신 반응이다. 영어로 좋아요를 누른 뒤 일본어로 물으면
     * null이다(AC-13.2). 쓰기 포트(SpotReactionPort.find)와 같은 행을 보지만 이쪽은 읽기
     * 경로라 애그리게이트를 복원하지 않는다.
     */
    fun findReaction(catalogId: Long, memberId: Long, language: DomainLocale): ReactionType?

    /**
     * 카탈로그 id → upstream 식별 정보. **[language] 원본만 본다 — 폴백하지 않는다**
     * (SPEC-0029 R-10). 그 언어에 원본이 없으면 null이다.
     *
     * **`spot`을 읽는 유일한 조회 경로다**(SPEC-0019). TourAPI가 contentId를 언어별로 발급하므로
     * 상세를 물으려면 원본 행이 필요하다. 돌려받은 [SpotIdentity.language]는 항상 [language]다.
     *
     * null은 **두 가지를 뜻한다** — 카탈로그가 없거나, 있어도 그 언어에 원본이 없거나.
     * 둘을 다른 오류 코드로 갈라야 하는 호출자는 [existsCatalog]를 함께 본다.
     *
     * 좌표([SpotIdentity.mapX]/[SpotIdentity.mapY])는 원본 행이 아니라 **카탈로그**의 값이다.
     */
    fun findIdentity(catalogId: Long, language: DomainLocale): SpotIdentity?

    /**
     * 카탈로그가 존재하는가 — **언어와 무관하다.**
     *
     * [findIdentity]·[findSpotBasics]의 null을 `SPOT_NOT_FOUND`(카탈로그 없음)와
     * `SPOT_NOT_FOUND_IN_LOCALE`(언어에 원본 없음)로 가르는 데만 쓴다(AC-12.3).
     */
    fun existsCatalog(catalogId: Long): Boolean

    /**
     * 이 카탈로그가 **해소되는 언어 전부**(SPEC-0029 R-15). 어느 언어에서도 해소되지 않으면
     * 빈 집합이며, 카탈로그가 없을 때와 같은 값이다 — 호출자에게 두 경우의 결과가 같다.
     *
     * 판정은 `spot_catalog_translation.visible`이다(V71 이후 "그 언어에 원본이 있다"는 뜻).
     */
    fun findResolvedLanguages(catalogId: Long): Set<DomainLocale>

    /**
     * 카탈로그가 [language]로 보유한 상세 표시 값. 상세 본문이 부재할 때 응답을 구성하고 부재
     * 행의 NOT NULL 컬럼을 채우는 재료다.
     *
     * **[language] 번역만 본다 — 폴백하지 않는다**(SPEC-0029 R-10). 그 언어에 원본이 없거나
     * 카탈로그 자체가 없으면 null이다.
     */
    fun findSpotBasics(catalogId: Long, language: DomainLocale): SpotBasics?

    /**
     * 여러 카탈로그의 기본 정보를 **IN 절 한 번**으로. **[language]에서 해소되지 않는 id는
     * 결과에 키가 없다**(SPEC-0029 R-10) — 없는 id와 같게 다뤄진다.
     */
    fun findSpotBasicsIn(catalogIds: List<Long>, language: DomainLocale): Map<Long, SpotBasics>

    /**
     * 회원이 좋아요(LIKE)한 spot 한 페이지. DISLIKE는 제외한다.
     * **요청 언어가 반응과 번역 양쪽에 걸린다**(SPEC-0029 AC-14.1/14.2) — 그 언어에서 누른
     * 좋아요만, 그 언어의 제목으로 나온다.
     */
    fun findLikedSpots(query: LikedSpotsSearch): CursorPage<LikedSpotView>
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
    /** 카테고리 선택. 비어 있으면 전체. 값이 있으면 `spot_catalog.category`를 IN으로 거른다. */
    val categories: CategorySelection,
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
    /** 카테고리 선택. 비어 있으면 전체. 값이 여럿이면 IN으로 거른다(OR). */
    val categories: CategorySelection,
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
 * `spot.id`가 가리키는 행의 upstream 식별 정보와 카탈로그 좌표.
 * 상세 조회의 TourAPI 폴백·언어 검증, 그리고 상세 응답의 좌표(SPEC-0027 R1)에 쓴다.
 */
data class SpotIdentity(
    /**
     * 원본 `spot.id`. **상세 캐시·`spot_detail` 계열의 키다.**
     *
     * 카탈로그 id를 그 자리에 쓰면 안 된다 — `spot_detail*`의 FK가 `spot(id)`를 가리키므로
     * 적재가 FK 위반으로 조용히 실패하고(리스너가 삼킨다), 우연히 값이 겹치면 **다른 장소의
     * 본문이 적재된다.** 두 id 공간이 겹칠 수 있다는 것이 이 필드가 존재하는 이유다.
     */
    val spotId: Long,
    val contentId: String,
    val language: DomainLocale,
    /** 경도. **`spot_catalog`의 값이며 원본 `spot` 행의 값이 아니다**(SPEC-0027). */
    val mapX: Double,
    /** 위도. 출처는 [mapX]와 같다. */
    val mapY: Double,
)
