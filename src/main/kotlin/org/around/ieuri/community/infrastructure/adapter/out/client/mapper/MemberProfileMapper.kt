package org.around.ieuri.community.infrastructure.adapter.out.client.mapper

import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.community.application.query.PostAuthorView

/** auth 응답 → 커뮤니티 read DTO 변환(POL-033). */
fun MemberPublicProfileView.toPostAuthorView(): PostAuthorView = PostAuthorView(
    id = memberId,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
)
