package org.around.ieuri.auth.application.member
import org.around.ieuri.auth.application.member.provided.RoleQueryUseCase
import org.around.ieuri.auth.application.member.provided.dto.RoleInfoView
import org.around.ieuri.auth.application.member.required.RoleQueryPort
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 역할 조회 서비스. 읽기 포트의 read DTO를 그대로 반환한다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class RoleQueryService(
    private val roleQueryPort: RoleQueryPort,
) : RoleQueryUseCase {

    override fun assignableRoles(): List<RoleInfoView> = roleQueryPort.findAssignableRoles()
}
