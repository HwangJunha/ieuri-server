package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import java.time.Instant

/**
 * upsert가 필요한 축제 정보 쓰기. [SpotUpsertPort]와 같은 이유로 [FestivalStorePort]와 갈라져 있다 —
 * 생 SQL 어댑터가 이 포트 뒤에 있고, 어댑터가 어댑터를 주입받지 않게 한다.
 */
interface FestivalUpsertPort {
    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 축제 전용 컬럼을 덮어쓴다.
     * [syncedAt]은 `last_synced_at`에 기록된다.
     *
     * **대응하는 `spot` 행이 없는 항목은 조용히 건너뛴다** — 부모 없는 축제는 정상적으로 발생할 수
     * 있으므로 FK 위반으로 Step을 실패시키지 않는다.
     *
     * @return 실제로 적재된 행 수(부모가 없어 건너뛴 항목은 제외).
     */
    fun upsertAll(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int
}
