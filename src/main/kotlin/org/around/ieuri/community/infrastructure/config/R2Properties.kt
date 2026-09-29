package org.around.ieuri.community.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Cloudflare R2 접근 설정. 자격증명은 환경변수로만 주입한다.
 * - DB에는 object key만 저장하고, 공개 도메인과의 조합은 클라이언트가 한다.
 */
@ConfigurationProperties(prefix = "ieuri.r2")
data class R2Properties(
    val s3Endpoint: String = "",
    val accessKey: String = "",
    val secretAccessKey: String = "",
    val bucket: String = "",
    val uploadUrlTtl: Duration = Duration.ofMinutes(30),
    val maxUploadBytes: Long = 10L * 1024 * 1024,
    val allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
)

/** 커뮤니티 조회·피드 설정. */
@ConfigurationProperties(prefix = "ieuri.community")
data class CommunityProperties(
    /** 커넥션 first 상한. 초과 요청은 오류가 아니라 절삭된다. */
    val maxPageSize: Int = 50,
    /** 피드 카드에 얹는 댓글 미리보기 개수. */
    val commentPreviewSize: Int = 3,
    val maxRadiusMeters: Int = 100_000,
)

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
