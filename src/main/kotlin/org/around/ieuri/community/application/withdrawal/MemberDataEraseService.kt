package org.around.ieuri.community.application.withdrawal

import org.around.ieuri.community.application.shared.required.ClockPort
import org.around.ieuri.community.application.withdrawal.provided.MemberDataEraseUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.ClassUtils

/**
 * 탈퇴 회원의 커뮤니티 데이터 정리. 트랜잭션을 열지 않고 애그리게이트별 단계를 차례로 부른다.
 * - 한 단계가 실패해도 나머지는 진행한다. 실패한 단계는 재시도하지 않고 로그만 남긴다.
 */
@Service
class MemberDataEraseService(
    private val erasers: List<MemberContentEraser>,
    private val clockPort: ClockPort,
) : MemberDataEraseUseCase {

    override fun eraseMemberData(memberId: Long) {
        val deletedAt = clockPort.now()
        erasers.forEach { eraser ->
            // 트랜잭션 프록시가 아니라 실제 서비스 이름을 남긴다.
            val step = ClassUtils.getUserClass(eraser).simpleName
            runCatching { eraser.eraseMemberContent(memberId, deletedAt) }
                // 식별자와 건수만 남긴다(POL-013).
                .onSuccess { log.info("community data cleared for withdrawn member {}: {} rows={}", memberId, step, it) }
                .onFailure { log.error("failed to clear community data for withdrawn member {}: {}", memberId, step, it) }
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MemberDataEraseService::class.java)
    }
}
