package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/** 배치 전용 프로파일. 이 프로파일로 뜬 프로세스만 잡·런처·스케줄러 빈을 갖는다. */
const val BATCH_PROFILE = "batch"

/**
 * 관광지 동기화 배치 설정(SPEC-0004 §4.2). 실제 값은 `application-batch.yaml`에 있으며
 * **batch 프로파일로 뜬 프로세스만** 그것을 읽는다.
 *
 * @param pageSize areaBasedSyncList2의 numOfRows. **조회 경로의 `maxPageSize`(100)와 별개다** —
 *   그쪽은 클라이언트 응답 크기 상한이고 이쪽은 수집 효율이라 성격이 다르다. 설계 문서는 1000을
 *   제안하나 upstream 허용 여부는 **아직 실측되지 않았다**(이 오퍼레이션은 이 레포에서 호출된 적이
 *   없다). 거부되면 페이지 수가 10배가 되므로, 설정으로 두어 코드 수정 없이 낮출 수 있게 한다.
 *   (적재 성능은 실측했다 — 1000건 upsert에 약 0.2초, 4만 건 전량에 약 10초.)
 * @param job 실행할 잡 이름. **외부에서 지정한다**(`--ieuri.tourapi.sync.job=...`).
 *   잡이 늘어나도 런처 코드는 그대로 두고 CronJob 매니페스트가 "무엇을 돌릴지"를 소유한다.
 * @param cleanupMinRatio 사라진 항목 정리의 **급감 게이트**. 이번 수집 건수가 기존 적재 건수의
 *   이 비율 미만이면 정리를 건너뛴다. `arrange=Q` 정렬 이동으로 페이지 경계 몇 건이 누락되는 것은
 *   정상 범위지만, 수천 건 규모로 빠지면 그것은 업스트림 이상이며 그대로 지우면 대량 오삭제가 된다.
 * @param zone `runDate` 잡 파라미터를 만들 때 쓰는 시간대. 이 값이 "하루"의 경계를 정하므로
 *   중복 실행 방지(AC-6)의 기준이기도 하다. 실행 스케줄 자체는 k8s CronJob이 소유한다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.sync")
data class SpotSyncProperties(
    val pageSize: Int = 1_000,
    val job: String = "spotSyncJob",
    val cleanupMinRatio: Double = 0.5,
    val zone: String = "Asia/Seoul",
)
