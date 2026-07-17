package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 관리자 임시 가드 설정. 본격 RBAC 전까지 정지/복구를 이 키로 보호한다.
 * 비밀값 — prod는 환경변수 주입(fail-fast), local은 개발 전용 값. 커밋 금지.
 */
@ConfigurationProperties(prefix = "ieuri.admin")
data class AdminProperties(
    val apiKey: String,
)
