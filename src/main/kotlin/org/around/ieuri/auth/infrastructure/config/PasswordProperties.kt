package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 비밀번호 해시용 pepper(BCrypt 앞단 HMAC, DB에 저장하지 않음). prod는 IEURI_PASSWORD_PEPPER 주입(기본값 없음 → 부팅 실패). */
@ConfigurationProperties(prefix = "ieuri.password")
data class PasswordProperties(
    val pepper: String,
)
