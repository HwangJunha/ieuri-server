package org.around.ieuri.community.infrastructure.adapter.out.client.mapper

import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.tourism.application.query.SpotSummaryView

/**
 * tourism 응답 → 커뮤니티 read DTO 변환(POL-033). 어댑터 안에 숨기지 않고 여기 두는 이유는
 * 그래야 단위 테스트로 필드 대조가 가능하기 때문이다.
 *
 * 카테고리는 **문자열 코드로 건너온다** — tourism의 도메인 enum을 그대로 받으면 커뮤니티가
 * 상대 도메인의 domain 패키지에 의존하게 되고, 그것이 도메인 추출을 막는다.
 * 모르는 값이면 null이며 그 대상은 카테고리 칩으로 걸러지지 않는다.
 */
fun SpotSummaryView.toPostSpotView(): PostSpotView = PostSpotView(
    id = id,
    title = title,
    thumbnailImageUrl = thumbnailImageUrl,
    category = TargetCategory.fromOrNull(categoryCode),
    longitude = longitude,
    latitude = latitude,
)
