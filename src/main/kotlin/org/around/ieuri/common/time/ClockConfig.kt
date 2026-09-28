package org.around.ieuri.common.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시계 빈(UTC). 날짜 경계가 의미를 갖는 판정은 이 시계에서 곧바로 `LocalDate`를 뽑지 말고 시간대를 명시한다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
