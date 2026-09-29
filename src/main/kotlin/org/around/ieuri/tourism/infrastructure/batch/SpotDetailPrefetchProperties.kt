package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 상세 사전 수집 배치 설정(SPEC-0037, POL-044). 실제 값은 `application-batch.yaml`에 있다.
 *
 * @param batchSize 한 번에 뽑는 대상 수. 트랜잭션·커서 전진의 경계다.
 * @param refreshAfter 마지막 확보로부터 이 기간이 지나면 다시 확보한다(FR-007).
 * @param callsPerSecond 한 갈래가 초당 던지는 upstream 호출 수. 실측 한도(초당 50)보다 낮게 잡는다.
 * @param dailyLimit 한 갈래가 하루에 처리할 장소 수(=호출 수) 상한. 쿼터 일부를 사용자 조회 시점 확보(FR-010) 몫으로 남긴다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.detail-prefetch")
data class SpotDetailPrefetchProperties(
    val batchSize: Int = 200,
    val refreshAfter: Duration = Duration.ofDays(30),
    val callsPerSecond: Int = 40,
    val dailyLimit: Int = 90_000,
)
