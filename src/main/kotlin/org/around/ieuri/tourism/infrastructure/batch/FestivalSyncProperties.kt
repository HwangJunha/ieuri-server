package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 축제 동기화 설정(SPEC-0007 §4.4). 축제 Step은 `spotSyncJob` 안에서 함께 돌지만 **설정은 별도
 * 클래스로 둔다.**
 *
 * [SpotSyncProperties]에 키를 추가하지 않는 이유는 `cleanupMinRatio` 때문이다. 두 잡의 정리 게이트가
 * 다르고(R-10: 축제는 급감 비율 게이트를 쓰지 않는다), 한 클래스에 두면 그 값이 축제에도 적용되는
 * 것처럼 읽힌다. **축제용 설정에는 `cleanupMinRatio` 자체를 두지 않는다** — 존재하지 않는 설정은
 * 실수로 켤 수 없다.
 *
 * **시간대 설정은 두지 않는다.** 실행일은 잡 파라미터 `runDate`에서 오고 그 값은
 * [SpotSyncProperties.zone]이 정한다 — 같은 잡 안의 두 Step이 서로 다른 시간대를 볼 수 있는 설정을
 * 만들 이유가 없다.
 *
 * @param pageSize searchFestival2의 numOfRows. **이 오퍼레이션은 이 레포에서 호출된 적이 없어 상한이
 *   실측되지 않았다**([TBD-3]). 관광지 수집과 같은 1000으로 시작하되 설정으로 두어, 업스트림이
 *   거부하면 코드 수정 없이 낮출 수 있게 한다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.festival-sync")
data class FestivalSyncProperties(
    val pageSize: Int = 1_000,
)
