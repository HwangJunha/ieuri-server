package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.MessageRetentionUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.domain.aggregate.Message
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 보관 기간이 지난 메시지 삭제(SPEC-0017 R16, POL-050).
 *
 * **기준은 메시지 생성 시각이다** — 대화의 마지막 활동 시각이 아니다. 활발한 대화라고 해서
 * 개별 메시지의 보관 기간이 늘어나지 않는다(AC16-3). 기준이 단순해야 "언제 지워지는가"를
 * 사용자에게 설명할 수 있다.
 *
 * **삭제는 물리 삭제다.** 소프트 삭제로 두면 보관 기간을 정한 의미가 없다 — 지웠다고 하면서
 * 데이터는 남아 있게 된다.
 *
 * 한 회차만 지우고 반복은 호출자(배치 태스클릿)가 한다. 트랜잭션이 회차 단위로 끊겨야
 * 락과 WAL이 함께 커지지 않는다.
 */
@Service
class MessageRetentionService(
    private val messagePurgePort: MessagePurgePort,
    private val clockPort: ClockPort,
) : MessageRetentionUseCase {

    @Transactional
    override fun purgeExpiredBatch(limit: Int): Int {
        require(limit > 0) { "limit은 양수여야 합니다." }
        val cutoff = Message.retentionCutoff(clockPort.now())
        return messagePurgePort.deleteCreatedBefore(cutoff, limit)
    }
}
