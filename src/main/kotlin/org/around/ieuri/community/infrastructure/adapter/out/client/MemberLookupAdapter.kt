package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toPostAuthorView
import org.springframework.stereotype.Component

/**
 * 회원 조회. `auth`의 **인바운드 포트**를 통해서만 접근한다 — SpotLookupAdapter와 같은 이유다.
 *
 * `memberPublicProfile`을 쓰는 이유는 `member`가 관리자용 상세라 이메일을 복호화하기 때문이다.
 * 커뮤니티 표면에는 이메일·권한·상태가 필요 없다.
 */
@Component
class MemberLookupAdapter(
    private val memberQueryUseCase: MemberQueryUseCase,
) : MemberLookupPort {

    override fun findAuthor(memberId: Long): PostAuthorView? = memberQueryUseCase.memberPublicProfile(memberId)?.toPostAuthorView()

    /** 한 번의 조회로 여러 작성자를 가져온다 — 카드마다 묻지 않는다. */
    override fun findAuthors(memberIds: List<Long>): Map<Long, PostAuthorView> = memberQueryUseCase.memberPublicProfiles(memberIds.distinct())
        .mapValues { (_, profile) -> profile.toPostAuthorView() }
}
