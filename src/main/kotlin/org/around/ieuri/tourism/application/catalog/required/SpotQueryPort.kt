package org.around.ieuri.tourism.application.catalog.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.tourism.application.catalog.provided.dto.LikedSpotView
import org.around.ieuri.tourism.application.catalog.required.dto.LikedSpotsSearch
import org.around.ieuri.tourism.application.catalog.required.dto.SpotBasics
import org.around.ieuri.tourism.application.catalog.required.dto.SpotIdentity
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.shared.DomainLocale

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
