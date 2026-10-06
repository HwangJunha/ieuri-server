package org.around.ieuri.tourism.adapter.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 축제 동기화 설정. 축제 Step은 `spotSyncJob` 안에서 돈다.
 * - 시간대 키가 없다 — 실행일은 잡 파라미터 `runDate`([SpotSyncProperties.zone])에서 온다.
 *
 * @param pageSize searchFestival2의 numOfRows.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.festival-sync")
data class FestivalSyncProperties(
    val pageSize: Int = 1_000,
)
