package org.around.ieuri.auth.application.service

import org.around.ieuri.auth.application.port.`in`.query.RoleQueryUseCase
import org.around.ieuri.auth.application.port.out.query.RoleQueryPort
import org.around.ieuri.auth.application.query.RoleInfoView
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 역할 조회 서비스. 읽기 포트의 read DTO를 그대로 반환한다. */
@Service
@Transactional(readOnly = true)
class RoleQueryService(
    private val roleQueryPort: RoleQueryPort,
) : RoleQueryUseCase {

    override fun assignableRoles(): List<RoleInfoView> = roleQueryPort.findAssignableRoles()
}
