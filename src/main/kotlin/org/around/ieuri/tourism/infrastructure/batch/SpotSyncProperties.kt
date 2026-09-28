package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/** 배치 전용 프로파일. 이 프로파일로 뜬 프로세스만 잡·런처·스케줄러 빈을 갖는다. */
const val BATCH_PROFILE = "batch"

/**
 * 관광지 동기화 배치 설정. 실제 값은 `application-batch.yaml`에 있다.
 *
 * @param pageSize areaBasedSyncList2의 numOfRows. 조회 경로의 `maxPageSize`와 별개다.
 * @param job 실행할 잡 이름(`--ieuri.tourapi.sync.job=...`).
 * @param zone `runDate`를 만드는 시간대. "하루"의 경계, 곧 중복 실행 방지의 기준이다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.sync")
data class SpotSyncProperties(
    val pageSize: Int = 1_000,
    val job: String = "spotSyncJob",
    val zone: String = "Asia/Seoul",
)
