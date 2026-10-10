package org.around.ieuri.auth.application.token.provided.dto

/** 로그아웃 커맨드. 제출된 refresh를 폐기한다. */
data class LogoutCommand(
    val refreshToken: String,
    /** 접속지 정보(접속기록용). 규약은 [LoginCommand.ipAddress]와 같다. */
    val ipAddress: String? = null,
) {
    init {
        require(refreshToken.isNotBlank()) { "refreshToken은 필수입니다." }
    }
}
