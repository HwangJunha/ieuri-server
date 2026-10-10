package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 선언한 크기가 상한을 넘을 때(R6). */
class ImageTooLargeException(message: String) : UserFacingException("IMAGE_TOO_LARGE", message)
