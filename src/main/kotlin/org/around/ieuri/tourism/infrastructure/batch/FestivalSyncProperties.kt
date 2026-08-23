package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 축제 동기화 설정. 축제 Step은 `spotSyncJob` 안에서 함께 돌지만 설정은 별도 클래스로 둔다.
 *
 * 시간대 키를 **일부러 두지 않는다** — 실행일은 [SpotSyncProperties.zone]이 정한 잡 파라미터
 * `runDate`에서 온다.
 *
 * @param pageSize searchFestival2의 numOfRows. 업스트림이 거부하면 코드 수정 없이 낮출 수 있다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.festival-sync")
data class FestivalSyncProperties(
    val pageSize: Int = 1_000,
)
