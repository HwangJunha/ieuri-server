package org.around.ieuri.community.application.service

import org.around.ieuri.community.application.port.`in`.command.MemberDataEraseUseCase
import org.around.ieuri.community.application.port.out.persistence.MemberDataErasePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 커뮤니티 데이터 삭제(SPEC-0011 R17).
 *
 * **순서가 중요하다.** 글을 먼저 지우면 그 글에 달린 남의 댓글·좋아요가 cascade로 함께
 * 사라지고, 그 다음 남는 것이 이 회원이 *남의 글에* 남긴 흔적이다. 반대로 하면 자기 글의
 * 댓글을 두 번 지우려 든다(무해하지만 의미 없는 쿼리다).
 *
 * 이 도메인의 저장소만 만진다 — 다른 도메인의 삭제는 각자의 핸들러가 한다.
 */
@Service
class MemberDataEraseService(
    private val erasePort: MemberDataErasePort,
) : MemberDataEraseUseCase {

    @Transactional
    override fun eraseMemberData(memberId: Long) {
        val posts = erasePort.deletePostsByAuthor(memberId)
        val comments = erasePort.deleteCommentsByAuthor(memberId)
        val likes = erasePort.deleteLikesByMember(memberId)
        val hides = erasePort.deleteHidesByMember(memberId)
        val blocks = erasePort.deleteBlocksInvolving(memberId)

        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        log.info(
            "community data erased for withdrawn member {}: posts={} comments={} likes={} hides={} blocks={}",
            memberId, posts, comments, likes, hides, blocks,
        )
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberDataEraseService::class.java)
    }
}
