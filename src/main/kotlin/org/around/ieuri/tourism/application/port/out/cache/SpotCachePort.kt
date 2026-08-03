package org.around.ieuri.tourism.application.port.out.cache

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/**
 * 관광지 캐시 아웃바운드 포트(SPEC-0003 §4). **오퍼레이션 단위로 키를 나눈다** — GraphQL 필드별 지연 조회와
 * 짝이 맞아야 하기 때문이다(본문만 요청한 조회가 갤러리까지 캐시에 채우거나, 갤러리만 요청했는데 통짜 캐시가
 * 미스 나는 상황을 피한다).
 *
 * 캐시 대상은 **TourAPI 파생 필드**뿐이며, likeCount는 담지 않는다(매 조회 실시간 집계 — AC-14).
 * 캐시는 최적화지 정합성 소스가 아니다. 구현체는 저장소 장애를 삼키고(get→null, put→무시) upstream 폴백을
 * 허용해야 한다. 키는 (오퍼레이션, contentId, language[, category]) — contentId가 언어별로 갈리므로 언어를
 * 포함한다(AC-19). intro·info는 카테고리가 응답을 가르므로 키에 함께 넣는다.
 */
interface SpotCachePort {
    /** 상세 본문. 미스·장애면 null. */
    fun getDetail(spotId: Long): SpotDetail?
    fun putDetail(spotId: Long, detail: SpotDetail)

    /** 갤러리. 미스·장애면 null(빈 목록과 구분해야 하므로 nullable). */
    fun getGallery(spotId: Long): List<SpotImage>?
    fun putGallery(spotId: Long, gallery: List<SpotImage>)

    /**
     * 운영 정보. **미스와 "값 없음"을 구분해야 한다** — intro는 정상적으로 null일 수 있어서,
     * 히트했지만 값이 null인 경우를 [CachedIntro.value]로 감싸 표현한다. 미스·장애면 래퍼 자체가 null.
     */
    fun getIntro(spotId: Long, category: SpotCategory): CachedIntro?
    fun putIntro(spotId: Long, category: SpotCategory, intro: SpotIntro?)

    /** 이용안내 항목. 미스·장애면 null. */
    fun getInfoItems(spotId: Long, category: SpotCategory): List<SpotInfoItem>?
    fun putInfoItems(spotId: Long, category: SpotCategory, items: List<SpotInfoItem>)
}

/** 캐시 히트 여부와 "값이 null임"을 구분하기 위한 래퍼(intro 전용). */
data class CachedIntro(val value: SpotIntro?)
