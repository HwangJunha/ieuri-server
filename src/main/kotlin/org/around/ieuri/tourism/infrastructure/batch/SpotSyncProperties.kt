package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 관광지 동기화 배치 설정(SPEC-0004 §4.2).
 *
 * @param pageSize areaBasedSyncList2의 numOfRows. **조회 경로의 `maxPageSize`(100)와 별개다** —
 *   그쪽은 클라이언트 응답 크기 상한이고 이쪽은 수집 효율이라 성격이 다르다. 설계 문서는 1000을
 *   제안하나 **아직 실측되지 않았다**(이 오퍼레이션은 이 레포에서 호출된 적이 없다). 거부되면
 *   페이지 수가 10배가 되므로, 설정으로 두어 코드 수정 없이 낮출 수 있게 한다.
 * @param enabled 스케줄 자동 실행 여부. 기본 false — 배치가 upstream 호출 한도를 소모하므로
 *   개발·테스트 환경에서 의도치 않게 도는 것을 막는다. 운영에서 명시적으로 켠다.
 * @param cron 실행 주기. 기본은 매일 04:00(KST) — 트래픽이 가장 적은 시간대.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.sync")
data class SpotSyncProperties(
    val pageSize: Int = 1_000,
    val enabled: Boolean = false,
    val cron: String = "0 0 4 * * *",
    val zone: String = "Asia/Seoul",
)
