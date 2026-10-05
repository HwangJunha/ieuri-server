package org.around.ieuri.community.adapter.client

import org.around.ieuri.community.application.shared.required.ClockPort
import org.springframework.stereotype.Component
import java.time.Instant

/** 시스템 시계. */
@Component
class SystemClockAdapter : ClockPort {
    override fun now(): Instant = Instant.now()
}
