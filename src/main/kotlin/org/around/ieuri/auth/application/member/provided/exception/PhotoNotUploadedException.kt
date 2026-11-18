package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.AuthUserException

/** 발급받았으나 실제 업로드가 확인되지 않은 키일 때(R6). */
class PhotoNotUploadedException(message: String = "업로드가 확인되지 않았습니다.") : AuthUserException("PHOTO_NOT_UPLOADED", message)
