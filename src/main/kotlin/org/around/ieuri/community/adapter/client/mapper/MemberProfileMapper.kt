package org.around.ieuri.community.adapter.client.mapper

import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.community.application.shared.required.dto.MemberSummaryView

/** auth 응답 → 커뮤니티 read DTO 변환(POL-033). */
fun MemberPublicProfileView.toMemberSummaryView(): MemberSummaryView = MemberSummaryView(
    id = memberId,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
)
