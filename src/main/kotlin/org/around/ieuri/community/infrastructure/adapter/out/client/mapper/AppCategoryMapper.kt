package org.around.ieuri.community.infrastructure.adapter.out.client.mapper

import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.domain.vo.TargetCategory
import org.around.ieuri.tourism.application.query.AppCategoryCode

/** 카테고리 구성 → 커뮤니티 칩(SPEC-0020). 모르는 카테고리면 null이라 그 칩만 빠진다. */
fun AppCategoryCode.toChipViewOrNull(): CommunityChipView? = TargetCategory.fromOrNull(category)?.let {
    CommunityChipView(category = it)
}
