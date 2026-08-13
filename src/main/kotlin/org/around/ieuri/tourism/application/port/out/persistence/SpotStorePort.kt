package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.Instant

/**
 * 관광지 목록 적재 아웃바운드 포트. **쓰기 전용** — 조회는 읽기 포트가 따로 담당한다.
 */
interface SpotStorePort {
    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 전체 컬럼을 덮어쓴다.
     * [syncedAt]은 건드린 행의 `last_synced_at`에 기록된다.
     *
     * @return 실제로 upsert된 행 수.
     */
    fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int

    /**
     * [language]에서 [survivingContentIds]에 **없는** 행을 삭제한다.
     *
     * **호출자가 게이트를 통과시킨 뒤에만 부른다** — 이 메서드는 안전 판정을 하지 않으므로,
     * 빈 집합으로 부르면 그 언어가 전량 삭제된다.
     *
     * @return 삭제된 행 수.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int
}
