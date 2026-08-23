package org.around.ieuri.community.infrastructure.adapter.out.client.mapper

import org.around.ieuri.auth.application.query.MemberPublicProfileView
import org.around.ieuri.community.application.query.PostAuthorView

/**
 * auth 응답 → 커뮤니티 read DTO 변환(POL-033). 어댑터 안에 숨기지 않고 여기 두어
 * 단위 테스트로 필드 대조가 가능하게 한다.
 */
fun MemberPublicProfileView.toPostAuthorView(): PostAuthorView = PostAuthorView(
    id = memberId,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
)
