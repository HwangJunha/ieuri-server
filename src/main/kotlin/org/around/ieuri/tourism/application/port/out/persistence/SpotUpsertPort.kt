package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import java.time.Instant

/**
 * upsert가 필요한 관광지 목록 쓰기. **생 SQL 어댑터가 이 포트 뒤에 있다** — JPA에 이식 가능한
 * upsert가 없어 `INSERT ... ON CONFLICT`를 써야 하기 때문이다.
 *
 * [SpotStorePort]와 갈라 두는 이유는 `infrastructure → infrastructure` 주입을 막기 위해서다.
 * 어댑터가 다른 어댑터를 주입받으면 "어느 경로가 생 SQL을 쓰는가"가 애플리케이션에서 보이지 않는다.
 */
interface SpotUpsertPort {
    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 전체 컬럼을 덮어쓴다.
     * [syncedAt]은 건드린 행의 `last_synced_at`에 기록된다.
     *
     * @return 실제로 upsert된 행 수.
     */
    fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int
}
