package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * RBAC 설정. 최초 ADMIN 부트스트랩용 회원 id 목록을 주입한다(부팅 시 idempotent 부여).
 * 운영은 환경변수(IEURI_BOOTSTRAP_ADMIN_IDS, 쉼표 구분)로 주입한다. 비어 있으면 아무 것도 하지 않는다.
 */
@ConfigurationProperties(prefix = "ieuri.rbac")
data class RbacProperties(
    val bootstrapAdminMemberIds: List<Long> = emptyList(),
)
