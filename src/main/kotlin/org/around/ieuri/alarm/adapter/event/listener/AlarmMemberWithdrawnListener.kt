package org.around.ieuri.alarm.adapter.event.listener

import org.around.ieuri.alarm.application.device.provided.DeviceTokenCommandUseCase
import org.around.ieuri.alarm.application.preference.provided.NotificationPreferenceCommandUseCase
import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴한 회원의 기기 토큰과 수신 설정을 지운다(SPEC-0028 R3, POL-009). 애그리게이트마다 메서드 하나다(SPEC-0039 D-65).
 * - 각 메서드가 자기 실패를 삼킨다 — 한쪽이 실패해도 다른 쪽과 다른 도메인의 리스너를 막지 않는다.
 * - 클래스명의 `Alarm` 접두어를 빼면 다른 도메인의 리스너와 빈 이름이 겹쳐 기동이 막힌다.
 */
@Component
class AlarmMemberWithdrawnListener(
    private val deviceTokenCommandUseCase: DeviceTokenCommandUseCase,
    private val preferenceCommandUseCase: NotificationPreferenceCommandUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun eraseDeviceTokens(event: MemberWithdrawn) {
        try {
            deviceTokenCommandUseCase.eraseMemberTokens(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase alarm device tokens for withdrawn member {}", event.memberId, e)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun erasePreferences(event: MemberWithdrawn) {
        try {
            preferenceCommandUseCase.eraseMemberPreferences(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase alarm preferences for withdrawn member {}", event.memberId, e)
        }
    }
}
