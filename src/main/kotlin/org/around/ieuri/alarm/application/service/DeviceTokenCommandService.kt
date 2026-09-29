package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.command.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.command.RegisterDeviceTokenCommand
import org.around.ieuri.alarm.application.port.`in`.command.DeviceTokenCommandUseCase
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 기기 토큰 등록·해제(SPEC-0028 R1·R2·R20). 탈퇴 파기는 [AlarmMemberDataEraseService]에 있다. */
@Service
class DeviceTokenCommandService(
    private val deviceTokenRepositoryPort: DeviceTokenRepositoryPort,
) : DeviceTokenCommandUseCase {

    /** `deviceId` 기준 업서트(R1·R20). 기존 기기면 소유 회원까지 덮어쓴다(AC1.4). */
    @Transactional
    override fun register(command: RegisterDeviceTokenCommand, memberId: Long) {
        val existing = deviceTokenRepositoryPort.findByDeviceId(command.deviceId)
        if (existing == null) {
            deviceTokenRepositoryPort.save(
                DeviceToken.create(
                    deviceId = command.deviceId,
                    memberId = memberId,
                    fcmToken = command.fcmToken,
                    language = command.language,
                    platform = command.platform,
                ),
            )
            return
        }

        existing.reregister(
            memberId = memberId,
            fcmToken = command.fcmToken,
            language = command.language,
            platform = command.platform,
        )
        deviceTokenRepositoryPort.update(existing)
    }

    /** 로그아웃 해제(R2). 없거나 다른 회원의 기기면 지우지 않고 조용히 끝난다. */
    @Transactional
    override fun delete(command: DeleteDeviceTokenCommand, memberId: Long) {
        val existing = deviceTokenRepositoryPort.findByDeviceId(command.deviceId) ?: return
        if (!existing.ownedBy(memberId)) return
        deviceTokenRepositoryPort.deleteByDeviceId(command.deviceId)
    }
}
