package org.around.ieuri.alarm.infrastructure.adapter.`in`.event

import org.around.ieuri.alarm.application.port.`in`.command.AlarmMemberDataEraseUseCase
import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴한 회원의 기기 토큰과 수신 설정을 지운다(SPEC-0028 R3, POL-009).
 *
 * **`MemberPersistenceAdapter.deleteMember`의 목록에 넣지 않는 이유는 스키마가 달라서다.**
 * alarm 표는 `member`를 FK로 참조하지 않으므로 탈퇴를 막지 않고, 도메인 간 정리는
 * community·tourism과 같은 이벤트 경로를 쓴다.
 *
 * 클래스명에 도메인을 붙이는 것은 관례다 — Spring이 단순 클래스명으로 빈 이름을 지어
 * `MemberWithdrawnListener`가 여럿이면 기동이 막힌다.
 */
@Component
class AlarmMemberWithdrawnListener(
    private val memberDataEraseUseCase: AlarmMemberDataEraseUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 실패해도 형제 리스너를 막지 않는다 — 각 도메인의 정리는 서로 독립이다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) {
        try {
            memberDataEraseUseCase.eraseMemberData(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase alarm data for withdrawn member {}", event.memberId, e)
        }
    }
}
