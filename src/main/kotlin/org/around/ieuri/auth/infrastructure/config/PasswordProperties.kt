package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 비밀번호 해시용 서버 비밀값(pepper). BCrypt 앞단 HMAC에 쓰이며 DB에는 저장되지 않아 DB 유출 시 추가 방어가 된다.
 * prod는 환경변수(IEURI_PASSWORD_PEPPER)로 주입(기본값 없이 → 없으면 부팅 실패, fail-fast),
 * 로컬은 application-local.yml의 개발 전용 값을 사용한다.
 */
@ConfigurationProperties(prefix = "ieuri.password")
data class PasswordProperties(
    val pepper: String,
)
