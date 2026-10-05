package org.around.ieuri.community.application.conversation

import org.around.ieuri.community.application.conversation.provided.ExpiredMessageDeleteUseCase
import org.around.ieuri.community.application.conversation.provided.MessageRetentionUseCase
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 보관 기간이 지난 메시지 삭제(POL-050). 탈퇴로 가려진 메시지도 함께 지운다.
 * - 트랜잭션을 열지 않는다: 행 삭제는 [ExpiredMessageDeleteUseCase]가 커밋하고, 사진 삭제는 그 뒤 여기서 한다.
 * - 사진을 먼저 지우면 롤백 시 깨진 첨부가 남는다. 개별 실패는 삼키고 건수만 남긴다.
 */
@Service
class MessageRetentionService(
    private val expiredMessageDeleteUseCase: ExpiredMessageDeleteUseCase,
    private val objectStoragePort: ObjectStoragePort,
) : MessageRetentionUseCase {

    override fun purgeExpiredBatch(limit: Int): Int {
        val deleted = expiredMessageDeleteUseCase.deleteExpiredMessages(limit)

        var photoFailures = 0
        deleted.photoObjectKeys.forEach { key ->
            runCatching { objectStoragePort.delete(key) }.onFailure { photoFailures += 1 }
        }
        // 키에 memberId가 있어 로그에 싣지 않는다(POL-013).
        if (photoFailures > 0) log.warn("failed to delete {} expired DM photo objects", photoFailures)
        return deleted.deletedCount
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MessageRetentionService::class.java)
    }
}
