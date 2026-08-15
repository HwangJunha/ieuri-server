package org.around.ieuri.community.infrastructure.adapter.out.client.mapper

import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.tourism.application.query.AppCategoryCode

/**
 * 카테고리 구성 → 커뮤니티 칩(SPEC-0020). 상대 도메인은 문자열 축으로 내려주므로 이름으로 옮긴다.
 *
 * **모르는 값이면 null이다.** tourism이 카테고리를 먼저 추가해도 커뮤니티가 깨지지 않고
 * 그 칩만 빠진다 — 두 도메인이 각자 배포되려면 한쪽의 확장이 다른 쪽의 실패가 되면 안 된다.
 */
fun AppCategoryCode.toChipViewOrNull(): CommunityChipView? =
    TargetCategory.fromOrNull(category)?.let {
        CommunityChipView(category = it)
    }
