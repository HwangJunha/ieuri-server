package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.community.application.port.out.client.ClockPort
import org.springframework.stereotype.Component
import java.time.Instant

/** 시스템 시계. 단위 테스트는 이 어댑터 대신 고정 시계를 주입한다. */
@Component
class SystemClockAdapter : ClockPort {
    override fun now(): Instant = Instant.now()
}
