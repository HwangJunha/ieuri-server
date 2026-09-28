package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.LocalDate

/**
 * 축제 정보 조회·정리 아웃바운드 포트. 적재(upsert)는 [FestivalUpsertPort]가 맡는다.
 * - 삭제는 `spot` 부모 행을 지우며, `spot_festival`은 FK CASCADE로 따라간다.
 */
interface FestivalStorePort {
    /**
     * [language]에서 종료일이 [asOf]보다 이전인 축제를 삭제하고 삭제 행 수를 돌려준다. 종료일 당일은 지우지 않는다.
     * - 이 경계는 [org.around.ieuri.tourism.domain.vo.FestivalPeriod.isEndedAsOf]와 같아야 한다.
     */
    fun deleteEnded(language: DomainLocale, asOf: LocalDate): Int

    /**
     * [language]의 축제 중 [survivingContentIds]에 없는 것을 삭제하고 삭제 행 수를 돌려준다.
     * - 안전 판정을 하지 않는다 — 빈 집합으로 부르면 그 언어의 축제가 전량 삭제된다.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int

    /**
     * [language]에서 종료일이 [asOf]보다 이전인 축제의 contentId 집합.
     * - 관광지 수집 Step이 이것을 제외하지 않으면 [deleteEnded]가 지운 행사를 되살린다.
     */
    fun findEndedContentIds(language: DomainLocale, asOf: LocalDate): Set<String>
}
