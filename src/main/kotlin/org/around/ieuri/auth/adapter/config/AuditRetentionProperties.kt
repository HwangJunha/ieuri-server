package org.around.ieuri.auth.adapter.config
import org.springframework.boot.context.properties.ConfigurationProperties

/** 감사 기록 보관 정리 배치 설정(SPEC-0032 R22, POL-044). 보관 기간은 도메인([org.around.ieuri.auth.domain.specification.AuditRetention])이 소유한다. */
@ConfigurationProperties(prefix = "ieuri.auth.audit-retention")
data class AuditRetentionProperties(
    /** 한 회차에 지울 최대 건수. 트랜잭션이 회차 단위로 끊겨 락과 WAL이 커지지 않는다. */
    val batchSize: Int = 1_000,
    /** 한 번의 잡 실행에서 돌 최대 회차 수. 무한 루프를 막는 상한이다. */
    val maxBatchesPerRun: Int = 100,
)
