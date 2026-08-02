package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 관광지 동기화 배치 설정(SPEC-0004 §4.2). 실제 값은 `application-batch.yaml`에 있으며
 * **batch 프로파일로 뜬 프로세스만** 그것을 읽는다. API 서버는 [enabled]=false 기본값을 쓴다.
 *
 * @param pageSize areaBasedSyncList2의 numOfRows. **조회 경로의 `maxPageSize`(100)와 별개다** —
 *   그쪽은 클라이언트 응답 크기 상한이고 이쪽은 수집 효율이라 성격이 다르다. 설계 문서는 1000을
 *   제안하나 upstream 허용 여부는 **아직 실측되지 않았다**(이 오퍼레이션은 이 레포에서 호출된 적이
 *   없다). 거부되면 페이지 수가 10배가 되므로, 설정으로 두어 코드 수정 없이 낮출 수 있게 한다.
 *   (적재 성능은 실측했다 — 1000건 upsert에 약 0.2초, 4만 건 전량에 약 10초.)
 * @param enabled 배치 기능 자체의 활성 여부. **기본 false** — API 서버 프로세스에서 스케줄러가
 *   살아나 배치가 의도치 않게 도는 것을 막는다.
 * @param schedulerEnabled 실행 트리거를 **누가 소유하는가**.
 *   - `false`(기본): 외부 트리거(k3s CronJob). 부팅 즉시 1회 실행하고 프로세스가 종료된다.
 *   - `true`: 앱 내부 `@Scheduled`. 프로세스가 떠 있으면서 [cron] 시각에 실행한다(로컬 관찰용).
 *   둘 다 켜면 하루에 두 번 도므로 배타적으로 쓴다.
 * @param cron 내부 스케줄러 사용 시 실행 주기. 기본은 매일 04:00(KST) — 트래픽이 가장 적은 시간대.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.sync")
data class SpotSyncProperties(
    val pageSize: Int = 1_000,
    val enabled: Boolean = false,
    val schedulerEnabled: Boolean = false,
    val cron: String = "0 0 4 * * *",
    val zone: String = "Asia/Seoul",
)
