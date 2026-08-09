package org.around.ieuri.common.time

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 시계 빈. 여러 도메인이 시간을 읽으므로 공유 커널에 둔다 — 한 도메인의 설정 클래스에 두면
 * 다른 도메인이 그 도메인의 존재에 조용히 의존하게 되고, 먼저 추출되는 순간 부팅이 깨진다.
 *
 * 시스템 시각을 직접 읽는 대신 주입하는 이유는 시간에 의존하는 판정(만료, 진행 중 여부)을
 * 단위 테스트로 고정하기 위해서다.
 *
 * **UTC다.** 날짜 경계가 의미를 갖는 판정(예: '오늘'이 행사 기간에 포함되는가)은 이 시계에서
 * 곧바로 `LocalDate`를 뽑지 말고 의도한 시간대를 명시해야 한다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
