package org.around.ieuri.tourism.application.event

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/**
 * upstream에서 상세를 가져왔을 때 발행되는 이벤트(SPEC-0004 R-14). 리스너가 이를 받아 DB에
 * 적재하며, **조회 응답은 적재를 기다리지 않는다**(AC-39).
 *
 * 오퍼레이션별로 이벤트를 나눈 것은 저장 포트와 같은 이유다 — 요청된 필드만 적재한다.
 * auth 도메인의 `VerificationEmailRequested`와 같은 관례를 따른다(POL-028: 이벤트는
 * Spring ApplicationEvent, Kafka 이관은 후속).
 */
data class SpotDetailFetched(
    val contentId: String,
    val locale: DomainLocale,
    val detail: SpotDetail,
)

data class SpotGalleryFetched(
    val contentId: String,
    val locale: DomainLocale,
    val gallery: List<SpotImage>,
)

/** [intro]는 정상적으로 null일 수 있다 — "값 없음"도 적재해야 다음 조회가 미스로 오판하지 않는다. */
data class SpotIntroFetched(
    val contentId: String,
    val locale: DomainLocale,
    val category: SpotCategory,
    val intro: SpotIntro?,
)

data class SpotInfoItemsFetched(
    val contentId: String,
    val locale: DomainLocale,
    val category: SpotCategory,
    val items: List<SpotInfoItem>,
)
