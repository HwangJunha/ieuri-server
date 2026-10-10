package org.around.ieuri.auth.domain.member

/** 약관 한 버전. 시드로만 들어오며 서버가 쓰지 않는다. */
data class Terms(
    val id: Long,
    val code: String,
    val version: String,
    val required: Boolean,
)
