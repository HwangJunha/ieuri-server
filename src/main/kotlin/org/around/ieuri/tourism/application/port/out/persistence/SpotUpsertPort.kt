package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import java.time.Instant

/** upsert가 필요한 관광지 목록 쓰기. `INSERT ... ON CONFLICT`라 생 SQL 어댑터가 구현한다. */
interface SpotUpsertPort {
    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 전체 컬럼을 덮어쓴다.
     * [syncedAt]은 건드린 행의 `last_synced_at`에 기록된다.
     *
     * @return 실제로 upsert된 행 수.
     */
    fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int
}
