package org.around.ieuri.community.application.post.provided.dto

import org.around.ieuri.community.domain.post.TargetCategory

/** 커뮤니티 상단 칩 하나. 표시 문구는 담지 않는다 — 앱이 [category]를 언어별로 매핑한다. */
data class CommunityChipView(
    val category: TargetCategory,
)
