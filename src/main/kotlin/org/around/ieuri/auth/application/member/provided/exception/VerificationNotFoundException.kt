package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 검증 핸들(publicId)에 해당하는 챌린지를 찾을 수 없을 때. */
class VerificationNotFoundException(message: String) : UserFacingException("VERIFICATION_NOT_FOUND", message)
