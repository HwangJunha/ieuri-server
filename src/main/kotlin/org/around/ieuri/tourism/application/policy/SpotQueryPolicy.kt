package org.around.ieuri.tourism.application.policy

import java.time.Duration

/**
 * spots 조회 정책값(설정에서 주입). 반경 UX/성능 상한과 페이지 크기 캡을 코드 수정 없이 조정한다.
 *
 * @param maxRadiusMeters 서버 UX/성능 반경 상한(실측 확정 100,000m). 초과 시 RADIUS_TOO_LARGE.
 * @param maxPageSize     `spots` 오프셋 페이지의 size 상한(POL-018 잠정 100). 초과 요청은 클램프.
 * @param maxLikedPageSize `likedSpots` 커서 페이지의 first 상한(SPEC-0008 [TBD-2], `members` 선례로 100).
 *   [maxPageSize]와 우연히 값이 같지만 **다른 계약이다** — 하나는 위치 기반 오프셋 목록, 다른 하나는
 *   회원의 좋아요 커서 목록이다. 한 노브로 묶으면 `spots` 상한을 조정할 때 좋아요 목록이 조용히
 *   함께 바뀐다.
 * @param detailAbsenceTtl 상세 본문 **부재** 행의 신선도 기준(SPEC-0008 [TBD-1] = 7일).
 *   이 기간이 지나면 부재 spot을 upstream에 다시 물어본다(AC-13) — 나중에 데이터가 생길 수 있기
 *   때문이다. 정상 행에는 만료가 없다(1차 신선도는 Valkey TTL 24h, 배치가 목록을 매일 최신화).
 *   부재를 정상보다 짧게 잡는 것이 이 값의 요지이며, 실측 후 조정 대상이라 설정값으로 둔다.
 */
data class SpotQueryPolicy(
    val maxRadiusMeters: Int,
    val maxPageSize: Int,
    val maxLikedPageSize: Int,
    val detailAbsenceTtl: Duration,
)
