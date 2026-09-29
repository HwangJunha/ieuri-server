package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationMessageRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationMessage
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.around.ieuri.alarm.domain.vo.NotificationType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 발송 경로의 DB 접근만 담는다(SPEC-0028). FCM 발송이 트랜잭션 밖에서 일어나게 한다.
 * - [PushDispatchService]의 private 메서드로 합치면 자기 호출이라 `@Transactional`이 무효가 된다.
 * - `REQUIRES_NEW`는 `@Async`가 빠져 `AFTER_COMMIT` 스레드에서 불릴 때 쓰기가 사라지는 것을 막는다.
 */
@Service
class PushDispatchStore(
    private val deviceTokenRepositoryPort: DeviceTokenRepositoryPort,
    private val preferenceRepositoryPort: NotificationPreferenceRepositoryPort,
    private val notificationMessageRepositoryPort: NotificationMessageRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 저장된 행이 없으면 켜진 것으로 본다(AC10.3). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun categoryEnabled(memberId: Long, category: NotificationCategory): Boolean = preferenceRepositoryPort
        .findByMemberIdAndCategory(memberId, category)
        ?.enabled
        ?: NotificationPreference.DEFAULT_ENABLED

    /** 수신자의 기기 전부(AC5.3). 없으면 빈 목록이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun findTokens(memberId: Long): List<DeviceToken> = deviceTokenRepositoryPort.findByMemberId(memberId)

    /** 이 종류의 언어별 문구(R13)를 언어 키 맵으로 돌려준다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun findMessages(type: NotificationType): Map<AlarmLocale, NotificationMessage> = notificationMessageRepositoryPort.findByType(type).associateBy { it.locale }

    /** FCM이 무효로 판정한 기기를 지운다(R4). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun removeInvalidTokens(deviceIds: List<String>) {
        if (deviceIds.isEmpty()) return
        val removed = deviceTokenRepositoryPort.deleteByDeviceIds(deviceIds)
        // 토큰 원문을 남기지 않는다(POL-013).
        log.info("invalid device tokens removed: count={}", removed)
    }
}
