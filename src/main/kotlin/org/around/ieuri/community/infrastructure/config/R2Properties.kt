package org.around.ieuri.community.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Cloudflare R2 접근 설정. **자격증명은 환경변수로만 주입한다** — 코드에 하드코딩하지 않는다.
 *
 * 조회용 공개 도메인(cdn.ieuri.co.kr)은 여기 두지 않는다 — 서버가 쓰지 않기 때문이다.
 * DB에는 object key만 저장하고 클라이언트가 공개 베이스와 조합해 쓴다.
 */
@ConfigurationProperties(prefix = "ieuri.r2")
data class R2Properties(
    /** S3 호환 엔드포인트. 쓰기는 인증된 이 엔드포인트로만 간다. */
    val s3Endpoint: String = "",
    val accessKey: String = "",
    val secretAccessKey: String = "",
    val bucket: String = "",
    /**
     * presigned URL 유효 기간. 여러 장을 연달아 올리는 동안 만료되지 않아야 하고,
     * 모바일 네트워크에서 대용량 사진은 느리다.
     */
    val uploadUrlTtl: Duration = Duration.ofMinutes(30),
    val maxUploadBytes: Long = 10L * 1024 * 1024,
    val allowedContentTypes: List<String> = listOf("image/jpeg", "image/png", "image/webp"),
)

/** 커뮤니티 조회·피드 설정. */
@ConfigurationProperties(prefix = "ieuri.community")
data class CommunityProperties(
    /** 커넥션 first 상한. 초과 요청은 오류가 아니라 절삭된다. */
    val maxPageSize: Int = 50,
    /** 피드 카드에 얹는 댓글 미리보기 개수. 전체 목록은 comments(postId:)가 따로 낸다. */
    val commentPreviewSize: Int = 3,
    val maxRadiusMeters: Int = 100_000,
    /** 지역 피드가 한 번에 다룰 spot 수 상한. 넘으면 잘린다. */
)

/**
 * DM 메시지 보관 배치 설정(SPEC-0017 R16).
 *
 * **회차 상한을 설정으로 뺀다**(POL-044) — 실측 전까지 확정값이 아니므로 근거 없는 계수를
 * 코드에 박지 않는다. 한 회차가 끝나면 다음 회차가 이어지며, 상한보다 적게 지운 회차가
 * 나오면 지울 것이 없다는 뜻이라 멈춘다.
 */
@ConfigurationProperties(prefix = "ieuri.community.message-retention")
data class MessageRetentionProperties(
    /** 한 회차에 지울 최대 건수. 트랜잭션이 회차 단위로 끊겨 락과 WAL이 커지지 않는다. */
    val batchSize: Int = 1_000,
    /** 한 번의 잡 실행에서 돌 최대 회차 수. 무한 루프를 막는 상한이다. */
    val maxBatchesPerRun: Int = 100,
)
