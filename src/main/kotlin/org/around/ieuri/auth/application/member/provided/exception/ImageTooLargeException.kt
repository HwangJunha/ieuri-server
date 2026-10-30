package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.AuthUserException

/** 선언한 크기가 상한을 넘을 때(R6). */
class ImageTooLargeException(message: String = "이미지 크기가 허용 범위를 벗어났습니다.") : AuthUserException("IMAGE_TOO_LARGE", message)
