package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.MessageRetentionUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.domain.aggregate.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 보관 기간이 지난 메시지 물리 삭제(SPEC-0017, POL-050).
 * - 기준은 메시지 생성 시각이다(대화 활동 시각 아님). 탈퇴로 가려진 메시지도 함께 지운다.
 * - 한 회차만 지우고 반복은 호출자(배치 태스클릿)가 한다.
 */
@Service
class MessageRetentionService(
    private val messagePurgePort: MessagePurgePort,
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
) : MessageRetentionUseCase {

    /**
     * 트랜잭션을 여기서 열지 않는다 — 경계는 행 삭제 포트(`ExpiredMessageRowPurger`) 안에만 있다.
     * - 여기서 열면 사진 삭제 왕복이 트랜잭션 안에서 돌아 커넥션을 오래 잡는다.
     */
    override fun purgeExpiredBatch(limit: Int): Int {
        require(limit > 0) { "limit은 양수여야 합니다." }
        val cutoff = Message.retentionCutoff(clockPort.now())
        val purged = messagePurgePort.deleteCreatedBefore(cutoff, limit)
        if (purged.deletedCount == 0) return 0

        // 첨부 사진은 행 삭제 뒤에 지운다 — 먼저 지우면 롤백 시 깨진 첨부가 남는다.
        // 개별 실패는 삼키고 건수만 로그로 남긴다.
        var photoFailures = 0
        purged.photoObjectKeys.forEach { key ->
            runCatching { objectStoragePort.delete(key) }.onFailure { photoFailures += 1 }
        }
        // 키에 memberId가 있어 로그에 싣지 않는다(POL-013).
        if (photoFailures > 0) log.warn("failed to delete {} expired DM photo objects", photoFailures)
        return purged.deletedCount
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MessageRetentionService::class.java)
    }
}
