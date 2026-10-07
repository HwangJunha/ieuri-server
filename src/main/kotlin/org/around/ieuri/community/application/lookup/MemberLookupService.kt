package org.around.ieuri.community.application.lookup

import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.application.lookup.required.MemberLookupPort
import org.around.ieuri.community.domain.lookup.MemberSummary
import org.springframework.stereotype.Service

/** 회원 조회. 다른 context 호출이라 community 트랜잭션을 열지 않는다. */
@Service
class MemberLookupService(
    private val memberLookupPort: MemberLookupPort,
) : MemberLookupUseCase {
    override fun member(memberId: Long): MemberSummary? = memberLookupPort.findAuthor(memberId)

    override fun members(memberIds: List<Long>): Map<Long, MemberSummary> = memberLookupPort.findAuthors(memberIds)
}
