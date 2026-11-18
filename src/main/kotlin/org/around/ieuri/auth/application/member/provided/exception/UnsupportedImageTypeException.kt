package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.AuthUserException

/** 허용하지 않는 이미지 형식으로 업로드 URL을 요청할 때(R6). */
class UnsupportedImageTypeException(message: String = "지원하지 않는 이미지 형식입니다.") : AuthUserException("UNSUPPORTED_IMAGE_TYPE", message)
