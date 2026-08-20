package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.LocalDate

/**
 * 축제 정보 조회·정리 아웃바운드 포트. [SpotStorePort]와 같은 규율로, 적재(upsert)는 생 SQL 경로라
 * [FestivalUpsertPort]로 갈라져 있다.
 *
 * 삭제 메서드가 두 갈래인 것은 정리 대상이 두 갈래이기 때문이다 — 이미 종료된 행([deleteEnded])과
 * 이번 수집 결과에 없는 행([deleteMissing]). 두 경우 모두 `spot` 부모 행을 지우며 축제 정보는
 * CASCADE로 따라간다.
 */
interface FestivalStorePort {
    /**
     * [language]에서 종료일이 [asOf]보다 **이전인** 축제를 삭제한다. 종료일 당일은 지우지 않으며,
     * 이 경계는 도메인 [org.around.ieuri.tourism.domain.vo.FestivalPeriod.isEndedAsOf]와 같아야 한다.
     *
     * 지우는 대상은 `spot` 행이고 `spot_festival`은 FK CASCADE로 따라 사라진다.
     *
     * @return 삭제된 행 수.
     */
    fun deleteEnded(language: DomainLocale, asOf: LocalDate): Int

    /**
     * [language]의 축제 중 [survivingContentIds]에 **없는** 것을 삭제한다. 대상이 축제로 한정되어
     * 관광지 전반을 건드리지 않는다.
     *
     * **호출자가 게이트를 통과시킨 뒤에만 부른다** — 이 메서드는 안전 판정을 하지 않으므로,
     * 빈 집합으로 부르면 그 언어의 축제가 전량 삭제된다.
     *
     * @return 삭제된 행 수.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int

    /**
     * [language]에서 종료일이 [asOf]보다 이전인 축제의 contentId 집합. 관광지 수집 Step이 적재
     * 대상에서 제외할 집합이며, 이 제외가 없으면 [deleteEnded]가 지운 행사를 같은 잡의 관광지
     * 수집이 그대로 되살린다.
     */
    fun findEndedContentIds(language: DomainLocale, asOf: LocalDate): Set<String>
}
