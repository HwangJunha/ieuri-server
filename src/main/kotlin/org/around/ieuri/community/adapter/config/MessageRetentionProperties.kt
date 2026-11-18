package org.around.ieuri.community.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * DM 메시지 보관 배치 설정(SPEC-0017 R16, POL-044).
 * - 회차를 이어 돌다가 상한보다 적게 지운 회차가 나오면 멈춘다.
 */
@ConfigurationProperties(prefix = "ieuri.community.message-retention")
data class MessageRetentionProperties(
    /** 한 회차(트랜잭션 하나)에 지울 최대 건수. */
    val batchSize: Int = 1_000,
    /** 한 번의 잡 실행에서 돌 최대 회차 수. */
    val maxBatchesPerRun: Int = 100,
)
