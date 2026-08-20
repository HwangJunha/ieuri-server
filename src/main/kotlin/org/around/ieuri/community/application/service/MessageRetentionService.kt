package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.MessageRetentionUseCase
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.domain.aggregate.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

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
    private val objectStoragePort: ObjectStoragePort,
    private val clockPort: ClockPort,
) : MessageRetentionUseCase {

    /**
     * **트랜잭션을 여기서 열지 않는다.** 회차의 트랜잭션 경계는 행 삭제 포트 안에만 있다
     * (`ExpiredMessageRowPurger`) — 여기서 열면 아래 R2 왕복 최대 limit건이 그 트랜잭션 안에서
     * 돌아 커넥션을 수십 초 잡는다. 경계를 나눈 의미가 사라진다.
     */
    override fun purgeExpiredBatch(limit: Int): Int {
        require(limit > 0) { "limit은 양수여야 합니다." }
        val cutoff = Message.retentionCutoff(clockPort.now())
        val purged = messagePurgePort.deleteCreatedBefore(cutoff, limit)
        if (purged.deletedCount == 0) return 0

        // **첨부 사진도 함께 지운다.** 행만 지우고 오브젝트를 남기면 "6개월 뒤 삭제"가
        // 사진에 대해서는 거짓이 된다 — 보관 기간을 정한 의미가 없어진다.
        //
        // 행 삭제 **뒤에** 지운다. 먼저 지웠다가 트랜잭션이 롤백되면 메시지는 남고 사진만
        // 사라져 깨진 첨부가 된다. 반대 순서에서는 최악이 고아 오브젝트인데, 그쪽이 낫다.
        //
        // 개별 실패를 삼키는 이유는 한 장의 삭제 실패로 회차 전체를 되돌릴 이유가 없어서다 —
        // 스토리지에 이미 없는 키도 정상 경로다(멱등).
        var photoFailures = 0
        purged.photoObjectKeys.forEach { key ->
            runCatching { objectStoragePort.delete(key) }.onFailure { photoFailures += 1 }
        }
        // 키에 memberId 구간이 있어 로그에 싣지 않는다(POL-013) — 건수만 남긴다.
        if (photoFailures > 0) log.warn("failed to delete {} expired DM photo objects", photoFailures)
        return purged.deletedCount
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MessageRetentionService::class.java)
    }
}
