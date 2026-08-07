package org.around.ieuri.community.infrastructure.adapter.`in`.event

import org.around.ieuri.auth.domain.event.MemberWithdrawn
import org.around.ieuri.community.application.port.`in`.command.MemberDataEraseUseCase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 탈퇴 이벤트를 받아 커뮤니티 데이터를 지운다(SPEC-0011 R17). **인바운드 어댑터다** —
 * auth가 이 도메인을 호출하는 것이 아니라 이 도메인이 이벤트를 구독한다.
 *
 * `AFTER_COMMIT`이라 회원 삭제가 확정된 뒤에만 실행된다. 그래서 회원은 지워졌는데 글이 아직
 * 남아 있는 창이 생기는데, 스펙이 이 창을 허용한다 — 커뮤니티 조회는 작성자 부재를 이미
 * 견딘다(작성자를 찾지 못하면 자리표시자 닉네임을 쓴다).
 *
 * **예외를 삼킨다.** 커밋 뒤라 회원 삭제를 되돌릴 수 없고, 여기서 던지면 같은 이벤트를 받는
 * 다른 도메인의 리스너까지 막힌다(R17의 마지막 두 인수 기준). 실패는 기록만 남기고, 보상은
 * 재시도와 실패 기록으로 처리한다.
 *
 * **클래스 이름에 도메인을 붙인다.** Spring은 단순 클래스명으로 빈 이름을 짓기 때문에,
 * tourism의 같은 리스너와 이름이 겹치면 패키지가 달라도 기동이 실패한다
 * (ConflictingBeanDefinitionException).
 *
 * 도메인 이벤트 타입만 auth에서 가져온다 — 발행/구독의 계약이라 내부 의존이 아니다.
 * Kafka로 옮기면 이 클래스가 컨슈머로 바뀌고 애플리케이션 레이어는 그대로다(POL-028).
 */
@Component
class CommunityMemberWithdrawnListener(
    private val memberDataEraseUseCase: MemberDataEraseUseCase,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun on(event: MemberWithdrawn) {
        try {
            memberDataEraseUseCase.eraseMemberData(event.memberId)
        } catch (e: Exception) {
            log.error("failed to erase community data for withdrawn member {}", event.memberId, e)
        }
    }

    private companion object {
        private val log = LoggerFactory.getLogger(CommunityMemberWithdrawnListener::class.java)
    }
}
