package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 위치 조회 감사 설정(SPEC-0032 R21, POL-044·POL-057).
 *
 * **기본값이 꺼짐인 것이 요구다.** 위치정보사업 신고가 끝나기 전에는 기록을 만들지 않는다 —
 * 코드는 배포되지만 동작하지 않는 상태로 둔다. 켜는 것은 설정 키 하나다.
 */
@ConfigurationProperties(prefix = "ieuri.auth.location-audit")
data class LocationAuditProperties(
    /** 켜면 좌표를 받는 연산이 회원·연산명·시각을 남긴다. 신고 완료 전까지 꺼 둔다. */
    val enabled: Boolean = false,
)

/**
 * 감사 기록 보관 정리 배치 설정(SPEC-0032 R22, POL-044).
 *
 * **보관 기간 자체는 여기 없다** — 정책값이라 도메인
 * ([org.around.ieuri.auth.domain.vo.AuditRetention])이 소유한다. 설정으로 빼는 것은 실측 전까지
 * 확정값이 아닌 회차 계수뿐이다(`MessageRetentionProperties`의 선례).
 */
@ConfigurationProperties(prefix = "ieuri.auth.audit-retention")
data class AuditRetentionProperties(
    /** 한 회차에 지울 최대 건수. 트랜잭션이 회차 단위로 끊겨 락과 WAL이 커지지 않는다. */
    val batchSize: Int = 1_000,
    /** 한 번의 잡 실행에서 돌 최대 회차 수. 무한 루프를 막는 상한이다. */
    val maxBatchesPerRun: Int = 100,
)
