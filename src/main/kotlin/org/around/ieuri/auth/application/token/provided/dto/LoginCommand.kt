package org.around.ieuri.auth.application.token.provided.dto
import org.around.ieuri.auth.domain.member.Provider

/** SNS 로그인 커맨드. id_token으로 기존 회원을 인증한다(미가입은 거부). */
data class LoginCommand(
    val provider: Provider,
    val idToken: String,
    /** 탈퇴 시 연결 해제용 토큰을 보충하기 위한 provider authorization code. 없으면 보충하지 않는다. */
    val authorizationCode: String? = null,
) {
    init {
        require(idToken.isNotBlank()) { "idToken은 필수입니다." }
    }
}
