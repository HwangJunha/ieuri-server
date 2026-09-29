package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncFestivalCommand
import java.time.Instant

/** 축제 정보 upsert 포트. */
interface FestivalUpsertPort {
    /**
     * 한 페이지를 upsert하고 실제 적재된 행 수를 돌려준다. 자연키 (contentId, language) 충돌 시 덮어쓴다.
     * - 대응하는 `spot` 행이 없는 항목은 조용히 건너뛴다.
     */
    fun upsertAll(festivals: List<SyncFestivalCommand>, syncedAt: Instant): Int
}
