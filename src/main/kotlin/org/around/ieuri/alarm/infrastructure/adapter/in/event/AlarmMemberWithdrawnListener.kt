package org.around.ieuri.alarm.infrastructure.adapter.`in`.event

import org.around.ieuri.alarm.application.port.`in`.command.AlarmMemberDataEraseUseCase
import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴한 회원의 기기 토큰과 수신 설정을 지운다(SPEC-0028 R3, POL-009).
 * - 클래스명의 `Alarm` 접두어를 빼면 다른 도메인의 리스너와 빈 이름이 겹쳐 기동이 막힌다.
 */
@Component
class AlarmMemberWithdrawnListener(
    private val memberDataEraseUseCase: AlarmMemberDataEraseUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실패해도 예외를 삼켜 다른 도메인의 리스너를 막지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) {
        try {
            memberDataEraseUseCase.eraseMemberData(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase alarm data for withdrawn member {}", event.memberId, e)
        }
    }
}
