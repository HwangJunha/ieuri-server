package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.port.`in`.command.AuditRetentionUseCase
import org.around.ieuri.auth.application.port.out.persistence.AuditLogRetentionPort
import org.around.ieuri.auth.domain.vo.AuditRetention
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * 감사 기록 보관 기간 집행(SPEC-0032 R22, POL-014).
 *
 * POL-014가 회원 감사 로그 1년을 정했으나 **삭제 경로가 아예 없어 정책이 집행되지 않고
 * 있었다** — 고지한 기간과 실제 동작이 다르면 약관이 사실과 어긋난다.
 *
 * **대상은 회원 감사 로그뿐이다.** 위치 기록의 보관 기간은 위치정보법 신고 전이라 이 슬라이스가
 * 정하지 않으며, R21의 스위치가 꺼져 있어 정리할 행 자체가 존재하지 않는다. 다만 판정 함수는
 * 기간을 인자로 받으므로([AuditRetention]) 대상이 늘 때 판정 로직을 다시 쓰지 않는다.
 *
 * `MessageRetentionService`의 선례를 따라 **@Transactional을 붙이지 않는다** — 트랜잭션 경계는
 * 포트 구현 안에 있고, 회차 단위로 끊겨야 락과 WAL이 커지지 않는다.
 */
@Service
class AuditRetentionService(
    private val auditLogRetentionPort: AuditLogRetentionPort,
    private val clock: Clock,
) : AuditRetentionUseCase {

    override fun purgeExpiredBatch(limit: Int): Int {
        val cutoff = AuditRetention.cutoff(clock.instant(), AuditRetention.MEMBER_AUDIT)
        return auditLogRetentionPort.deleteCreatedBefore(cutoff, limit)
    }
}
