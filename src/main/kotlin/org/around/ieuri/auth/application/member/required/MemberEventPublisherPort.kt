package org.around.ieuri.auth.application.member.required

/** 회원 애그리게이트가 내는 이벤트의 발행 포트. 커밋 뒤 후처리(메일 발송·감사 기록·다른 context 정리)를 일으킨다. */
interface MemberEventPublisherPort {
    fun publish(event: Any)
}
