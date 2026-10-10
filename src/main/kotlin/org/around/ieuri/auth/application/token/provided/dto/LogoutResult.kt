package org.around.ieuri.auth.application.token.provided.dto
import org.around.ieuri.auth.application.token.provided.dto.LoginCommand
import org.around.ieuri.auth.application.token.provided.dto.LoginEmailCommand
import org.around.ieuri.auth.application.token.provided.dto.LogoutCommand
import org.around.ieuri.auth.application.token.provided.dto.RefreshTokenCommand
data class LogoutResult(
    val success: Boolean,
)
