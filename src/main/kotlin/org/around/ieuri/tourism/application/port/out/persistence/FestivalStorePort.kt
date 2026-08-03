package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.Instant
import java.time.LocalDate

/**
 * 축제 정보 적재 아웃바운드 포트(SPEC-0007). [SpotStorePort]와 같은 자리·같은 규율이다 —
 * **쓰기 전용**이며, application은 이 인터페이스만 보고 어댑터·테이블을 알지 않는다.
 *
 * 삭제 메서드가 두 갈래인 것은 정리 대상이 두 갈래이기 때문이다(스펙 §상위 결정):
 * **이미 종료된 행**([deleteEnded])과 **이번 수집 결과에 없는 행**([deleteMissing]).
 * 두 경우 모두 `spot` 부모 행을 지우며 축제 정보는 CASCADE로 따라간다.
 */
interface FestivalStorePort {
    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 축제 전용 컬럼을 덮어쓴다(R-7).
     *
     * **대응하는 `spot` 행이 없는 항목은 조용히 건너뛴다**(R-2·AC-5). FK 위반으로 Step을 실패시키지
     * 않는다 — 축제 수집이 관광지 수집보다 뒤에 오지만 두 오퍼레이션의 수집 조건이 완전히 같지는
     * 않아(예: `arrange=Q`의 대표이미지 필수) 부모 없는 축제가 정상적으로 발생할 수 있다.
     *
     * [syncedAt]은 이번 실행 시각으로 `last_synced_at`에 기록된다.
     * @return 실제로 적재된 행 수(부모 없어 건너뛴 항목은 제외 — AC-39의 언어별 건수).
     */
    fun upsertAll(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int

    // [SpotStorePort]에 있는 countByLanguage는 여기 두지 않는다. 그 메서드의 유일한 쓰임은 급감 비율
    // 게이트인데, 축제는 그 게이트를 쓰지 않는다(R-10·AC-36). 대칭을 위해 남기면 호출자 없는 메서드가
    // 된다.

    /**
     * [language]에서 종료일이 [asOf]보다 **이전인** 축제를 삭제한다(R-8).
     *
     * **종료일 당일은 지우지 않는다**(AC-27) — 도메인 [org.around.ieuri.tourism.domain.vo.FestivalPeriod.isEndedAsOf]와
     * 같은 경계다. 판정 기준이 두 곳에 있으므로 어긋나지 않도록 도메인 쪽 규칙을 SQL이 그대로 옮긴다.
     *
     * 지우는 대상은 **`spot` 행**이며 `spot_festival`은 FK CASCADE로 따라 사라진다(AC-30).
     * `spot_festival`만 지우는 경로를 만들지 않는다 — 관광지 행이 남으면 축제 정보 없는 축제가
     * 조회되고, 그것을 다음 실행이 다시 축제로 인식할 방법도 없다(AC-7).
     *
     * @return 삭제된 행 수.
     */
    fun deleteEnded(language: DomainLocale, asOf: LocalDate): Int

    /**
     * [language]의 축제 중 [survivingContentIds]에 **없는** 것을 삭제한다. [SpotStorePort.deleteMissing]과
     * 같은 집합 기준이되 대상이 축제로 한정된다(AC-22·AC-29) — 관광지 전반을 건드리지 않는다.
     *
     * [deleteEnded]와 마찬가지로 `spot` 행을 지우고 축제 정보는 CASCADE로 따라간다.
     *
     * **호출자가 게이트를 통과시킨 뒤에만 부른다** — 이 메서드는 안전 판정을 하지 않는다.
     * @return 삭제된 행 수.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int

    /**
     * [language]에서 종료일이 [asOf]보다 이전인 축제의 contentId 집합(R-9).
     *
     * 관광지 수집 Step이 **적재 대상에서 제외할 집합**이다. `areaBasedSyncList2`에는 `contentTypeId`
     * 요청 파라미터가 없어 축제가 이미 그 경로로 `spot`에 적재되므로, 이 제외가 없으면 R-8이 지운
     * 행사를 같은 잡의 관광지 수집이 그대로 되살린다(AC-31·AC-32).
     *
     * 잡 시작 시 **한 번만** 읽어 Step에 주입한다 — 축제는 절대 건수가 적어 집합이 작고, 하루 1회
     * 배치에서 잡 시작 시점의 스냅샷으로 충분하다. 페이지마다 조회하면 페이지당 질의가 하나씩 는다.
     */
    fun findEndedContentIds(language: DomainLocale, asOf: LocalDate): Set<String>
}
