package org.around.ieuri.auth.domain.member

import java.util.UUID

/** 새로 발급할 코드의 핸들과 해시. 평문 코드는 도메인에 들어오지 않는다. */
data class VerificationCode(
    val publicId: UUID,
    val codeHash: String,
)
