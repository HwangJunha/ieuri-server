package org.around.ieuri.alarm.application.device

import org.around.ieuri.alarm.application.device.provided.DeviceTokenQueryUseCase
import org.around.ieuri.alarm.application.device.provided.dto.DeviceTokenView
import org.around.ieuri.alarm.application.device.required.DeviceTokenQueryPort
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 발송 대상 기기 조회. 발송 경로(`@Async`·`AFTER_COMMIT`)에서 불려 REQUIRES_NEW다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class DeviceTokenQueryService(
    private val deviceTokenQueryPort: DeviceTokenQueryPort,
) : DeviceTokenQueryUseCase {

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW, readOnly = true)
    override fun tokensOf(memberId: Long): List<DeviceTokenView> = deviceTokenQueryPort.findByMemberId(memberId)
}
