package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.application.port.`in`.command.MemberReactionEraseUseCase
import org.around.ieuri.tourism.application.port.out.persistence.SpotReactionPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 좋아요/싫어요 삭제(SPEC-0011 R17).
 * 이 도메인의 저장소만 만진다 — 다른 도메인의 삭제는 각자의 핸들러가 한다.
 */
@Service
class MemberReactionEraseService(
    private val spotReactionPort: SpotReactionPort,
) : MemberReactionEraseUseCase {

    /**
     * **REQUIRES_NEW여야 한다.** 이 유스케이스는 `AFTER_COMMIT` 리스너가 부르는데, 그 시점의
     * 원본 트랜잭션은 이미 커밋됐으면서도 스레드에 **묶여 있다.** 기본 전파(REQUIRED)로 두면
     * 커밋할 수 없는 그 트랜잭션에 조용히 합류해 삭제가 흘러가지 않는다 —
     * 예외도 나지 않아 성공한 것처럼 보인다(실제로 탈퇴 후 글·댓글이 남았다).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberReactions(memberId: Long) {
        val deleted = spotReactionPort.deleteByMember(memberId)
        // 개인정보를 담지 않는다(POL-013) — 식별자와 건수만 남긴다.
        log.info("tourism reactions erased for withdrawn member {}: reactions={}", memberId, deleted)
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberReactionEraseService::class.java)
    }
}
