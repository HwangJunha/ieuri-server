package org.around.ieuri.auth.application.token.required

/** 접속기록(로그인·로그아웃) 이벤트의 발행 포트. */
interface TokenEventPublisherPort {
    fun publish(event: Any)
}
