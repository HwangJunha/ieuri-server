package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 최초 ADMIN 부트스트랩 회원 id 목록(IEURI_BOOTSTRAP_ADMIN_IDS, 쉼표 구분). 부팅 시 멱등 부여하며 비면 아무것도 하지 않는다. */
@ConfigurationProperties(prefix = "ieuri.rbac")
data class RbacProperties(
    val bootstrapAdminMemberIds: List<Long> = emptyList(),
)
