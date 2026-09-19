package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * 상세 사전 수집 배치 설정(SPEC-0037). 실제 값은 `application-batch.yaml`에 있다.
 *
 * `SpotSyncProperties`를 넓히지 않고 따로 둔 이유는 잡이 별개이기 때문이다 — 목록 동기화만 돌리는
 * CronJob이 상세 수집 설정까지 읽을 이유가 없고, 잡별 분리는 기존 관례이기도 하다
 * (`FestivalSyncProperties`·`TourStatsSyncProperties`).
 *
 * **전부 근거가 확정되지 않은 값이라 설정으로 뺐다**(POL-044). 재확보 30일은 사용자가 정했고,
 * 동시 호출 수는 초당 한도 응답을 보고 조정한다.
 *
 * @param batchSize 한 번에 뽑는 대상 수. 이 단위가 곧 트랜잭션·커서 전진의 경계다.
 * @param refreshAfter 마지막 확보로부터 이 기간이 지나면 다시 확보한다(FR-007).
 * @param concurrency 동시에 던지는 upstream 호출 수. 초당 한도에 걸리면 낮춘다.
 * @param dailyLimit **한 갈래가 하루에 처리할 장소 수의 상한.** 장소 하나가 호출 하나이므로
 *   곧 호출 수다. 오퍼레이션당 쿼터가 100,000이라 기본값 90,000은 10,000건을 사용자 조회 시점
 *   확보(FR-010) 몫으로 남긴다 — 배치가 쿼터를 다 쓰면 그날 조회가 upstream에 닿지 못한다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.detail-prefetch")
data class SpotDetailPrefetchProperties(
    val batchSize: Int = 200,
    val refreshAfter: Duration = Duration.ofDays(30),
    val concurrency: Int = 20,
    val dailyLimit: Int = 90_000,
)
