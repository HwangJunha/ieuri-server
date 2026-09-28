package org.around.ieuri.tourism.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.`in`.query.MemberQueryUseCase
import org.around.ieuri.tourism.application.port.out.client.MapOwnerLookupPort
import org.springframework.stereotype.Component

/**
 * 공유 지도 주인의 닉네임 조회(SPEC-0030 R13). `auth`의 **인바운드 포트**를 통해서만 접근한다.
 *
 * tourism의 application·domain은 [MapOwnerLookupPort]만 알고 auth라는 이름조차 모른다.
 *
 * `memberPublicProfile`을 쓰는 이유는 그것이 **이메일을 복호화하지 않는** 표시용 진입점이기
 * 때문이다 — `member`를 쓰면 공유 응답에 필요 없는 이메일·권한·상태까지 딸려 온다(R13).
 *
 * 닉네임만 꺼내 넘긴다. auth의 read DTO가 이 경계를 넘지 않는다.
 */
@Component
class MapOwnerLookupAdapter(
    private val memberQueryUseCase: MemberQueryUseCase,
) : MapOwnerLookupPort {

    override fun findNickname(memberId: Long): String? = memberQueryUseCase.memberPublicProfile(memberId)?.nickname

    /** auth가 벌크 진입점을 이미 제공한다 — 목록 한 페이지가 왕복 1회로 끝난다. */
    override fun findNicknames(memberIds: Collection<Long>): Map<Long, String> {
        if (memberIds.isEmpty()) return emptyMap()

        return memberQueryUseCase.memberPublicProfiles(memberIds.distinct())
            .mapValues { (_, profile) -> profile.nickname }
    }
}
