package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 앱 내부 스케줄러 트리거(SPEC-0004 R-2·AC-4). **로컬에서 배치를 띄워 두고 관찰할 때 쓴다.**
 *
 * 운영(k3s)에서는 CronJob이 스케줄을 소유하므로 이 빈은 뜨지 않는다 — `scheduler-enabled=false`.
 * 둘 다 켜면 하루에 두 번 돌기 때문에 배타적으로 쓴다. 실행 로직 자체는 [SpotSyncLauncher]가 갖고
 * 여기서는 "언제"만 정한다.
 */
@Component
@Profile(BATCH_PROFILE)
@ConditionalOnProperty(
    prefix = "ieuri.tourapi.sync",
    name = ["scheduler-enabled"],
    havingValue = "true",
)
class SpotSyncScheduler(
    private val launcher: SpotSyncLauncher,
) {
    @Scheduled(cron = "\${ieuri.tourapi.sync.cron}", zone = "\${ieuri.tourapi.sync.zone}")
    fun run() {
        launcher.launch()
    }
}
