package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 발급받았으나 실제 업로드가 확인되지 않은 키일 때(R6). */
class PhotoNotUploadedException(message: String) : UserFacingException("PHOTO_NOT_UPLOADED", message)
