package org.around.ieuri.tourism.application.port.out.queue

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro

/**
 * 상세 write-behind 투입구(SPEC-0004 R-14·R-15). **호출 즉시 반환해야 한다** — 조회 응답이 적재
 * 완료를 기다리지 않는 것이 이 포트의 존재 이유다(AC-39).
 *
 * 구현체는 **어떤 경우에도 예외를 던지지 않는다.** 적재는 캐시 충전이지 데이터 보존이 아니므로,
 * 큐가 가득 차거나 종료 중이면 조용히 버린다(AC-40·AC-42·AC-45). 버려진 항목은 다음 조회 때 다시
 * 큐에 들어오므로 정합성이 깨지지 않는다. 폐기 건수는 구현체가 카운터로 남긴다(AC-43).
 *
 * 메서드가 오퍼레이션별로 나뉜 것은 저장 포트와 같은 이유다 — 요청된 필드만 적재한다.
 */
interface SpotDetailWriteQueuePort {
    fun enqueueDetail(contentId: String, locale: DomainLocale, detail: SpotDetail)
    fun enqueueGallery(contentId: String, locale: DomainLocale, gallery: List<SpotImage>)
    fun enqueueIntro(contentId: String, locale: DomainLocale, category: SpotCategory, intro: SpotIntro?)
    fun enqueueInfoItems(contentId: String, locale: DomainLocale, category: SpotCategory, items: List<SpotInfoItem>)
}
