package org.around.ieuri.auth.application.member.provided

/** 인증 코드 메일 발송. 코드를 만든 트랜잭션이 커밋된 뒤에 부른다 — 롤백되면 메일이 나가지 않는다. */
interface VerificationMailUseCase {
    fun sendVerificationCode(toEmail: String, code: String)

    fun sendPasswordResetCode(toEmail: String, code: String)
}
