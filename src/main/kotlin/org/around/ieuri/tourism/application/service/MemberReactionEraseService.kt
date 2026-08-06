package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 좋아요/싫어요 삭제(SPEC-0011 R17).
 * 이 도메인의 저장소만 만진다 — 다른 도메인의 삭제는 각자의 핸들러가 한다.
 */
@Service
class MemberReactionEraseService(
    private val spotReactionPort: SpotReactionPort,
) : MemberReactionEraseUseCase {

    @Transactional
    override fun eraseMemberReactions(memberId: Long) {
        val deleted = spotReactionPort.deleteByMember(memberId)
        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        log.info("tourism reactions erased for withdrawn member {}: reactions={}", memberId, deleted)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberReactionEraseService::class.java)
    }
}
