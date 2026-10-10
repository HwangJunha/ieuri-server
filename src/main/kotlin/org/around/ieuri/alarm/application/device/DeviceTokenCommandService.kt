package org.around.ieuri.alarm.application.device

import org.around.ieuri.alarm.application.device.provided.DeviceTokenCommandUseCase
import org.around.ieuri.alarm.application.device.provided.dto.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.device.provided.dto.RegisterDeviceTokenCommand
import org.around.ieuri.alarm.application.device.required.DeviceTokenRepository
import org.around.ieuri.alarm.domain.device.DeviceToken
import org.around.ieuri.common.persistence.ExposedConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 기기 토큰 등록·해제·파기(SPEC-0028 R1~R4·R20). 로그에 토큰 원문을 남기지 않는다(POL-013). */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class DeviceTokenCommandService(
    private val repository: DeviceTokenRepository,
) : DeviceTokenCommandUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /** `deviceId` 기준 업서트(R1·R20). 기존 기기면 소유 회원까지 덮어쓴다(AC1.4). */
    override fun register(command: RegisterDeviceTokenCommand, memberId: Long) {
        val existing = repository.findByDeviceId(command.deviceId)
        val token = existing?.reregister(memberId, command.fcmToken, command.language, command.platform)
            ?: DeviceToken.register(command.deviceId, memberId, command.fcmToken, command.language, command.platform)
        repository.save(token)
    }

    /** 로그아웃 해제(R2). 없거나 다른 회원의 기기면 지우지 않고 조용히 끝난다. */
    override fun delete(command: DeleteDeviceTokenCommand, memberId: Long) {
        val existing = repository.findByDeviceId(command.deviceId) ?: return
        if (!existing.ownedBy(memberId)) return
        repository.deleteByDeviceId(command.deviceId)
    }

    /** `AFTER_COMMIT` 리스너가 부르므로 REQUIRES_NEW여야 한다 — REQUIRED면 삭제가 조용히 사라진다. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberTokens(memberId: Long) {
        val tokens = repository.deleteByMemberId(memberId)
        log.info("alarm device tokens erased for withdrawn member {}: deviceTokens={}", memberId, tokens)
    }

    /** 발송 경로(`@Async`·`AFTER_COMMIT`)에서 불린다 — REQUIRES_NEW라 호출 스레드의 트랜잭션과 무관하다. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun removeInvalid(deviceIds: List<String>) {
        if (deviceIds.isEmpty()) return
        val removed = repository.deleteByDeviceIds(deviceIds)
        log.info("invalid device tokens removed: count={}", removed)
    }
}
