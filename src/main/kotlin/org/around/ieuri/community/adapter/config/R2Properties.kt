package org.around.ieuri.community.adapter.config

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
