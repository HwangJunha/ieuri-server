package org.around.ieuri.community.adapter.client.mapper

import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.community.domain.lookup.MemberSummary

/** auth 응답 → 커뮤니티 read DTO 변환(POL-033). */
fun MemberPublicProfileView.toMemberSummary(): MemberSummary = MemberSummary(
    id = memberId,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
)
