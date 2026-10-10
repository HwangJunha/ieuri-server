package org.around.ieuri.auth.application.member.provided.dto
import java.util.UUID

/** 가입 결과. 미검증이면 verificationPublicId(검증 핸들)가 채워진다. member_id는 노출하지 않는다. */
data class RegisterMemberResult(
    val emailVerified: Boolean,
    val verificationPublicId: UUID?,
)
