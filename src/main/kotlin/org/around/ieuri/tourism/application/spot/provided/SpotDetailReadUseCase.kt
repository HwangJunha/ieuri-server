package org.around.ieuri.tourism.application.spot.provided

import org.around.ieuri.tourism.application.catalog.required.SpotIdentity
import org.around.ieuri.tourism.application.spot.provided.dto.KnownIntro
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem

/**
 * 상세 조회의 DB 단계. 호출마다 읽기 전용 트랜잭션 하나다 — 캐시·업스트림을 끼는 [SpotDetailQueryUseCase]가 단계마다 부른다.
 * - 적재 조회는 미적재면 null이다(빈 목록·값 없음과 구분된다).
 */
interface SpotDetailReadUseCase {
    /**
     * 카탈로그 id를 활성 언어의 원본으로 해석한다(SPEC-0029). 폴백하지 않는다.
     * - 그 언어에 없으면 SPOT_NOT_FOUND_IN_LOCALE, 카탈로그 자체가 없으면 SPOT_NOT_FOUND다(POL-043).
     */
    fun resolve(catalogId: Long, locale: DomainLocale): SpotIdentity

    /** 정의되지 않은 카테고리면 UNKNOWN_CATEGORY다. 상세 하위 필드에만 건다. */
    fun requireKnownCategory(category: SpotCategory)

    fun storedDetail(spotId: Long): SpotDetail?

    fun storedGallery(spotId: Long): List<SpotImage>?

    fun storedIntro(spotId: Long, category: SpotCategory): KnownIntro?

    fun storedInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?

    /** 본문 부재 시 응답 — 활성 언어의 카탈로그 값이다(언어 폴백 아님). overview·homepage·tel은 null이다. */
    fun absentDetail(catalogId: Long, locale: DomainLocale): SpotDetail
}
