package org.around.ieuri.auth.application.query

import java.time.Instant

/** 약관 1건의 동의 상태(SPEC-0011 R9). 새 버전이 시행되면 이전 버전 동의는 이월되지 않아 agreed가 거짓이 된다. */
data class AgreementStatusView(
    val termsCode: String,
    val required: Boolean,
    val agreed: Boolean,
    /** 시행 중인 버전에 동의한 시각. 미동의면 null. */
    val agreedAt: Instant?,
    val version: String,
)

/** 프로필 이미지 업로드 URL 발급 결과(R6). */
data class ProfileImageUploadUrlView(
    val uploadUrl: String,
    val objectKey: String,
    val expiresAt: Instant,
)
