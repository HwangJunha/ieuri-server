package org.around.ieuri.auth.application.member.required

/** 이메일 발송 포트. 발송 실패는 예외로 알리지 않는다(이미 커밋된 트랜잭션의 뒷정리 경로다). */
interface EmailSenderPort {
    fun sendVerificationCode(toEmail: String, code: String)

    fun sendPasswordResetCode(toEmail: String, code: String)
}
