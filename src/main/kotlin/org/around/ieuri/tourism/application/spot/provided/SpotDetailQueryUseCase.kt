package org.around.ieuri.tourism.application.spot.provided

import org.around.ieuri.tourism.application.spot.provided.dto.SpotDetailView
import org.around.ieuri.tourism.domain.catalog.ReactionType
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotIntro

/** spot 상세 조회(CQRS 읽기). 본문과 하위 필드는 각자 따로 조회한다 — 선택된 필드만 I/O가 일어난다. */
interface SpotDetailQueryUseCase {
    /**
     * 카탈로그 단건 상세 본문. 갤러리·운영정보·이용안내·좋아요 수는 아래 메서드로 따로 조회한다.
     * - 본문이 upstream에 없어도 성공한다 — overview·homepage·tel만 null이 된다.
     * - `SPOT_NOT_FOUND`는 id 자체가 해석되지 않을 때만 발생한다.
     * - [locale]에 원본이 없으면 거부한다.
     */
    fun spotDetail(catalogId: Long, locale: DomainLocale): SpotDetailView

    /** 갤러리(요청 시에만). 캐시 → 미스면 detailImage2 1콜. */
    fun spotGallery(catalogId: Long, locale: DomainLocale): List<SpotImage>

    /** 운영 정보(요청 시에만). 캐시 → 미스면 detailIntro2 1콜. 표시할 값이 없으면 null. */
    fun spotIntro(catalogId: Long, category: SpotCategory, locale: DomainLocale): SpotIntro?

    /** 이용안내 항목(요청 시에만). 캐시 → 미스면 detailInfo2 1콜. */
    fun spotInfoItems(catalogId: Long, category: SpotCategory, locale: DomainLocale): List<SpotInfoItem>

    /**
     * 좋아요 수(요청 시에만). 캐시하지 않고 매번 집계한다.
     * - [locale] 안에서만 센다(SPEC-0029 AC-13.5).
     */
    fun spotLikeCount(catalogId: Long, locale: DomainLocale): Long

    /**
     * 이 회원이 [locale]에서 이 장소에 남긴 최신 반응.
     * - 반응한 적 없으면 null이며, 다른 언어에서 누른 것은 보이지 않는다(AC-13.2).
     */
    fun spotViewerReaction(catalogId: Long, memberId: Long, locale: DomainLocale): ReactionType?
}
