package org.around.ieuri.tourism.adapter.client

import org.around.ieuri.auth.application.member.provided.MemberQueryUseCase
import org.around.ieuri.tourism.application.footprint.required.MapOwnerLookupPort
import org.springframework.stereotype.Component

/**
 * 공유 지도 주인의 닉네임 조회(SPEC-0030 R13). `auth`의 인바운드 포트를 통해서만 접근한다.
 * - 이메일을 복호화하지 않는 `memberPublicProfile`을 쓰며, 닉네임만 넘긴다(auth의 DTO는 경계를 넘지 않는다).
 */
@Component
class MapOwnerLookupAdapter(
    private val memberQueryUseCase: MemberQueryUseCase,
) : MapOwnerLookupPort {

    override fun findNickname(memberId: Long): String? = memberQueryUseCase.memberPublicProfile(memberId)?.nickname

    /** 벌크 진입점이라 목록 한 페이지가 왕복 1회다. */
    override fun findNicknames(memberIds: Collection<Long>): Map<Long, String> {
        if (memberIds.isEmpty()) return emptyMap()

        return memberQueryUseCase.memberPublicProfiles(memberIds.distinct())
            .mapValues { (_, profile) -> profile.nickname }
    }
}
