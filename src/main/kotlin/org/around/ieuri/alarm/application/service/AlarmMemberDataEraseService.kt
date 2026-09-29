package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.port.`in`.command.AlarmMemberDataEraseUseCase
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 탈퇴 회원의 알림 데이터 파기(SPEC-0028 R3, POL-009). */
@Service
class AlarmMemberDataEraseService(
    private val deviceTokenRepositoryPort: DeviceTokenRepositoryPort,
    private val notificationPreferenceRepositoryPort: NotificationPreferenceRepositoryPort,
) : AlarmMemberDataEraseUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /** `AFTER_COMMIT` 리스너가 부르므로 REQUIRES_NEW여야 한다. REQUIRED면 삭제가 조용히 사라진다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberData(memberId: Long) {
        val tokens = deviceTokenRepositoryPort.deleteByMemberId(memberId)
        val preferences = notificationPreferenceRepositoryPort.deleteByMemberId(memberId)
        // 토큰 원문을 남기지 않는다(POL-013).
        log.info(
            "alarm data erased for withdrawn member {}: deviceTokens={} preferences={}",
            memberId,
            tokens,
            preferences,
        )
    }
}
