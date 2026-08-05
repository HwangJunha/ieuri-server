package org.around.ieuri.community.application.policy

import java.time.Duration

/**
 * 커뮤니티 유스케이스의 설정값. 인프라 @ConfigurationProperties에서 뽑아 온 프레임워크
 * 무의존 데이터로, 서비스가 이것만 주입받는다.
 *
 * @param commentPreviewSize 피드 카드에 얹는 댓글 미리보기 개수. 전체 목록은 별도 쿼리다.
 * @param maxPageSize      커넥션 first 상한. 초과 요청은 오류가 아니라 상한으로 절삭된다.
 * @param maxRadiusMeters  지역 피드 반경 상한. 초과 요청은 상한으로 절삭된다.
 * @param maxNearbySpots   지역 피드가 한 번에 다룰 spot 수 상한. 넘으면 조용히 잘린다 —
 *                         넓은 반경에서 수만 건이 나오면 `IN` 절의 파라미터 수와 실행 계획이
 *                         함께 무너지기 때문이다.
 * @param uploadUrlTtl     presigned URL 유효 기간.
 * @param maxUploadBytes   업로드 허용 최대 바이트.
 * @param allowedContentTypes 허용 MIME 타입. 이 외에는 URL을 발급하지 않는다.
 */
data class CommunityPolicy(
    val maxPageSize: Int,
    val commentPreviewSize: Int,
    val maxRadiusMeters: Int,
    val maxNearbySpots: Int,
    val uploadUrlTtl: Duration,
    val maxUploadBytes: Long,
    val allowedContentTypes: Set<String>,
)
