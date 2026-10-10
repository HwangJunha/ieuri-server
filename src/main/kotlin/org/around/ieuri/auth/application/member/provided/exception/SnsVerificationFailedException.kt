package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** SNS id_token 검증 실패. 사유를 노출하지 않도록 단일 code로 일반화한다. */
class SnsVerificationFailedException(message: String) : UserFacingException("SNS_VERIFICATION_FAILED", message)
